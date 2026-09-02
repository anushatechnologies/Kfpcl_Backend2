package com.project.Anusha.service;

import com.project.Anusha.dto.WhatsAppTemplateRequest;
import com.project.Anusha.dto.WhatsAppTemplateResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppTemplateServiceTest {

    @Test
    void createAndSubmitTemplateShouldMoveStatusToPendingReview() {
        WhatsAppTemplateService service = new WhatsAppTemplateService();

        WhatsAppTemplateRequest request = new WhatsAppTemplateRequest();
        request.setName("scan2paper_shop_invitation");
        request.setLanguage("English");
        request.setCategory("Marketing");
        request.setBodyText("Hello {{1}}\n\nWe are introducing Scan2Paper.");
        request.setHeaderType("none");

        WhatsAppTemplateResponse created = service.createTemplate(request);

        assertThat(created.getId()).isPositive();
        assertThat(created.getStatus()).isEqualTo("DRAFT");

        WhatsAppTemplateResponse submitted = service.submitForReview(created.getId());

        assertThat(submitted.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.getReviewNotes()).contains("submitted");
    }
}
