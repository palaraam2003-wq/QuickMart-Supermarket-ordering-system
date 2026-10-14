package com.rapidmart.order.controller;

import com.rapidmart.order.dto.CheckoutRequest;
import com.rapidmart.order.dto.OrderDto;
import com.rapidmart.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/customer/orders", "/api/v1/orders", "/api/customer/orders"})
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {

    private final OrderService orderService;
    private final com.rapidmart.delivery.service.DeliveryTrackingService deliveryTrackingService;

    public CustomerOrderController(OrderService orderService,
                                   com.rapidmart.delivery.service.DeliveryTrackingService deliveryTrackingService) {
        this.orderService = orderService;
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> checkout(@Valid @RequestBody CheckoutRequest request) {
        return new ResponseEntity<>(orderService.checkout(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto>> getMyOrders(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(orderService.getMyOrders(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/{id}/tracking")
    public ResponseEntity<com.rapidmart.delivery.dto.DeliveryTrackingDto> getOrderTracking(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryTrackingService.getCustomerOrderTracking(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable Long id, @RequestParam(defaultValue = "Customer requested cancellation") String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }
}
