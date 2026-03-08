package com.thriftbazaar.backend.dto;

// This file is intentionally left as a redirect to the canonical definition.
// The OrderItemResponseDto used throughout the codebase is the inner static
// class inside OrderResponseDto.  This standalone file is kept only for
// backwards-compatibility with any import that may exist; it delegates
// to the inner class by simply re-exporting the same fields.
//
// If you need to import an OrderItemResponseDto, use:
//   import com.thriftbazaar.backend.dto.OrderResponseDto.OrderItemResponseDto;

@Deprecated
public class OrderItemResponseDto {
    // Intentionally empty — use OrderResponseDto.OrderItemResponseDto instead.
    // Keeping this file prevents a compile error if any old import still exists.
    private OrderItemResponseDto() {}
}
