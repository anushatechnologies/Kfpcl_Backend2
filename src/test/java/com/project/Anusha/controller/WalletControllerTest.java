package com.project.Anusha.controller;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.UserMain;
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

import java.math.BigDecimal;
import java.util.Collections;
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

    @InjectMocks
    private WalletController walletController;

    private Customer customer;
    private UserMain userMain;

    @BeforeEach
    void setUp() {
        userMain = new UserMain();
        userMain.setId(101L);
        userMain.setPhoneNumber("9876543210");
        userMain.setWalletBalance(new BigDecimal("50.00"));

        customer = new Customer();
        customer.setId(201L);
        customer.setUserMain(userMain);
    }

    @Test
    void testAddMoney_Success() {
        when(customerService.getCustomerById(201L)).thenReturn(customer);

        Map<String, Object> request = Map.of(
                "userMainId", 201L,
                "amount", "100.00",
                "description", "Top up"
        );

        ResponseEntity<?> response = walletController.addMoney(201L, null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(walletService, times(1)).addMoney(eq(101L), eq(new BigDecimal("100.00")), eq("Top up"));
    }

    @Test
    void testDebitMoney_Success() {
        when(customerService.getCustomerById(201L)).thenReturn(customer);

        Map<String, Object> request = Map.of(
                "userMainId", 201L,
                "amount", "50.00",
                "description", "Spend"
        );

        ResponseEntity<?> response = walletController.debitMoney(201L, null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(walletService, times(1)).deductMoney(eq(101L), eq(new BigDecimal("50.00")), eq("Spend"));
    }

    @Test
    void testGetBalance_Success() {
        when(customerService.getCustomerById(101L)).thenReturn(customer);
        when(walletService.getBalance(101L)).thenReturn(new BigDecimal("150.00"));

        ResponseEntity<?> response = walletController.getBalance(null, null, 101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
