package com.somepro.interfaces.rest.ticket;

import com.somepro.application.ticket.PawnTicketAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ticket.converter.PawnTicketVoConverter;
import com.somepro.interfaces.rest.ticket.dto.TicketCreateRequest;
import com.somepro.interfaces.rest.ticket.dto.TicketIdRequest;
import com.somepro.interfaces.rest.ticket.dto.TicketUpdateRequest;
import com.somepro.interfaces.rest.ticket.vo.PawnTicketVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 当票模块用户接口层：开立、修改、详情、撤销、按条件翻票。
 *
 * 只做协议适配（参数解析、VO 转换、Result 包装），业务编排在 {@link PawnTicketAppService}。
 * 入参统一走 @ModelAttribute / @RequestParam：表单 / query string / x-www-form-urlencoded 都能接，
 * 便于柜台端直接调用。
 */
@RestController
@RequestMapping("/api/ticket")
public class PawnTicketController {

    private final PawnTicketAppService ticketAppService;

    public PawnTicketController(PawnTicketAppService ticketAppService) {
        this.ticketAppService = ticketAppService;
    }

    /**
     * 开立当票：认人认物、按类别折当率上限卡当金、利率费率抄类别配置快照、到期按起当+当期推算；
     * 票号服务端按 DP-年份-序号 生成，一件当物只许挂一张在当票。
     */
    @PostMapping("/create")
    public Mono<Result<PawnTicketVO>> create(@ModelAttribute TicketCreateRequest request) {
        return ticketAppService.issue(request.getCollateralId(), request.getPawnAmount(),
                        request.getStartDate(), request.getTermMonths())
                .map(PawnTicketVoConverter::toVo)
                .map(Result::ok);
    }

    /** 修改：只准动当金/起当日期/当期月数；非在当票挡回，改当金仍卡折当率上限。 */
    @PostMapping("/update")
    public Mono<Result<PawnTicketVO>> update(@ModelAttribute TicketUpdateRequest request) {
        return ticketAppService.update(request.getId(), request.getPawnAmount(),
                        request.getStartDate(), request.getTermMonths())
                .map(PawnTicketVoConverter::toVo)
                .map(Result::ok);
    }

    /** 撤销：仅在当（ACTIVE）票撤得动，撤后当物释放、可再开新票；票根留库不物理删。 */
    @PostMapping("/cancel")
    public Mono<Result<PawnTicketVO>> cancel(@ModelAttribute TicketIdRequest request) {
        return ticketAppService.cancel(request.getId())
                .map(PawnTicketVoConverter::toVo)
                .map(Result::ok);
    }

    /** 详情：id 或 ticketNo 任一指定。 */
    @GetMapping("/detail")
    public Mono<Result<PawnTicketVO>> detail(@RequestParam(required = false) Long id,
                                             @RequestParam(required = false) String ticketNo) {
        return ticketAppService.detail(id, ticketNo)
                .map(PawnTicketVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 翻票：当户/类别/状态随意拼，都不填翻整本。
     * pageNum/pageSize 由请求说了算，每行带 ticketNo 便于与纸质票根对号。
     */
    @GetMapping("/list")
    public Mono<Result<PageVO<PawnTicketVO>>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) Long pawnerId,
                                                   @RequestParam(required = false) String category,
                                                   @RequestParam(required = false) String status) {
        return ticketAppService.page(pageNum, pageSize, pawnerId, category, status)
                .map(PawnTicketVoConverter::toPageVo)
                .map(Result::ok);
    }
}
