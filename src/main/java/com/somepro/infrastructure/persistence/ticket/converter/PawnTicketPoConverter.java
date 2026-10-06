package com.somepro.infrastructure.persistence.ticket.converter;

import com.somepro.domain.collateral.model.Category;
import com.somepro.domain.ticket.model.PawnTicket;
import com.somepro.domain.ticket.model.TicketStatus;
import com.somepro.infrastructure.persistence.ticket.po.PawnTicketPO;

/**
 * PawnTicketPO（表）↔ PawnTicket（领域）转换器（基础设施层），PO 不外泄。
 * category / status 两列都存枚举名，读出时 valueOf 还原；库里的值受写入端约束，必为合法枚举值。
 */
public final class PawnTicketPoConverter {

    private PawnTicketPoConverter() {
    }

    public static PawnTicketPO toPo(PawnTicket domain) {
        PawnTicketPO po = new PawnTicketPO();
        po.setId(domain.getId());
        po.setTicketNo(domain.getTicketNo());
        po.setPawnerId(domain.getPawnerId());
        po.setCollateralId(domain.getCollateralId());
        po.setCategory(domain.getCategory() == null ? null : domain.getCategory().code());
        po.setPawnAmount(domain.getPawnAmount());
        po.setAppraisedValue(domain.getAppraisedValue());
        po.setMonthlyRate(domain.getMonthlyRate());
        po.setServiceRate(domain.getServiceRate());
        po.setStartDate(domain.getStartDate());
        po.setDueDate(domain.getDueDate());
        po.setTermMonths(domain.getTermMonths());
        po.setStatus(domain.getStatus() == null ? null : domain.getStatus().code());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PawnTicket toDomain(PawnTicketPO po) {
        PawnTicket domain = new PawnTicket();
        domain.setId(po.getId());
        domain.setTicketNo(po.getTicketNo());
        domain.setPawnerId(po.getPawnerId());
        domain.setCollateralId(po.getCollateralId());
        domain.setCategory(po.getCategory() == null ? null : Category.valueOf(po.getCategory()));
        domain.setPawnAmount(po.getPawnAmount());
        domain.setAppraisedValue(po.getAppraisedValue());
        domain.setMonthlyRate(po.getMonthlyRate());
        domain.setServiceRate(po.getServiceRate());
        domain.setStartDate(po.getStartDate());
        domain.setDueDate(po.getDueDate());
        domain.setTermMonths(po.getTermMonths());
        domain.setStatus(po.getStatus() == null ? null : TicketStatus.valueOf(po.getStatus()));
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
