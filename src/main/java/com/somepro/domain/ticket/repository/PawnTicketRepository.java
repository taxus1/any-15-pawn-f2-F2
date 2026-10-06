package com.somepro.domain.ticket.repository;

import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.ticket.model.PawnTicket;
import com.somepro.domain.ticket.model.PawnTicketQuery;
import reactor.core.publisher.Mono;

/**
 * 当票聚合的仓储端口（领域层定义，基础设施层实现）。
 *
 * 两条硬约束由实现保证：
 * 1. 票号全局唯一（DP-年份-序号，一票一号、销了也不复用）——MySQL 命名锁在全实例串行化，
 *    锁内取当年最大序号 +1，撞号再整段重试；uk_ticket_no 是最后防线；
 * 2. 一件当物只能挂一张没结清（ACTIVE）的票 —— {@link #insert} 在同一写锁临界区里先点
 *    该当物有没有 ACTIVE 票，有就业务挡回，把并发抢同一件当物的情形也兜在锁内。
 */
public interface PawnTicketRepository {

    /**
     * 开票：分配雪花 id、生成全局唯一票号（DP-年份-序号）、写临界区确认当物未被在当票占用后落库。
     * 同一件当物已被在当票占用时抛业务异常。返回回填票号与审计字段后的领域对象。
     */
    Mono<PawnTicket> insert(PawnTicket ticket);

    /**
     * 修改：票号/当户/当物/类别/估值快照/利率费率快照永不改，只更新当金/起当日期/到期/当期等可变字段。
     * 目标不存在（含已逻辑删除）时抛业务异常。
     */
    Mono<PawnTicket> update(PawnTicket ticket);

    Mono<PawnTicket> findById(Long id);

    Mono<PawnTicket> findByTicketNo(String ticketNo);

    /** 按条件翻票；逻辑删除的不出现，稳定按 id 升序分页，每行都带票号。 */
    Mono<PageResult<PawnTicket>> page(int pageNum, int pageSize, PawnTicketQuery query);
}
