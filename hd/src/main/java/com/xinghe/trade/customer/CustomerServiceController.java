package com.xinghe.trade.customer;

import com.xinghe.trade.common.ApiResponse;
import com.xinghe.trade.logistics.LogisticsQueryService;
import com.xinghe.trade.product.Product;
import com.xinghe.trade.product.ProductQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/customer-service")
public class CustomerServiceController {
    private final CustomerConversationService conversationService;
    private final OrderQueryService orderQueryService;
    private final LogisticsQueryService logisticsQueryService;
    private final ProductQueryService productQueryService;

    public CustomerServiceController(CustomerConversationService conversationService,
                                     OrderQueryService orderQueryService,
                                     LogisticsQueryService logisticsQueryService,
                                     ProductQueryService productQueryService) {
        this.conversationService = conversationService;
        this.orderQueryService = orderQueryService;
        this.logisticsQueryService = logisticsQueryService;
        this.productQueryService = productQueryService;
    }

    @PostMapping("/sessions")
    public ApiResponse<CustomerConversationService.SessionView> createSession(@RequestHeader("X-User-Id") String userId) {
        // 创建客服会话并返回会话标识，前端据此加载后续消息。
        return ApiResponse.ok(conversationService.createSession(requireUser(userId)));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CustomerConversationService.SessionView> session(@RequestHeader("X-User-Id") String userId,
                                                                          @PathVariable String sessionId) {
        // 查询单个会话，同时校验请求用户是否拥有该会话。
        return ApiResponse.ok(conversationService.requireSession(requireUser(userId), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<CustomerConversationService.MessageView>> history(@RequestHeader("X-User-Id") String userId,
                                                                               @PathVariable String sessionId) {
        // 提供会话历史接口，供前端切换会话时恢复消息列表。
        return ApiResponse.ok(conversationService.history(requireUser(userId), sessionId));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestHeader("X-User-Id") String userId,
                           @PathVariable String sessionId,
                           @Valid @RequestBody SendMessageRequest request) {
        // 通过 SSE 返回客服回复，完成后发送 complete 事件并关闭连接。
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            CustomerConversationService.MessageView response = conversationService.reply(requireUser(userId), sessionId, request.content());
            emitter.send(SseEmitter.event().name("message").data(response));
            emitter.send(SseEmitter.event().name("complete").data("DONE"));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderQueryService.OrderView> order(@RequestHeader("X-User-Id") String userId,
                                                           @PathVariable String orderNo) {
        // 提供受当前用户身份保护的订单查询工具接口。
        return ApiResponse.ok(orderQueryService.query(requireUser(userId), orderNo));
    }

    @GetMapping("/orders/{orderNo}/logistics")
    public ApiResponse<LogisticsQueryService.LogisticsView> logistics(@RequestHeader("X-User-Id") String userId,
                                                                       @PathVariable String orderNo) {
        // 提供先校验订单归属、再查询物流记录的工具接口。
        return ApiResponse.ok(logisticsQueryService.query(requireUser(userId), orderNo));
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products(@RequestParam(defaultValue = "") String keyword) {
        // 搜索当前在售商品，供客服工作台和 Agent 工具使用。
        return ApiResponse.ok(productQueryService.search(keyword));
    }

    private String requireUser(String userId) {
        // 统一校验联调请求头，避免空用户身份进入业务服务。
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("X-User-Id 请求头不能为空");
        return userId.trim();
    }

    public record SendMessageRequest(@NotBlank @Size(max = 1000) String content) {}
}
