package com.somepro.domain.ticket.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 当票聚合根（纯领域对象，不带任何持久化注解）。
 *
 * 一张当票 = 一笔放款。整条线上最较真的一道口子，核心不变量：
 * 1. 票、户、物一一对应：必须挂在真实未注销的当户名下、押一件真实当物；类别是当物所属类别的快照；
 * 2. 当金顶格卡：当金 / 估值 不得超过该类别配置的折当率上限（{@link #requireWithinLoanCap}），顶上去就挡回；
 *    当金必须为正；
 * 3. 利率/费率是抄来的快照：月利率、月综合费率照类别当前配置抄一份进票，不接受手填，
 *    日后配置改了不影响已开出的票，这样对账才对得平；
 * 4. 估值快照：折当那一刻的评估价值（{@link #appraisedValue}）随票冻结，当物估值再变也不动老票；
 * 5. 到期日期由起当日期按当期月数往后推（{@link #plusMonths}），当期至少 1 个月；
 * 6. 状态：新开默认 {@link TicketStatus#ACTIVE}，往后走已赎/已绝当/已撤销。
 *    只有在当（ACTIVE）的票能改、能撤销；撤销后当物释放、可再开新票。
 *
 * 票号 ticketNo（DP-2026-0001 样式）由仓储按当年序号生成，一票一号、永不复用。
 * 「一件当物只能挂一张没结清的票」是跨聚合口径，由应用层与仓储写临界区共同守住，聚合本身不查库。
 */
@Getter
@Setter
public class PawnTicket extends BaseEntity {

    private Long id;

    /** 当票号，如 DP-2026-0001；开票时由仓储生成，业务上不可改。 */
    private String ticketNo;

    /** 当户 id（t_pawner.id）。 */
    private Long pawnerId;

    /** 押的当物 id（t_collateral.id）。 */
    private Long collateralId;

    /** 类别快照：随当物带出，开票后不随后续类别/配置变化而变。 */
    private Category category;

    /** 当金（元），正数；不得超过 估值 × 折当率上限。 */
    private BigDecimal pawnAmount;

    /** 折当估值快照（元）：开票那一刻当物的评估价值，随票冻结。 */
    private BigDecimal appraisedValue;

    /** 月利率快照：抄自类别当前配置，不接受手填。 */
    private BigDecimal monthlyRate;

    /** 月综合费率快照：抄自类别当前配置，不接受手填。 */
    private BigDecimal serviceRate;

    /** 起当日期。 */
    private LocalDate startDate;

    /** 到期日期：起当日期 + termMonths 个月。 */
    private LocalDate dueDate;

    /** 当期月数，至少 1。 */
    private Integer termMonths;

    private TicketStatus status;

    /**
     * 工厂方法：开立新当票。默认状态 {@link TicketStatus#ACTIVE}。
     *
     * @param pawnerId       当户 id（真实性/未注销由应用层认人）
     * @param collateralId   当物 id
     * @param category       当物所属类别（快照）
     * @param pawnAmount     当金，正数
     * @param appraisedValue 折当时估值快照（当物当前估值）
     * @param policy         该类别当前费率配置（利率/费率快照 + 折当率上限）
     * @param startDate      起当日期
     * @param termMonths     当期月数，至少 1
     */
    public static PawnTicket issue(Long pawnerId, Long collateralId, Category category,
                                   BigDecimal pawnAmount, BigDecimal appraisedValue,
                                   RatePolicy policy, LocalDate startDate, Integer termMonths) {
        if (pawnerId == null) {
            throw new BizException("必须指定当票开在哪位当户名下");
        }
        if (collateralId == null) {
            throw new BizException("必须指定押的是哪件当物");
        }
        if (category == null) {
            throw new BizException("类别不能为空");
        }
        if (policy == null) {
            throw new BizException("该类别尚未配置费率，开不了票");
        }
        requirePositiveAmount(pawnAmount);
        requirePositiveAmount(appraisedValue);
        requireWithinLoanCap(pawnAmount, appraisedValue, policy.maxLoanRatio());
        if (startDate == null) {
            throw new BizException("起当日期不能为空");
        }
        int term = requireValidTerm(termMonths);
        if (policy.monthlyRate() == null || policy.serviceRate() == null) {
            throw new BizException("类别费率配置不完整，开不了票");
        }

        PawnTicket ticket = new PawnTicket();
        ticket.pawnerId = pawnerId;
        ticket.collateralId = collateralId;
        ticket.category = category;
        ticket.pawnAmount = pawnAmount.setScale(2, RoundingMode.HALF_UP);
        ticket.appraisedValue = appraisedValue.setScale(2, RoundingMode.HALF_UP);
        // 利率/费率抄快照：与配置脱钩，配置日后改动不影响本票
        ticket.monthlyRate = policy.monthlyRate();
        ticket.serviceRate = policy.serviceRate();
        ticket.startDate = startDate;
        ticket.termMonths = term;
        ticket.dueDate = plusMonths(startDate, term);
        ticket.status = TicketStatus.ACTIVE;
        return ticket;
    }

    /**
     * 修改在当当票：只准动当金、起当日期、当期月数；票号/当户/当物/类别/估值快照/利率费率快照一律不动。
     * 各字段传 null 表示该项不动。改当金仍按当前类别配置的折当率上限卡（快照利率费率不动，配置只用于卡额）。
     */
    public void revise(BigDecimal pawnAmount, RatePolicy policy,
                       LocalDate startDate, Integer termMonths) {
        requireActive();
        // 传当金时应用层必已带上当前启用的配置，没带配置却来改额属于调用方违约，直接挡
        if (pawnAmount != null && policy == null) {
            throw new BizException("改当金必须提供类别当前的折当率配置");
        }
        BigDecimal newAmount = pawnAmount != null ? pawnAmount : this.pawnAmount;
        requirePositiveAmount(newAmount);
        if (policy != null) {
            requireWithinLoanCap(newAmount, this.appraisedValue, policy.maxLoanRatio());
        }
        LocalDate newStart = startDate != null ? startDate : this.startDate;
        int newTerm = termMonths != null ? requireValidTerm(termMonths) : this.termMonths;

        this.pawnAmount = newAmount.setScale(2, RoundingMode.HALF_UP);
        this.startDate = newStart;
        this.termMonths = newTerm;
        this.dueDate = plusMonths(newStart, newTerm);
        // 利率/费率快照刻意不动：改票不重抄费率，配置变化也不回溯已开出的票
    }

    /**
     * 撤销：只有在当（ACTIVE）的票能撤。撤销后票作废（CANCELLED），当物回到可再处置状态、
     * 可另开新票。已赎/已绝当/已撤销都不能再撤。
     */
    public void cancel() {
        requireActive();
        this.status = TicketStatus.CANCELLED;
    }

    /** 是否没结清 —— 只有 ACTIVE 算没结清，已赎/已绝当/已撤销都算结清。 */
    public boolean isActive() {
        return status == TicketStatus.ACTIVE;
    }

    private void requireActive() {
        if (this.status != TicketStatus.ACTIVE) {
            throw new BizException("只有在当（ACTIVE）的当票才能办理，当前状态："
                    + (this.status == null ? "-" : this.status.label()));
        }
    }

    /**
     * 折当率上限卡：当金 / 估值 不得超过 maxLoanRatio，等价于 当金 不得超过 估值 × maxLoanRatio。
     * 顶格（恰好相等）放行，超过一分都挡回。用 compareTo 比较，不受 scale 干扰。
     */
    private static void requireWithinLoanCap(BigDecimal pawnAmount, BigDecimal appraisedValue,
                                             BigDecimal maxLoanRatio) {
        if (maxLoanRatio == null || maxLoanRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该类别折当率上限未配置，无法核定当金");
        }
        BigDecimal cap = appraisedValue.multiply(maxLoanRatio);
        if (pawnAmount.compareTo(cap) > 0) {
            throw new BizException(String.format(
                    "当金 %s 元超过折当率上限：估值 %s 元 × 上限 %s = 最高可放 %s 元",
                    pawnAmount.stripTrailingZeros().toPlainString(),
                    appraisedValue.stripTrailingZeros().toPlainString(),
                    maxLoanRatio.stripTrailingZeros().toPlainString(),
                    cap.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
    }

    /** 当金/估值必须为正数：null、零、负数一律不收。 */
    private static void requirePositiveAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("当金与估值都必须是正数，零和负数一律不收");
        }
    }

    /** 当期月数必须是正整数。 */
    private static int requireValidTerm(Integer termMonths) {
        if (termMonths == null || termMonths < 1) {
            throw new BizException("当期月数必须是正整数");
        }
        return termMonths;
    }

    /**
     * 到期日期：起当日期往后推 termMonths 个月。用 {@link LocalDate#plusMonths}，
     * 自动处理大小月与月底（如 2026-01-31 +1 月 = 2026-02-28）。
     */
    private static LocalDate plusMonths(LocalDate startDate, int termMonths) {
        return startDate.plusMonths(termMonths);
    }
}
