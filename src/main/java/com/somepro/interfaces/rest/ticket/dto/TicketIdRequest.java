package com.somepro.interfaces.rest.ticket.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销当票入参（用户接口层）：按 id 指定要撤销的票。仅在当（ACTIVE）的票撤得动。 */
@Getter
@Setter
public class TicketIdRequest {

    private Long id;
}
