package com.project.Anusha.dto;

import lombok.Data;

/**
 * Sent by the frontend after payment completion/redirect from Cashfree.
 * The backend verifies the transaction status from Cashfree servers before marking the order PAID.
 */
@Data
public class CashfreeVerifyRequest {
    private Long orderId;        // our internal DB Order ID
    private String cfOrderId;    // Cashfree order ID
    private String receipt;      // our internal txnid/receipt ID
}
