package com.project.Anusha.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppServiceTest {

    @Test
    void globalHiringTemplateShouldNotSendUrlButtonParameters() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        WhatsAppService service = new WhatsAppService(restTemplate, objectMapper);

        ReflectionTestUtils.setField(service, "apiToken", "test-token");
        ReflectionTestUtils.setField(service, "phoneNumberId", "test-phone-number-id");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"messages\":[{\"id\":\"msg_1\"}]}", HttpStatus.OK));

        service.sendTemplateMessageWithHeaderMediaIdAndUrlButton(
                "919948598350",
                "anusha_nexus_global_hiring",
                Collections.emptyList(),
                null,
                "video",
                "https://example.com/app"
        );

        ArgumentCaptor<HttpEntity> payloadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue().getBody();
        Map<String, Object> template = (Map<String, Object>) payload.get("template");
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");

        assertTrue(components.isEmpty(), "Global hiring payload should not include button parameters");
    }
}
