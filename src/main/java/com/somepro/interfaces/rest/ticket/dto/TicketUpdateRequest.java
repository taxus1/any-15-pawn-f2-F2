package com.somepro.interfaces.rest.ticket.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改当票入参（用户接口层）。只准动当金、起当日期、当期月数，任一项留空表示该项不动。
 *
 * 票号/当户/当物/类别/估值快照/月利率/月综合费率都不允许经本接口改动，故这里不出现这些字段；
 * 非在当（ACTIVE）的票由应用层挡回，改当金仍按当前类别折当率上限卡。
 */
@Getter
@Setter
public class TicketUpdateRequest {

    private Long id;

    /** 新当金（元）；空表示不动当金。 */
    private String pawnAmount;

    /** 新起当日期 yyyy-MM-dd；空表示不动。 */
    private String startDate;

    /** 新当期月数；空表示不动。 */
    private Integer termMonths;
}
