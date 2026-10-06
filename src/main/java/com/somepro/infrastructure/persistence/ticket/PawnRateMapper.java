package com.somepro.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.ticket.po.PawnRatePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 费率配置 Mapper（基础设施层）。当票模块只通过 BaseMapper 的条件查询读它，不做任何写入。
 *
 * 阻塞 JDBC API，只能在适配器的 blocking(...) 桥接里调用。
 */
@Mapper
public interface PawnRateMapper extends BaseMapper<PawnRatePO> {
}
