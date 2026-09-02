package com.project.Anusha.repository;

import com.project.Anusha.model.MarqueeAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarqueeAnnouncementRepository extends JpaRepository<MarqueeAnnouncement, Long> {

    List<MarqueeAnnouncement> findByActiveTrueOrderByDisplayOrderAsc();

    List<MarqueeAnnouncement> findAllByOrderByDisplayOrderAsc();
}
