package com.xinghe.trade.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CustomerConversationPersistence {
    private final CustomerServiceSessionMapper sessionMapper;
    private final CustomerServiceMessageMapper messageMapper;

    public CustomerConversationPersistence(CustomerServiceSessionMapper sessionMapper,
                                           CustomerServiceMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public void saveReply(String userId, String sessionId,
                          CustomerServiceMessage userMessage,
                          CustomerServiceMessage assistantMessage) {
        CustomerServiceSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new IllegalArgumentException("客服会话不存在");
        if (!userId.equals(session.getUserId())) throw new SecurityException("无权访问该会话");

        messageMapper.insert(userMessage);
        messageMapper.insert(assistantMessage);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }
}
