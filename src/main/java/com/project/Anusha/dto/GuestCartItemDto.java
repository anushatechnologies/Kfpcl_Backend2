package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestCartItemDto {
    private Long variantId;
    private Integer quantity;
}
