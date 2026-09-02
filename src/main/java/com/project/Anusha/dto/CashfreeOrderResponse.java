package com.project.Anusha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Returned to the frontend after creating a Cashfree order.
 * The frontend uses paymentSessionId and cfOrderId to open the Cashfree checkout SDK.
 */
@Data
@AllArgsConstructor
public class CashfreeOrderResponse {
    private String cfOrderId;          // Cashfree order ID
    private String paymentSessionId;   // Session ID needed by Cashfree JS SDK
    private long   amountInPaise;      // Display amount (Rupees * 100)
    private String currency;           // "INR"
    private String receipt;            // our internal txnid / receipt ID
    private String environment;        // "production" or "sandbox" (frontend needs this to init Cashfree SDK)
}
