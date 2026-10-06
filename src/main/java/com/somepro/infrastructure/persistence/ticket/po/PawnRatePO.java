package com.somepro.infrastructure.persistence.ticket.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * t_pawn_rate 费率配置表的持久化对象（PO，基础设施层）。只描述表结构，不放业务规则。
 *
 * 当票模块只【读】它：取某类别当前启用的一套配置（月利率、月综合费率、折当率上限）。
 * 表已由 doc/schema/pawn.sql 建好，一个类别一行（uk_category），列名即契约。
 */
@Getter
@Setter
@TableName("t_pawn_rate")
public class PawnRatePO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("category")
    private String category;

    @TableField("monthly_rate")
    private BigDecimal monthlyRate;

    @TableField("service_rate")
    private BigDecimal serviceRate;

    @TableField("max_loan_ratio")
    private BigDecimal maxLoanRatio;

    @TableField("status")
    private String status;
}
