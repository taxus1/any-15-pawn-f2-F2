package com.somepro.infrastructure.persistence.ticket;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.ticket.model.PawnTicket;
import com.somepro.domain.ticket.model.PawnTicketQuery;
import com.somepro.domain.ticket.repository.PawnTicketRepository;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.ticket.converter.PawnTicketPoConverter;
import com.somepro.infrastructure.persistence.ticket.po.PawnTicketPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 当票仓储适配器（基础设施层）：MyBatis-Plus 阻塞 JDBC 经 blocking(...) 桥接进响应式链路。
 *
 * 关键业务语义：
 *
 * 1) 票号 DP-yyyy-NNNN（含并发、含撤销/删除不复用）。
 *    ticket_no 有全局唯一索引，但「当年序号取最大 +1」必须串行，否则并发开票会算出同一个号。
 *    开票先抢 MySQL 命名锁 GET_LOCK('ticket:write')（全实例互斥），锁内取号 + 写入。
 *    取号刻意包含已撤销、已逻辑删除（del_flag=1）的行：票号一经分配永久占用，废票的号也不发给新票。
 *    锁用一条【独立于事务的原始连接】在事务开启前 GET_LOCK、在事务【提交之后】才 RELEASE_LOCK，
 *    避免「锁已放、事务未提交」导致后到者算重号。
 *
 * 2) 一件当物只能挂一张没结清（ACTIVE）的票。判定就放在同一把写锁、同一事务里
 *    （existsActiveByCollateral）：两个人前后脚拿同一件当物来开票，锁内串行后第二张必被业务挡回，
 *    不靠应用层「先查后插」的竞态，也不把唯一索引的底层错甩给柜台。
 */
@Repository
public class PawnTicketRepositoryImpl implements PawnTicketRepository {

    /** 业务日期统一按行里所在时区算，避免容器 UTC 下票号跨年。 */
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    /** 当票开具临界区命名锁（MySQL 全实例同名互斥）。 */
    private static final String WRITE_LOCK = "ticket:write";
    private static final int LOCK_WAIT_SECONDS = 10;
    private static final int MAX_RETRY = 5;

    private final PawnTicketMapper ticketMapper;
    private final TransactionTemplate transactionTemplate;
    private final DataSource dataSource;

    public PawnTicketRepositoryImpl(PawnTicketMapper ticketMapper,
                                    PlatformTransactionManager transactionManager,
                                    DataSource dataSource) {
        this.ticketMapper = ticketMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
    }

    @Override
    public Mono<PawnTicket> insert(PawnTicket ticket) {
        return blocking(() -> {
            // 每轮重试用独立连接重新抢锁；兜住票号撞号 / 锁等待超时等瞬态冲突
            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                try {
                    return inWriteLock(() -> transactionTemplate.execute(status -> {
                        // 锁内权威判定：该当物已有没结清的票，直接业务挡回（不重试、不抛底层唯一错）
                        if (ticketMapper.existsActiveByCollateral(ticket.getCollateralId()) > 0) {
                            throw new BizException("该当物已挂在一张在当（ACTIVE）的当票上，一件东西不能开两张票；"
                                    + "原票结清（赎当/绝当/撤销）后才能再开");
                        }
                        PawnTicketPO po = PawnTicketPoConverter.toPo(ticket);
                        po.setId(IdUtil.getSnowflakeNextId());
                        po.setTicketNo(nextTicketNo());
                        ticketMapper.insert(po);
                        return PawnTicketPoConverter.toDomain(po);
                    }));
                } catch (BizException e) {
                    // 业务判定（一票一占、取锁失败提示）直接透传，绝不当作瞬态冲突去重试
                    throw e;
                } catch (DuplicateKeyException | TransientDataAccessException e) {
                    // uk_ticket_no 是最后防线，锁内正常不会撞；撞了整段重新取号重试
                    if (attempt == MAX_RETRY - 1) {
                        throw new BizException("系统繁忙，请稍后重试");
                    }
                    try {
                        Thread.sleep(10L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BizException("系统繁忙，请稍后重试");
                    }
                }
            }
            throw new BizException("系统繁忙，请稍后重试");
        });
    }

    @Override
    public Mono<PawnTicket> update(PawnTicket ticket) {
        return blocking(() -> {
            PawnTicketPO existing = ticketMapper.selectById(ticket.getId());
            if (existing == null) {
                throw new BizException("当票不存在");
            }
            // 票号/当户/当物/类别/估值快照/利率费率快照永不改：把原值带回，避免被覆盖
            ticket.setTicketNo(existing.getTicketNo());
            ticket.setPawnerId(existing.getPawnerId());
            ticket.setCollateralId(existing.getCollateralId());
            ticket.setCategory(Category.valueOf(existing.getCategory()));
            ticket.setAppraisedValue(existing.getAppraisedValue());
            ticket.setMonthlyRate(existing.getMonthlyRate());
            ticket.setServiceRate(existing.getServiceRate());
            PawnTicketPO po = PawnTicketPoConverter.toPo(ticket);
            int rows = ticketMapper.updateById(po);
            if (rows == 0) {
                throw new BizException("当票不存在");
            }
            PawnTicketPO refreshed = ticketMapper.selectById(ticket.getId());
            return PawnTicketPoConverter.toDomain(Objects.requireNonNullElse(refreshed, po));
        });
    }

