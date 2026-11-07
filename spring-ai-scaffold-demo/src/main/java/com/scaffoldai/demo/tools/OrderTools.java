package com.scaffoldai.demo.tools;

import com.scaffoldai.tool.AiTool;
import com.scaffoldai.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示工具：把一个 Spring Bean 的方法暴露给模型调用。
 * 只要方法标注 @AiTool，就会被 ToolRegistry 自动扫描（无需额外注册）。
 */
@Component
public class OrderTools {

    @AiTool(name = "order_status",
            description = "按订单号查询订单状态、金额与预计送达时间。用户问「我的订单到哪了」时调用。")
    public String orderStatus(@ToolParam("订单号，例如 SO20261107001") String orderId) {
        Map<String, String> orders = new LinkedHashMap<>();
        orders.put("SO20261107001", "状态=已发货，金额=￥399.00，承运商=顺丰，预计送达=2026-11-09");
        orders.put("SO20261107002", "状态=待付款，金额=￥129.00，支付截止=2026-11-08 23:59");
        orders.put("SO20261107003", "状态=已签收，金额=￥88.00，签收时间=2026-11-06 14:20");
        String value = orders.get(orderId);
        return value == null ? "未查询到订单 " + orderId + "，请确认订单号是否正确" : "订单 " + orderId + "：" + value;
    }

    @AiTool(name = "shipping_fee",
            description = "估算运费：按城市与重量计算。用户询问「寄到某地多少钱」时调用。")
    public String shippingFee(@ToolParam("收货城市，例如 天津") String city,
                              @ToolParam("重量，单位 kg") double weightKg) {
        double unit = switch (city == null ? "" : city.trim()) {
            case "北京", "天津", "河北" -> 8.0;
            case "上海", "江苏", "浙江" -> 10.0;
            case "新疆", "西藏" -> 22.0;
            default -> 12.0;
        };
        double extra = Math.max(0, weightKg - 1.0) * 3.0;
        BigDecimal total = BigDecimal.valueOf(unit + extra).setScale(2, RoundingMode.HALF_UP);
        return String.format("寄往%s，重量 %.2fkg，预估运费 ￥%s（首重 %.2f 元，续重 3 元/kg）",
                city, weightKg, total.toPlainString(), unit);
    }

    @AiTool(name = "refund_request",
            description = "提交退款申请（演示用，返回模拟工单号）。用户明确要求退款时调用。")
    public String refundRequest(@ToolParam("订单号") String orderId,
                                @ToolParam("退款原因") String reason) {
        String ticket = "RF" + Math.abs((orderId + reason).hashCode());
        return "退款申请已提交，工单号 " + ticket + "，订单 " + orderId + "，原因：" + reason
                + "。预计 1~3 个工作日审核完成。";
    }
}
