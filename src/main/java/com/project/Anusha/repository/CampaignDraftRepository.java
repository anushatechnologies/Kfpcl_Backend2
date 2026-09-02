package com.project.Anusha.repository;

import com.project.Anusha.model.CampaignDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignDraftRepository extends JpaRepository<CampaignDraft, Long> {
    List<CampaignDraft> findAllByOrderByUpdatedAtDesc();
}
