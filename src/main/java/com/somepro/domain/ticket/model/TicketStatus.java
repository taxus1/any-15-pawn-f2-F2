package com.somepro.domain.ticket.model;

import com.somepro.common.exception.BizException;

/**
 * 当票状态（纯领域枚举，不依赖任何框架）。
 * <ul>
 *   <li>{@link #ACTIVE} 在当：新开的票默认落这里，当金放出去、当物被占着</li>
 *   <li>{@link #REDEEMED} 已赎：当户还本付息把东西赎回去，票结清</li>
 *   <li>{@link #FORFEITED} 已绝当：到期未赎走绝当处置，票结清</li>
 *   <li>{@link #CANCELLED} 已撤销：开错/撤单，票作废、当物释放，可再开新票</li>
 * </ul>
 * 用枚举名落库（status 列直接存这些字符串）。
 *
 * 「没结清」只认 {@link #ACTIVE}：赎当、绝当、撤销都算结清，结了清同一件当物才允许再开新票。
 */
public enum TicketStatus {

    ACTIVE("ACTIVE", "在当"),
    REDEEMED("REDEEMED", "已赎"),
    FORFEITED("FORFEITED", "已绝当"),
    CANCELLED("CANCELLED", "已撤销");

    private final String code;
    private final String label;

    TicketStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 由外部传入值解析枚举：只认上述四个 code（大小写敏感，列里就是这么存的）。
     * 传 null 返回 null（翻票时表示不按状态筛）；传空串或其它写法都算非法入参，直接挡回。
     */
    public static TicketStatus ofCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (TicketStatus status : values()) {
            if (status.code.equals(trimmed)) {
                return status;
            }
        }
        throw new BizException("当票状态只支持 ACTIVE / REDEEMED / FORFEITED / CANCELLED：" + code);
    }
}
