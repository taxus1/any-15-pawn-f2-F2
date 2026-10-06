package com.somepro.interfaces.rest.ticket.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当票对外对象（不可变 record）：开票/修改/详情/翻票共用。
 *
 * 每行都带 ticketNo（DP-年份-序号），方便柜台跟纸质票根核对；
 * 利率/费率/估值都是随票冻结的快照，原样回给柜台对账；
 * 刻意不暴露 delFlag / createBy / updateBy 等内部字段。
 */
public record PawnTicketVO(Long id,
                           String ticketNo,
                           Long pawnerId,
                           Long collateralId,
                           String category,
                           BigDecimal pawnAmount,
                           BigDecimal appraisedValue,
                           BigDecimal monthlyRate,
                           BigDecimal serviceRate,
                           LocalDate startDate,
                           LocalDate dueDate,
                           Integer termMonths,
                           String status,
                           LocalDateTime createTime,
                           LocalDateTime updateTime) implements Serializable {
}
