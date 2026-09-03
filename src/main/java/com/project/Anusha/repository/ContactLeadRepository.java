package com.project.Anusha.repository;

import com.project.Anusha.model.ContactLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactLeadRepository extends JpaRepository<ContactLead, Long> {
    List<ContactLead> findByBuyerId(Long buyerId);
}
