package com.somepro.interfaces.rest.ticket.converter;

import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.ticket.model.PawnTicket;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ticket.vo.PawnTicketVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 当票领域对象 → VO 转换器（用户接口层）。Controller 不直接把领域对象塞进 Result。
 */
public final class PawnTicketVoConverter {

    private PawnTicketVoConverter() {
    }

    public static PawnTicketVO toVo(PawnTicket domain) {
        return new PawnTicketVO(
                domain.getId(),
                domain.getTicketNo(),
                domain.getPawnerId(),
                domain.getCollateralId(),
                domain.getCategory() == null ? null : domain.getCategory().code(),
                domain.getPawnAmount(),
                domain.getAppraisedValue(),
                domain.getMonthlyRate(),
                domain.getServiceRate(),
                domain.getStartDate(),
                domain.getDueDate(),
                domain.getTermMonths(),
                domain.getStatus() == null ? null : domain.getStatus().code(),
                domain.getCreateTime(),
                domain.getUpdateTime());
    }

    public static PageVO<PawnTicketVO> toPageVo(PageResult<PawnTicket> page) {
        List<PawnTicketVO> content = page.content().stream()
                .map(PawnTicketVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
