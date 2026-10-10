package com.rapidmart.report.service;

import com.rapidmart.common.enums.DeliveryStatus;
import com.rapidmart.common.enums.OrderStatus;
import com.rapidmart.common.enums.RefundStatus;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.delivery.entity.Delivery;
import com.rapidmart.delivery.repository.DeliveryRepository;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.order.entity.Order;
import com.rapidmart.order.repository.OrderRepository;
import com.rapidmart.refund.entity.RefundRequest;
import com.rapidmart.refund.repository.RefundRequestRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Report rules:
 * - Date ranges are inclusive and apply to the order creation date in the server's local time zone.
 * - Sales count every order except CANCELLED and AWAITING_PAYMENT (i.e. confirmed, in delivery or delivered).
 * - Refunded total is the sum of REFUNDED refund requests for those orders; net sales = gross sales - refunded total.
 */
@Service
public class ReportService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final DeliveryRepository deliveryRepository;
    private final RefundRequestRepository refundRequestRepository;

    public ReportService(OrderRepository orderRepository,
                         InventoryRepository inventoryRepository,
                         DeliveryRepository deliveryRepository,
                         RefundRequestRepository refundRequestRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.deliveryRepository = deliveryRepository;
        this.refundRequestRepository = refundRequestRepository;
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "End date cannot be before start date.");
        }
    }

    private static boolean inRange(Order order, LocalDate startDate, LocalDate endDate) {
        if (order.getCreatedAt() == null) return false;
        LocalDate placed = order.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        return (startDate == null || !placed.isBefore(startDate)) && (endDate == null || !placed.isAfter(endDate));
    }

    private List<Order> ordersInRange(LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        return orderRepository.findAll().stream()
                .filter(o -> inRange(o, startDate, endDate))
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();
    }

    private static boolean countsAsSale(Order order) {
        return order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.AWAITING_PAYMENT;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSalesSummary(LocalDate startDate, LocalDate endDate) {
        List<Order> sales = ordersInRange(startDate, endDate).stream().filter(ReportService::countsAsSale).toList();
        Set<Long> saleIds = sales.stream().map(Order::getId).collect(Collectors.toSet());

        BigDecimal totalRevenue = sales.stream().map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        // Refunded undelivered orders are cancelled (so already excluded); refunds of delivered orders are subtracted.
        BigDecimal refundedTotal = refundRequestRepository.findByRefundStatus(RefundStatus.REFUNDED).stream()
                .filter(r -> saleIds.contains(r.getOrder().getId()))
                .map(RefundRequest::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        summary.put("totalOrders", (long) sales.size());
        summary.put("totalRevenue", totalRevenue);
        summary.put("refundedTotal", refundedTotal);
        summary.put("netRevenue", totalRevenue.subtract(refundedTotal));
        summary.put("basis", "Inclusive order-creation dates (server time zone); excludes CANCELLED and AWAITING_PAYMENT orders");
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInventorySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", inventoryRepository.count());
        summary.put("lowStockCount", (long) inventoryRepository.findLowStockInventory().size());
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDeliverySummary() {
        Map<DeliveryStatus, Long> counts = deliveryRepository.findAll().stream()
                .collect(Collectors.groupingBy(Delivery::getDeliveryStatus, () -> new EnumMap<>(DeliveryStatus.class), Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDeliveries", counts.values().stream().mapToLong(Long::longValue).sum());
        summary.put("deliveredCount", counts.getOrDefault(DeliveryStatus.DELIVERED, 0L));
        summary.put("failedCount", counts.getOrDefault(DeliveryStatus.FAILED, 0L));
        for (DeliveryStatus status : DeliveryStatus.values()) {
            summary.put(status.name().toLowerCase(), counts.getOrDefault(status, 0L));
        }
        return summary;
    }

    @Transactional(readOnly = true)
    public String generateSalesCsv() {
        return generateSalesCsv(null, null);
    }

    @Transactional(readOnly = true)
    public String generateSalesCsv(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = ordersInRange(startDate, endDate);
        Map<Long, RefundRequest> refunds = refundRequestRepository.findByRefundStatus(RefundStatus.REFUNDED).stream()
                .collect(Collectors.toMap(r -> r.getOrder().getId(), Function.identity(), (a, b) -> a));

        StringBuilder csv = new StringBuilder();
        csv.append("Order Number,Customer Email,Delivery Date,Status,Total Amount (LKR),Order Date,Refunded Amount (LKR)\n");

        for (Order order : orders) {
            RefundRequest refund = refunds.get(order.getId());
            csv.append(csvField(order.getOrderNumber())).append(',')
               .append(csvField(order.getUser().getEmail())).append(',')
               .append(csvField(order.getDeliveryDate().toString())).append(',')
               .append(csvField(order.getStatus().name())).append(',')
               .append(csvField(order.getTotalAmount().toPlainString())).append(',')
               .append(csvField(order.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString())).append(',')
               .append(csvField(refund != null ? refund.getRefundAmount().toPlainString() : "0.00"))
               .append('\n');
        }

        return csv.toString();
    }

    /** Neutralises CSV formula injection (=, +, -, @, tab, CR) and quotes fields containing separators. */
    static String csvField(String field) {
        if (field == null) return "";
        String value = field.trim();
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 && !value.matches("^-?\\d+(\\.\\d+)?$")) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
