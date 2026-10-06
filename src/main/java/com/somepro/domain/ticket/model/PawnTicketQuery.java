package com.somepro.domain.ticket.model;

import com.somepro.domain.collateral.model.Category;

/**
 * 当票翻票条件（不可变值对象）。
 *
 * 当户（pawnerId）、类别、状态随意拼，任一项为 null 即不参与过滤；全 null 翻整本票簿。
 * 类别/状态在进入本对象前由各自的 {@code ofCode} 解析过，非法写法已在解析阶段挡回。
 */
public record PawnTicketQuery(Long pawnerId,
                              Category category,
                              TicketStatus status) {

    public static PawnTicketQuery of(Long pawnerId, Category category, TicketStatus status) {
        return new PawnTicketQuery(pawnerId, category, status);
    }
}
