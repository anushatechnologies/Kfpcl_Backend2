package com.project.Anusha.controller;

import com.project.Anusha.config.CashfreeConfig;
import com.project.Anusha.dto.CashfreeOrderResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.service.CashfreeService;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private CustomerService customerService;

    @Mock
    private CashfreeService cashfreeService;

    @Mock
    private CashfreeConfig cashfreeConfig;

    @InjectMocks
    private WalletController walletController;

    private UserDetails userDetails;
    private Customer customer;
    private UserMain userMain;

    @BeforeEach
    void setUp() {
        userDetails = new User("9876543210", "password", Collections.emptyList());

        userMain = new UserMain();
        userMain.setId(101L);
        userMain.setPhoneNumber("9876543210");
        userMain.setWalletBalance(new BigDecimal("50.00"));

        customer = new Customer();
        customer.setId(201L);
        customer.setUserMain(userMain);

        when(customerService.getCustomerByPhone("9876543210")).thenReturn(customer);
    }

    @Test
    void testInitiateWalletTopUp_Success() {
        when(cashfreeConfig.getApiUrl()).thenReturn("https://sandbox.cashfree.com/pg");

        Map<String, Object> cfOrderResponse = new HashMap<>();
        cfOrderResponse.put("order_id", "WALLET_101_1724749350");
        cfOrderResponse.put("payment_session_id", "session_test_12345");
        cfOrderResponse.put("order_currency", "INR");

        when(cashfreeService.createWalletTopupOrder(eq(customer), eq(101L), eq(new BigDecimal("100")), anyString()))
                .thenReturn(cfOrderResponse);

        Map<String, Object> request = Map.of("amount", 100);
        ResponseEntity<?> response = walletController.initiateWalletTopUp(userDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof CashfreeOrderResponse);

        CashfreeOrderResponse orderResponse = (CashfreeOrderResponse) response.getBody();
        assertEquals("WALLET_101_1724749350", orderResponse.getCfOrderId());
        assertEquals("session_test_12345", orderResponse.getPaymentSessionId());
        assertEquals(10000L, orderResponse.getAmountInPaise());
        assertEquals("sandbox", orderResponse.getEnvironment());
    }

    @Test
    void testVerifyWalletTopUp_Success() {
        String cfOrderId = "WALLET_101_1724749350";
        when(walletService.isTopupAlreadyCredited(101L, cfOrderId)).thenReturn(false);
        when(cashfreeService.verifyPayment(cfOrderId)).thenReturn(true);
        when(walletService.getBalance(101L)).thenReturn(new BigDecimal("150.00"));

        Map<String, Object> request = Map.of(
                "cfOrderId", cfOrderId,
                "amount", 100
        );

        ResponseEntity<?> response = walletController.verifyWalletTopUp(userDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals(new BigDecimal("150.00"), body.get("balance"));

        verify(walletService, times(1)).addMoney(eq(101L), eq(new BigDecimal("100")), contains(cfOrderId));
    }

    @Test
    void testVerifyWalletTopUp_AlreadyCredited_Idempotent() {
        String cfOrderId = "WALLET_101_1724749350";
        when(walletService.isTopupAlreadyCredited(101L, cfOrderId)).thenReturn(true);
        when(walletService.getBalance(101L)).thenReturn(new BigDecimal("150.00"));

        Map<String, Object> request = Map.of(
                "cfOrderId", cfOrderId,
                "amount", 100
        );

        ResponseEntity<?> response = walletController.verifyWalletTopUp(userDetails, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(true, body.get("success"));
        assertTrue(body.get("message").toString().contains("already verified"));

        // addMoney should NOT be called again
        verify(walletService, never()).addMoney(any(), any(), any());
        verify(cashfreeService, never()).verifyPayment(any());
    }

    @Test
    void testVerifyWalletTopUp_PaymentFailed() {
        String cfOrderId = "WALLET_101_1724749350";
        when(walletService.isTopupAlreadyCredited(101L, cfOrderId)).thenReturn(false);
        when(cashfreeService.verifyPayment(cfOrderId)).thenReturn(false);

        Map<String, Object> request = Map.of(
                "cfOrderId", cfOrderId,
                "amount", 100
        );

        ResponseEntity<?> response = walletController.verifyWalletTopUp(userDetails, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(false, body.get("success"));

        verify(walletService, never()).addMoney(any(), any(), any());
    }
}
