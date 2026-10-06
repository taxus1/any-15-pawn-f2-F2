package com.somepro.application.ticket;

import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.collateral.model.Collateral;
import com.somepro.domain.collateral.repository.CollateralRepository;
import com.somepro.domain.pawner.model.Pawner;
import com.somepro.domain.pawner.model.PawnerStatus;
import com.somepro.domain.pawner.repository.PawnerRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.ticket.model.PawnTicket;
import com.somepro.domain.ticket.model.PawnTicketQuery;
import com.somepro.domain.ticket.model.TicketStatus;
import com.somepro.domain.ticket.repository.PawnTicketRepository;
import com.somepro.domain.ticket.repository.RatePolicyPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 当票应用服务：编排开立、修改、详情、撤销、按条件翻票五个用例，不写表映射。
 *
 * 出入参用领域对象/基础类型，不认识 PO 与 VO。开票这道口子的对账在这里收口：
 * 1. 认人：当户必须真实存在且未注销（冻结户仍可开票）；
 * 2. 认物：当物必须真实存在，且必须挂在这位当户名下（不能拿别人的东西来开票）；
 * 3. 类别/估值随当物带出，利率/费率/折当率上限读类别当前配置（{@link RatePolicyPort}）：
 *    当金按折当率上限卡，利率费率只抄快照；
 * 4. 一件当物只能挂一张没结清的票 —— 仓储写临界区权威兜底（锁内点 ACTIVE 票）；
 * 5. 到期日期由起当日期按当期月数推算，起当不传按当天（行里时区）。
 */
@Service
public class PawnTicketAppService {

    /** 业务日期统一按行里所在时区算，避免容器 UTC 下起当日/票号跨年。 */
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private final PawnTicketRepository ticketRepository;
    private final PawnerRepository pawnerRepository;
    private final CollateralRepository collateralRepository;
    private final RatePolicyPort ratePolicyPort;

    public PawnTicketAppService(PawnTicketRepository ticketRepository,
                                PawnerRepository pawnerRepository,
                                CollateralRepository collateralRepository,
                                RatePolicyPort ratePolicyPort) {
        this.ticketRepository = ticketRepository;
        this.pawnerRepository = pawnerRepository;
        this.collateralRepository = collateralRepository;
        this.ratePolicyPort = ratePolicyPort;
    }

    /**
     * 开立当票。
     *
     * @param collateralId 押的当物 id（必须真实、挂在其所属当户名下）
     * @param pawnAmount   当金（元），受类别折当率上限卡
     * @param startDate    起当日期（yyyy-MM-dd），空按当天
     * @param termMonths   当期月数，空按 1
     */
    public Mono<PawnTicket> issue(Long collateralId, String pawnAmount, String startDate, Integer termMonths) {
        if (collateralId == null) {
            return Mono.error(new BizException("必须指定押的是哪件当物"));
        }
        BigDecimal amount = parseAmount(pawnAmount, "当金");
        LocalDate start = parseStartDate(startDate);
        int term = termMonths == null ? 1 : termMonths;

        return collateralRepository.findById(collateralId)
                .switchIfEmpty(Mono.error(new BizException("当物不存在，不能拿一件没入册的东西开票")))
                .flatMap(this::requirePledgeable)
                .flatMap(collateral -> requireOpenPawner(collateral.getPawnerId())
                        .flatMap(pawner -> ratePolicyPort.findEnabled(collateral.getCategory())
                                .switchIfEmpty(Mono.error(new BizException(
                                        "类别「" + collateral.getCategory().label()
                                                + "」当前没有启用的费率配置，开不了票")))
                                .map(policy -> PawnTicket.issue(
                                        pawner.getId(), collateral.getId(), collateral.getCategory(),
                                        amount, collateral.getAppraisedValue(), policy, start, term)))
                        // 一票一占：同一件当物已有在当票时由仓储写临界区权威挡回
                        .flatMap(ticketRepository::insert));
    }

