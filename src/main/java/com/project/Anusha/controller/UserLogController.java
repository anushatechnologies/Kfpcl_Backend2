package com.project.Anusha.controller;

import com.project.Anusha.model.UserLog;
import com.project.Anusha.service.UserLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-logs")
@CrossOrigin(origins = "*")
public class UserLogController {

    private final UserLogService userLogService;

    public UserLogController(UserLogService userLogService) {
        this.userLogService = userLogService;
    }

    /**
     * Get all user logs
     * GET /api/user-logs
     */
    @GetMapping
    public ResponseEntity<List<UserLog>> getAllLogs() {
        return ResponseEntity.ok(userLogService.getAllLogs());
    }

    /**
     * Get logs for a specific user
     * GET /api/user-logs/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserLog>> getLogsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(userLogService.getLogsByUserId(userId));
    }

    /**
     * Get logs by role
     * GET /api/user-logs/role/{role}
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserLog>> getLogsByRole(@PathVariable String role) {
        return ResponseEntity.ok(userLogService.getLogsByRole(role));
    }

    /**
     * Get logs by action
     * GET /api/user-logs/action/{action}
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<List<UserLog>> getLogsByAction(@PathVariable String action) {
        return ResponseEntity.ok(userLogService.getLogsByAction(action));
    }

    /**
     * Create a manual log entry
     * POST /api/user-logs
     */
    @PostMapping
    public ResponseEntity<UserLog> createLog(@RequestBody Map<String, Object> req) {
        Long userId = req.containsKey("userId") ? ((Number) req.get("userId")).longValue() : null;
        String userRole = (String) req.get("userRole");
        String action = (String) req.get("action");
        String details = (String) req.get("details");
        String ipAddress = (String) req.get("ipAddress");

        UserLog userLog = userLogService.log(userId, userRole, action, details, ipAddress);
        return ResponseEntity.ok(userLog);
    }
}
