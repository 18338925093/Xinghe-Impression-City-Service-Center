package com.xinghe.trade.customer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CustomerConversationService {
    private static final int CONTEXT_LIMIT = 20;
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);
    private static final Pattern ORDER_NO = Pattern.compile("(?i)XH[0-9A-Z]{8,}");

    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;
    private final StringRedisTemplate redis;
    private final FaqService faqService;
    private final OrderQueryService orderQueryService;
    private final CustomerAgentService customerAgentService;
    private final CustomerConversationPersistence persistence;

    public CustomerConversationService(CustomerServiceSessionMapper sessionMapper,
                                       CustomerServiceMessageMapper messageMapper,
                                       StringRedisTemplate redis,
                                       FaqService faqService,
                                       OrderQueryService orderQueryService,
                                       CustomerAgentService customerAgentService,
                                       CustomerConversationPersistence persistence) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.redis = redis;
        this.faqService = faqService;
        this.orderQueryService = orderQueryService;
        this.customerAgentService = customerAgentService;
        this.persistence = persistence;
    }

    @Transactional
    public SessionView createSession(String userId) {
        LocalDateTime now = LocalDateTime.now();
        CustomerServiceSession session = new CustomerServiceSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setStatus("OPEN");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return toSessionView(session);
    }

    public MessageView reply(String userId, String sessionId, String content) {
        requireOwnedSession(userId, sessionId);
        List<String> context = new ArrayList<>();
        List<String> previousContext = redis.opsForList().range("customer:session:context:" + sessionId, 0, -1);
        if (previousContext != null) context.addAll(previousContext);
        context.add("USER: " + content);

        Reply reply = route(userId, content, context);
        LocalDateTime now = LocalDateTime.now();
        CustomerServiceMessage userMessage = message(sessionId, userId, "USER", "USER_QUERY", content, now);
        CustomerServiceMessage assistantMessage = message(sessionId, userId, "ASSISTANT", reply.intent(), reply.content(), LocalDateTime.now());
        persistence.saveReply(userId, sessionId, userMessage, assistantMessage);
        appendContext(sessionId, "USER: " + content);
        appendContext(sessionId, "ASSISTANT: " + reply.content());
        return new MessageView(assistantMessage.getMessageId(), sessionId, "ASSISTANT", reply.intent(), reply.content(), assistantMessage.getCreatedAt());
    }

    public SessionView requireSession(String userId, String sessionId) {
        return toSessionView(requireOwnedSession(userId, sessionId));
    }

    public List<MessageView> history(String userId, String sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<CustomerServiceMessage>()
                        .eq(CustomerServiceMessage::getSessionId, sessionId)
                        .orderByAsc(CustomerServiceMessage::getCreatedAt))
                .stream()
                .map(message -> new MessageView(message.getMessageId(), message.getSessionId(), message.getMessageType(),
                        message.getIntent(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    private Reply route(String userId, String content, List<String> context) {
        String orderNo = extractOrderNo(content);
        boolean followUp = context.size() > 1 && containsAny(content, "那个", "它", "这单", "这个", "上述", "刚才", "我的订单", "订单", "物流", "快递", "配送", "状态");
        if (orderNo == null && !followUp) {
            Optional<FaqService.FaqAnswer> faqAnswer = faqService.answer(content);
            if (faqAnswer.isPresent()) {
                FaqService.FaqAnswer answer = faqAnswer.get();
                return new Reply(answer.intent(), answer.answer());
            }
        }

        Optional<String> agentReply = customerAgentService.reply(userId, context);
        if (agentReply.isPresent()) return new Reply("AGENT", agentReply.get());

        try {
            if (orderNo != null && containsAny(content, "物流", "快递", "配送")) {
                return new Reply("LOGISTICS_QUERY", "已识别订单 " + orderNo + "，物流查询接口已准备就绪；当前订单物流状态请以物流同步记录为准。" );
            }
            if (orderNo != null && containsAny(content, "订单", "订单号", "状态", "支付", "付款")) {
                OrderQueryService.OrderView order = orderQueryService.query(userId, orderNo);
                return new Reply("ORDER_QUERY", formatOrder(order));
            }
            return faqService.answer(content)
                    .map(answer -> new Reply(answer.intent(), answer.answer()))
                    .orElse(new Reply("UNSUPPORTED", "我可以帮你查询订单状态、商品信息、物流进度和售后规则。请提供订单号，或描述你遇到的问题；复杂情况可以回复“转人工”。"));
        } catch (SecurityException ex) {
            return new Reply("ORDER_ACCESS_DENIED", "为了保护账户安全，我只能查询当前登录用户自己的订单。请确认登录账号后重试。" );
        } catch (IllegalArgumentException ex) {
            return new Reply("ORDER_NOT_FOUND", "暂时没有找到这个订单，请检查订单号是否正确。" );
        }
    }

    private String formatOrder(OrderQueryService.OrderView order) {
        return "订单 " + order.orderNo() + " 当前状态为“" + order.status() + "”，商品 SKU：" + order.skuId()
                + "，数量：" + order.quantity() + "，金额：" + order.amount() + "。";
    }

    private CustomerServiceSession requireOwnedSession(String userId, String sessionId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) throw new IllegalArgumentException("用户或会话信息不能为空");
        CustomerServiceSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new IllegalArgumentException("客服会话不存在");
        if (!userId.equals(session.getUserId())) throw new SecurityException("无权访问该会话");
        return session;
    }

    private CustomerServiceMessage message(String sessionId, String userId, String type, String intent, String content, LocalDateTime createdAt) {
        CustomerServiceMessage message = new CustomerServiceMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setMessageType(type);
        message.setIntent(intent);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }

    private void appendContext(String sessionId, String message) {
        String key = "customer:session:context:" + sessionId;
        redis.opsForList().rightPush(key, message);
        redis.opsForList().trim(key, -CONTEXT_LIMIT, -1);
        redis.expire(key, CONTEXT_TTL);
    }

    private String extractOrderNo(String content) {
        Matcher matcher = ORDER_NO.matcher(content == null ? "" : content);
        return matcher.find() ? matcher.group().toUpperCase() : null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private SessionView toSessionView(CustomerServiceSession session) {
        return new SessionView(session.getSessionId(), session.getUserId(), session.getStatus(), session.getCreatedAt(), session.getUpdatedAt());
    }

    public record SessionView(String sessionId, String userId, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record MessageView(String messageId, String sessionId, String messageType, String intent, String content, LocalDateTime createdAt) {}
    private record Reply(String intent, String content) {}
}

