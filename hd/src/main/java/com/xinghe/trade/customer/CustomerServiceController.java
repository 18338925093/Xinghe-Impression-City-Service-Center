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
        return ApiResponse.ok(conversationService.createSession(requireUser(userId)));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<CustomerConversationService.SessionView> session(@RequestHeader("X-User-Id") String userId,
                                                                          @PathVariable String sessionId) {
        return ApiResponse.ok(conversationService.requireSession(requireUser(userId), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<CustomerConversationService.MessageView>> history(@RequestHeader("X-User-Id") String userId,
                                                                               @PathVariable String sessionId) {
        return ApiResponse.ok(conversationService.history(requireUser(userId), sessionId));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestHeader("X-User-Id") String userId,
                           @PathVariable String sessionId,
                           @Valid @RequestBody SendMessageRequest request) {
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
        return ApiResponse.ok(orderQueryService.query(requireUser(userId), orderNo));
    }

    @GetMapping("/orders/{orderNo}/logistics")
    public ApiResponse<LogisticsQueryService.LogisticsView> logistics(@RequestHeader("X-User-Id") String userId,
                                                                       @PathVariable String orderNo) {
        return ApiResponse.ok(logisticsQueryService.query(requireUser(userId), orderNo));
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(productQueryService.search(keyword));
    }

    private String requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("X-User-Id 请求头不能为空");
        return userId.trim();
    }

    public record SendMessageRequest(@NotBlank @Size(max = 1000) String content) {}
}