    @Override
    public Mono<PawnTicket> findById(Long id) {
        return blocking(() -> {
            PawnTicketPO po = ticketMapper.selectById(id);
            return po == null ? null : PawnTicketPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PawnTicket> findByTicketNo(String ticketNo) {
        return blocking(() -> {
            PawnTicketPO po = ticketMapper.selectOne(
                    Wrappers.<PawnTicketPO>lambdaQuery().eq(PawnTicketPO::getTicketNo, ticketNo));
            return po == null ? null : PawnTicketPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<PawnTicket>> page(int pageNum, int pageSize, PawnTicketQuery query) {
        return this.<PageResult<PawnTicket>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<PawnTicketPO> wrapper = Wrappers.<PawnTicketPO>lambdaQuery();
                if (query.pawnerId() != null) {
                    wrapper.eq(PawnTicketPO::getPawnerId, query.pawnerId());
                }
                if (query.category() != null) {
                    wrapper.eq(PawnTicketPO::getCategory, query.category().code());
                }
                if (query.status() != null) {
                    wrapper.eq(PawnTicketPO::getStatus, query.status().code());
                }
                // 稳定排序：翻页时同一张票不会在两页间漂移，结果可重复跟纸质票根对号
                wrapper.orderByAsc(PawnTicketPO::getId);
                List<PawnTicketPO> rows = ticketMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<PawnTicket> content = rows.stream()
                        .map(PawnTicketPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // PageHelper 靠 ThreadLocal 传分页参数，必须清，避免污染线程池下一次调用
                PageHelper.clearPage();
            }
        });
    }

    /**
     * 生成 DP-年份-序号：序号是当年已有票号（含已撤销、已删行）最大整数 +1，至少 4 位、超出自然进位。
     * 只在写锁（{@link #inWriteLock}）内调用，锁内串行所以不会撞号；
     * ticket_no 唯一索引是最后防线，极端瞬态冲突由外层整段重试兜底。
     */
    private String nextTicketNo() {
        int year = LocalDate.now(BIZ_ZONE).getYear();
        String prefix = "DP-" + year + "-";
        long maxSeq = 0L;
        for (String no : ticketMapper.findTicketNosByPrefix(prefix + "%")) {
            if (no == null || !no.startsWith(prefix)) {
                continue;
            }
            String tail = no.substring(prefix.length());
            if (tail.chars().allMatch(Character::isDigit)) {
                maxSeq = Math.max(maxSeq, Long.parseLong(tail));
            }
        }
        return prefix + String.format("%04d", maxSeq + 1);
    }

    /**
     * 在全局命名锁保护下执行一段【含事务】的写入：锁由一条独立原始连接持有，
     * 在事务开始前 GET_LOCK、在事务提交/回滚之后才 RELEASE_LOCK（顺序不能颠倒）。
     *
     * 为什么锁要走独立连接而不是 MyBatis 连接：GET_LOCK 绑定连接；
     * 若用事务所在连接，Spring 提交时归还连接会立刻放锁，存在「锁已放、事务未提交」的窗口，
     * 后到的事务取号时读不到刚提交的最大序号，会算出重复号，也可能在「一票一占」判定下双双放行。
     * 独立连接持锁可把锁保到提交之后。
     */
    private <T> T inWriteLock(Supplier<T> action) {
        Connection lockConn;
        try {
            lockConn = dataSource.getConnection();
        } catch (SQLException e) {
            throw new BizException("系统繁忙，请稍后重试");
        }
        try {
            if (!namedLock(lockConn, true)) {
                throw new BizException("系统繁忙，请稍后重试");
            }
            try {
                return action.get();
            } finally {
                // 此时 action 内的事务已提交（或回滚），放锁后后到者必能看到本次写入
                namedLock(lockConn, false);
            }
        } finally {
            try {
                lockConn.close();
            } catch (SQLException ignored) {
                // 连接关闭会自动释放其上的命名锁，不影响主流程
            }
        }
    }

    /** GET_LOCK / RELEASE_LOCK；返回 MySQL 结果（1 成功）。 */
    private boolean namedLock(Connection conn, boolean get) {
        String sql = get ? "SELECT GET_LOCK(?, ?)" : "SELECT RELEASE_LOCK(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, WRITE_LOCK);
            if (get) {
                ps.setInt(2, LOCK_WAIT_SECONDS);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int r = rs.getInt(1);
                    return !rs.wasNull() && r == 1;
                }
                return false;
            }
        } catch (SQLException e) {
            if (get) {
                throw new BizException("系统繁忙，请稍后重试");
            }
            return false;
        }
    }

    /**
     * 阻塞 DB 调用 → 响应式链路桥接器：先从 Reactor Context 取操作人，再切到 boundedElastic，
     * 操作人放进 AuditContextHolder 供审计填充（与当户/当物模块同一套约定，顺序不能颠倒）。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
