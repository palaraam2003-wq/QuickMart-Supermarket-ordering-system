package com.rapidmart.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CodSummaryDto(
        LocalDate date,
        BigDecimal collectedToday,
        int collectionsToday,
        BigDecimal unsettledTotal,
        int unsettledCount
) {
}
