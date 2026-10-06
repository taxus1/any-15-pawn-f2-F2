package com.somepro.interfaces.rest.ticket.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 开立当票入参（用户接口层）。
 *
 * 用可变 bean + @ModelAttribute：WebFlux 下 application/x-www-form-urlencoded 表单、
 * query string 都能直接绑定。金额、日期用字符串接收，由应用层解析（非数字/格式错给明确业务提示）。
 *
 * 当户不接受外部指定 —— 以当物登记时所属的当户为准，避免拿别人的东西开票。
 * 类别/估值/月利率/月综合费率/票号/状态都不由本入参给出：类别估值随当物带出、
 * 利率费率照类别配置抄快照、票号服务端生成、新票固定 ACTIVE。
 * 起当日期可空（默认当天），当期月数可空（默认 1）。
 */
@Getter
@Setter
public class TicketCreateRequest {

    /** 押的当物 id，必须是已入册、且未被在当当票占用的当物。 */
    private Long collateralId;

    /** 当金（元），不得超过 估值 × 类别折当率上限。 */
    private String pawnAmount;

    /** 起当日期 yyyy-MM-dd，空按当天。 */
    private String startDate;

    /** 当期月数，空按 1。 */
    private Integer termMonths;
}
