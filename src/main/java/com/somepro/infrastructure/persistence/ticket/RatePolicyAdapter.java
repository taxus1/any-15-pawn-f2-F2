package com.somepro.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.ticket.model.RatePolicy;
import com.somepro.domain.ticket.repository.RatePolicyPort;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.ticket.po.PawnRatePO;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * 费率配置只读端口适配器（基础设施层）：按类别读 t_pawn_rate 当前【启用】的一行。
 *
 * 读的是「当前配置」：开票时把利率/费率抄进票即与本表脱钩；本表之后怎么改都不影响已开出的票。
 * del_flag 由 @TableLogic 自动过滤；这里再显式要求 status='ENABLED'，停用的类别视为没配置、开不了票。
 */
@Component
public class RatePolicyAdapter implements RatePolicyPort {

    private final PawnRateMapper pawnRateMapper;

    public RatePolicyAdapter(PawnRateMapper pawnRateMapper) {
        this.pawnRateMapper = pawnRateMapper;
    }

    @Override
    public Mono<RatePolicy> findEnabled(Category category) {
        return blocking(() -> {
            PawnRatePO po = pawnRateMapper.selectOne(Wrappers.<PawnRatePO>lambdaQuery()
                    .eq(PawnRatePO::getCategory, category.code())
                    .eq(PawnRatePO::getStatus, "ENABLED"));
            if (po == null) {
                return null;
            }
            return new RatePolicy(Category.valueOf(po.getCategory()), po.getMonthlyRate(),
                    po.getServiceRate(), po.getMaxLoanRatio());
        });
    }

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
