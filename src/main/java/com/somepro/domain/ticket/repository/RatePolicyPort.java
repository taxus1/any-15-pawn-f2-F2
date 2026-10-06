package com.somepro.domain.ticket.repository;

import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.ticket.model.RatePolicy;
import reactor.core.publisher.Mono;

/**
 * 费率配置只读端口：当票开票/改当金时，向费率配置（t_pawn_rate）要某类别【当前启用】的一套配置
 * ——月利率、月综合费率、折当率上限。
 *
 * 只在开票/改当金那一刻读：利率/费率抄进票后即与配置脱钩，配置日后怎么改都不影响已开出的票；
 * 但折当率上限用于实时卡当金，配置停用（DISABLED）或该类别没配，都视为开不了票（返回空）。
 */
public interface RatePolicyPort {

    /** 取某类别当前启用的费率配置；不存在、已停用、已销的类别返回空 Mono。 */
    Mono<RatePolicy> findEnabled(Category category);
}
