package com.project.Anusha.service;

import com.project.Anusha.model.UserLog;
import com.project.Anusha.repository.UserLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final UserLogRepository userLogRepository;

    public Page<UserLog> search(String role, String q, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return userLogRepository.search(role, q, from, to, pageable);
    }
}

