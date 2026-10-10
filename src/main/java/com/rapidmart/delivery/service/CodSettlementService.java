package com.rapidmart.delivery.service;

import com.rapidmart.billing.service.BillingService;
import com.rapidmart.common.enums.PaymentStatus;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.delivery.dto.CodSettlementDto;
import com.rapidmart.delivery.dto.CodSummaryDto;
import com.rapidmart.delivery.entity.CodSettlement;
import com.rapidmart.delivery.repository.CodSettlementRepository;
import com.rapidmart.payment.entity.Payment;
import com.rapidmart.payment.repository.PaymentRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class CodSettlementService {

    private final CodSettlementRepository codSettlementRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;

    public CodSettlementService(CodSettlementRepository codSettlementRepository, UserRepository userRepository,
                                PaymentRepository paymentRepository, BillingService billingService) {
        this.codSettlementRepository = codSettlementRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.billingService = billingService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public List<CodSettlementDto> getUnconfirmedSettlements() {
        return codSettlementRepository.findByConfirmedByCashierFalse().stream().map(CodSettlementDto::from).toList();
    }

    /**
     * Cashier confirms physical receipt of COD cash. This is also the payment-verification step for COD orders:
     * the linked Payment moves PENDING -> PAID here, stamped with who verified it and when. Confirming twice is
     * a no-op (payment and verification fields are left untouched on a repeat call).
     */
    @Transactional
    public CodSettlement confirmCashReceipt(Long settlementId) {
        User cashier = getAuthenticatedUser();
        CodSettlement settlement = codSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("CodSettlement", "id", settlementId));

        if (settlement.isConfirmedByCashier()) {
            return settlement; // Idempotent
        }

        settlement.setConfirmedByCashier(true);
        settlement.setCashier(cashier);
        settlement.setSettledAt(OffsetDateTime.now());

        Payment payment = paymentRepository.findByOrderId(settlement.getDelivery().getOrder().getId())
                .orElseThrow(() -> new BusinessException("NO_PAYMENT", "Order has no payment record."));
        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setVerifiedBy(cashier);
            payment.setVerifiedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
        }

        CodSettlement saved = codSettlementRepository.save(settlement);
        if (payment.getOrder().isFinalized()) {
            billingService.createBillForOrder(payment.getOrder().getId());
        }
        return saved;
    }

    /** Delivery staff view of their own collections: today's total and cash not yet handed to a cashier. */
    @Transactional(readOnly = true)
    public CodSummaryDto getMySummary() {
        User staff = getAuthenticatedUser();
        LocalDate today = LocalDate.now();
        List<CodSettlement> mine = codSettlementRepository.findByDeliveryStaffId(staff.getId());

        List<CodSettlement> todays = mine.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().equals(today))
                .toList();
        List<CodSettlement> unsettled = mine.stream().filter(s -> !s.isConfirmedByCashier()).toList();

        return new CodSummaryDto(
                today,
                todays.stream().map(CodSettlement::getCollectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                todays.size(),
                unsettled.stream().map(CodSettlement::getCollectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                unsettled.size()
        );
    }
}