    /**
     * 修改在当当票：只动当金、起当日期、当期月数（传 null/空白表示该项不动）。
     * 票号/当户/当物/类别/估值快照/利率费率快照均不可改；改当金按当前类别折当率上限再卡一次。
     */
    public Mono<PawnTicket> update(Long id, String pawnAmount, String startDate, Integer termMonths) {
        if (id == null) {
            return Mono.error(new BizException("必须指定当票 id"));
        }
        BigDecimal amount = pawnAmount == null || pawnAmount.isBlank()
                ? null : parseAmount(pawnAmount, "当金");
        LocalDate start = startDate == null || startDate.isBlank()
                ? null : parseStartDate(startDate);

        return requireTicket(id).flatMap(ticket -> {
            if (amount == null) {
                // 不动当金就不需要费率配置：只改起当日期/当期，聚合跳过折当率卡额、利率费率快照不动
                ticket.revise(null, null, start, termMonths);
                return ticketRepository.update(ticket);
            }
            // 改当金：取类别当前启用的配置，只用其中的折当率上限实时卡额（利率费率快照仍不回写）
            return ratePolicyPort.findEnabled(ticket.getCategory())
                    .switchIfEmpty(Mono.error(new BizException(
                            "类别「" + ticket.getCategory().label()
                                    + "」当前没有启用的费率配置，无法核定当金")))
                    .flatMap(policy -> {
                        ticket.revise(amount, policy, start, termMonths);
                        return ticketRepository.update(ticket);
                    });
        });
    }

    /** 详情：id 或 ticketNo（DP-编号）任一指定。 */
    public Mono<PawnTicket> detail(Long id, String ticketNo) {
        if (id != null) {
            return requireTicket(id);
        }
        if (ticketNo != null && !ticketNo.isBlank()) {
            return ticketRepository.findByTicketNo(ticketNo.trim())
                    .switchIfEmpty(Mono.error(new BizException("当票不存在")));
        }
        return Mono.error(new BizException("请指定要查看的当票（id 或 ticketNo）"));
    }

    /**
     * 撤销：只有在当（ACTIVE）的票能撤。撤完状态置 CANCELLED，当物随即释放、可另开新票；
     * 不做物理删除，票根仍留库可查。
     */
    public Mono<PawnTicket> cancel(Long id) {
        return requireTicket(id).flatMap(ticket -> {
            ticket.cancel();
            return ticketRepository.update(ticket);
        });
    }

    /** 翻票：当户/类别/状态随意拼，都不填翻整本；每行带 ticketNo，分页稳定走，便于跟纸质票根核对。 */
    public Mono<PageResult<PawnTicket>> page(int pageNum, int pageSize,
                                             Long pawnerId, String category, String status) {
        if (pageNum < 1 || pageSize < 1) {
            return Mono.error(new BizException("页码与每页条数必须为正整数"));
        }
        PawnTicketQuery query = PawnTicketQuery.of(
                pawnerId,
                Category.ofCode(blankToNull(category)),
                TicketStatus.ofCode(blankToNull(status)));
        return ticketRepository.page(pageNum, pageSize, query);
    }

    /** 认物：只校验当物真实存在；能不能开票的权威口径是「这件当物有没有没结清的票」，由仓储写临界区点。 */
    private Mono<Collateral> requirePledgeable(Collateral collateral) {
        if (collateral.getCategory() == null || collateral.getAppraisedValue() == null) {
            return Mono.error(new BizException("该当物类别或估值缺失，无法折当开票"));
        }
        return Mono.just(collateral);
    }

    /** 认人：当户必须真实存在，且不是已注销状态；冻结户仍可开票。 */
    private Mono<Pawner> requireOpenPawner(Long pawnerId) {
        return pawnerRepository.findById(pawnerId)
                .switchIfEmpty(Mono.error(new BizException("当户不存在，当票开不到一个不存在的人头上")))
                .handle((pawner, sink) -> {
                    if (pawner.getStatus() == PawnerStatus.CLOSED) {
                        sink.error(new BizException("该当户已注销，不能再向其名下开票"));
                    } else {
                        sink.next(pawner);
                    }
                });
    }

    private Mono<PawnTicket> requireTicket(Long id) {
        return ticketRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("当票不存在")));
    }

    private BigDecimal parseAmount(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(field + "不能为空");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(field + "必须是正数，零和负数一律不收");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BizException(field + "必须是正数金额：" + raw);
        }
    }

    /** 起当日期：空按行里当天；非空必须是 yyyy-MM-dd。 */
    private LocalDate parseStartDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(BIZ_ZONE);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("起当日期必须是 yyyy-MM-dd 格式：" + raw);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
