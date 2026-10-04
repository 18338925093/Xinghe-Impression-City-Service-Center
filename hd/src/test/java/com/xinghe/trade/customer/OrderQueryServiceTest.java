package com.xinghe.trade.customer;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderQueryServiceTest {
    @Test
    void rejectsOrderOwnedByAnotherUser() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries("order:XH12345678")).thenReturn(Map.of("userId", "user-1", "status", "PAID"));

        OrderQueryService service = new OrderQueryService(redis);
        assertThatThrownBy(() -> service.query("user-2", "XH12345678"))
                .isInstanceOf(SecurityException.class);
    }
}
