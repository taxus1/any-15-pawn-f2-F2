package com.somepro.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.ticket.po.PawnTicketPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 当票 Mapper（基础设施层）。
 *
 * BaseMapper 覆盖常规 CRUD；票号生成需要自定义语义（取当年全部票号，含已撤销/已删行），用注解 SQL 写死，不建 XML。
 * 「一件当物是否被在当票占用」也在本 Mapper 点，供写临界区在锁内权威判定。
 * 写临界区的命名锁不走 MyBatis（要用独立于事务的连接持锁），见 PawnTicketRepositoryImpl#inWriteLock。
 *
 * 阻塞 JDBC API，只能在仓储适配器的 blocking(...) 桥接里调用。
 */
@Mapper
public interface PawnTicketMapper extends BaseMapper<PawnTicketPO> {

    /**
     * 取某年全部当票号（序号在 Java 侧取最大，只选 ticket_no 一列，数据量小）。
     *
     * 刻意不带 del_flag = 0：票号一经分配即永久占用，撤销/逻辑删除的票也不把号再发出去
     * （否则同一 DP 号在账上先后指向两张票）。
     * 不能直接 ORDER BY 字符串 DESC LIMIT 1：字符串排序下 DP-2026-9999 会排在 DP-2026-10000 前面。
     */
    @Select("SELECT ticket_no FROM t_pawn_ticket WHERE ticket_no LIKE #{prefix}")
    List<String> findTicketNosByPrefix(@Param("prefix") String prefix);

    /**
     * 该当物是否已被一张没结清（ACTIVE）且未删除的票占用。
     * 只点 ACTIVE：已赎/已绝当/已撤销都算结清，结了清同一件当物才允许再开新票。
     */
    @Select("SELECT EXISTS(SELECT 1 FROM t_pawn_ticket WHERE collateral_id = #{collateralId} "
            + "AND status = 'ACTIVE' AND del_flag = 0)")
    int existsActiveByCollateral(@Param("collateralId") Long collateralId);
}
