package com.xinghe.trade.customer;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class FaqService {
    public Optional<FaqAnswer> answer(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (containsAny(text, "退款", "退货", "退换")) {
            return Optional.of(new FaqAnswer("AFTER_SALE_POLICY", "未发货订单可以申请取消；已收货商品请在订单详情提交售后申请，具体是否支持退换货以商品规则和店铺审核结果为准。"));
        }
        if (containsAny(text, "配送", "快递", "发货", "几天到")) {
            return Optional.of(new FaqAnswer("DELIVERY_POLICY", "常规订单会在支付成功后 48 小时内发货，物流时效以承运商实际揽收和配送进度为准。你也可以提供订单号，我帮你查询订单状态。"));
        }
        if (containsAny(text, "支付", "付款", "支付方式")) {
            return Optional.of(new FaqAnswer("PAYMENT_POLICY", "目前支持在线支付。提交支付后请勿重复点击，若页面重试，系统会通过幂等 Token 避免重复扣款。"));
        }
        if (containsAny(text, "人工", "客服", "电话")) {
            return Optional.of(new FaqAnswer("HUMAN_SERVICE", "我可以先帮你查询订单和规则；如果仍未解决，请回复“转人工”，客服会继续处理。"));
        }
        return Optional.empty();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    public record FaqAnswer(String intent, String answer) {}
}
