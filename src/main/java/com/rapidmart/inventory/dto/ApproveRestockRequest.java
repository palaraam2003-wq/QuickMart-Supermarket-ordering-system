package com.rapidmart.inventory.dto;

import jakarta.validation.constraints.Min;

/** Optional body for approval. When approvedQuantity is omitted the full requested quantity is approved. */
public class ApproveRestockRequest {

    @Min(value = 1, message = "Approved quantity must be at least 1")
    private Integer approvedQuantity;

    public ApproveRestockRequest() {
    }

    public Integer getApprovedQuantity() {
        return approvedQuantity;
    }

    public void setApprovedQuantity(Integer approvedQuantity) {
        this.approvedQuantity = approvedQuantity;
    }
}
