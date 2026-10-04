package com.xinghe.trade.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaqServiceTest {
    private final FaqService faqService = new FaqService();

    @Test
    void returnsAfterSalePolicyForRefundQuestion() {
        var answer = faqService.answer("我想申请退款");
        assertThat(answer).isPresent();
        assertThat(answer.orElseThrow().intent()).isEqualTo("AFTER_SALE_POLICY");
    }

    @Test
    void returnsEmptyForUnknownQuestion() {
        assertThat(faqService.answer("你是谁")).isEmpty();
    }
}
