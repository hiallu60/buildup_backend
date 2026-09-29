package com.buildup.material.dto;

import com.buildup.material.entity.MaterialRequestItem;

import java.math.BigDecimal;

public record MaterialRequestItemResponse(
        String name,
        BigDecimal quantity,
        String unit
) {
    public static MaterialRequestItemResponse from(MaterialRequestItem item) {
        return new MaterialRequestItemResponse(item.getName(), item.getQuantity(), item.getUnit());
    }
}
