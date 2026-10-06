package com.somepro.domain.ticket.model;

import com.somepro.domain.collateral.model.Category;

import java.math.BigDecimal;

/**
 * 某类别当前的费率配置快照（不可变领域值对象），读自 t_pawn_rate。
 *
 * 开票时照着它把月利率、月综合费率抄一份到当票上，并拿 {@link #maxLoanRatio}（折当率上限）卡当金：
 * 配置只在开票/改当金那一刻「读」，抄进票里的快照此后与配置脱钩——日后配置改了不影响已开出的票，对账才对得平。
 *
 * @param category     适用类别
 * @param monthlyRate  月利率
 * @param serviceRate  月综合费率
 * @param maxLoanRatio 折当率上限（0~1）：当金不得超过 估值 × 该值
 */
public record RatePolicy(Category category,
                         BigDecimal monthlyRate,
                         BigDecimal serviceRate,
                         BigDecimal maxLoanRatio) {
}
