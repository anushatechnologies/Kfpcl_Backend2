package com.project.Anusha.service;

import com.project.Anusha.model.UserLog;
import com.project.Anusha.repository.UserLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserLogService {

    private final UserLogRepository userLogRepository;

    public UserLogService(UserLogRepository userLogRepository) {
        this.userLogRepository = userLogRepository;
    }

    public UserLog log(Long userId, String userRole, String action, String details, String ipAddress) {
        UserLog userLog = new UserLog();
        userLog.setUserId(userId);
        userLog.setUserRole(userRole);
        userLog.setAction(action);
        userLog.setDetails(details);
        userLog.setIpAddress(ipAddress);
        userLog.setTimestamp(LocalDateTime.now());
        return userLogRepository.save(userLog);
    }

    public List<UserLog> getAllLogs() {
        return userLogRepository.findAll();
    }

    public List<UserLog> getLogsByUserId(Long userId) {
        return userLogRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    public List<UserLog> getLogsByRole(String role) {
        return userLogRepository.findByUserRoleOrderByTimestampDesc(role);
    }

    public List<UserLog> getLogsByAction(String action) {
        return userLogRepository.findByActionOrderByTimestampDesc(action);
    }
}
