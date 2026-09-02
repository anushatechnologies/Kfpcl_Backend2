package com.project.Anusha.config;

import com.project.Anusha.model.User;
import com.project.Anusha.repository.UserRepository;
import com.project.Anusha.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SuperAdminSeeder implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${bootstrap.superadmin.email:}")
    private String email;

    @Value("${bootstrap.superadmin.password:}")
    private String password;

    @Value("${bootstrap.superadmin.name:Super Admin}")
    private String name;

    @Override
    public void run(ApplicationArguments args) {
        List<User> existingSuperAdmins = userRepository.findByRoleIn(List.of("ROLE_SUPER_ADMIN"));
        if (!existingSuperAdmins.isEmpty()) {
            User syncedSuperAdmin = null;

            if (email != null && !email.isBlank()) {
                syncedSuperAdmin = existingSuperAdmins.stream()
                        .filter(user -> email.equalsIgnoreCase(user.getEmail()))
                        .findFirst()
                        .orElse(null);
            }

            if (syncedSuperAdmin != null && password != null && !password.isBlank()) {
                emailService.ensureFirebaseUserExists(
                        syncedSuperAdmin.getEmail(),
                        syncedSuperAdmin.getName(),
                        password
                );
                System.out.println("Super admin already exists. Firebase auth synced for: " + syncedSuperAdmin.getEmail());
            } else {
                System.out.println("Super admin already exists. Skipping seed.");
            }
            return;
        }

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            System.out.println("BOOTSTRAP_SUPERADMIN_EMAIL / PASSWORD not set. Super admin seed skipped.");
            return;
        }

        if (userRepository.findByEmail(email).isPresent()) {
            User existing = userRepository.findByEmail(email).get();
            existing.setRole("ROLE_SUPER_ADMIN");
            existing.setMustChangePassword(false);
            userRepository.save(existing);
            emailService.ensureFirebaseUserExists(existing.getEmail(), existing.getName(), password);
            System.out.println("Promoted existing user to ROLE_SUPER_ADMIN: " + email);
            return;
        }

        User superAdmin = new User();
        superAdmin.setEmail(email);
        superAdmin.setName(name);
        superAdmin.setPassword(passwordEncoder.encode(password));
        superAdmin.setRole("ROLE_SUPER_ADMIN");
        superAdmin.setEnabled(true);
        superAdmin.setMustChangePassword(false);
        superAdmin.setCreatedAt(LocalDateTime.now());
        userRepository.save(superAdmin);
        emailService.ensureFirebaseUserExists(email, name, password);

        System.out.println("Super admin created: " + email);
    }
}
