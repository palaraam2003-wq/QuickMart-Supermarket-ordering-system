package com.rapidmart.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateInventoryLimitsRequest {

    @NotNull(message = "Minimum level is required")
    @Min(value = 0, message = "Minimum level cannot be negative")
    private Integer minimumLevel;

    @NotNull(message = "Maximum level is required")
    @Min(value = 1, message = "Maximum level must be at least 1")
    private Integer maximumLevel;

    public UpdateInventoryLimitsRequest() {
    }

    public Integer getMinimumLevel() {
        return minimumLevel;
    }

    public void setMinimumLevel(Integer minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public Integer getMaximumLevel() {
        return maximumLevel;
    }

    public void setMaximumLevel(Integer maximumLevel) {
        this.maximumLevel = maximumLevel;
    }
}
