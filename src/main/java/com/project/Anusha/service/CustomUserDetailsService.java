package com.project.Anusha.service;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.User;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.CustomerRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import com.project.Anusha.repository.UserMainRepository;
import com.project.Anusha.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spring Security UserDetailsService that resolves a username (= phone number or email)
 * into a UserDetails object for JWT authentication.
 *
 * Order of lookup:
 *  1. Admin users  (by e-mail, in the legacy 'users' table)
 *  2. users_main   (by phone number → then determine granted authorities from roles)
 *
 * This collapses Customer + DeliveryPerson into a single lookup via users_main,
 * so a person who has BOTH roles gets both authorities in their security context.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMainRepository userMainRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DeliveryPersonRepository deliveryPersonRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. Admin user – identified by e-mail address
        Optional<User> adminUser = userRepository.findByEmail(username);
        if (adminUser.isPresent()) {
            User user = adminUser.get();
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())
                    .roles(user.getRole().replace("ROLE_", ""))
                    .disabled(!user.isEnabled())
                    .build();
        }

        // 2. Regular user – identified by phone number via users_main
        Optional<UserMain> userMainOpt = userMainRepository.findByPhoneNumber(username);
        if (userMainOpt.isPresent()) {
            UserMain userMain = userMainOpt.get();
            List<SimpleGrantedAuthority> authorities = buildAuthorities(userMain);

            if (authorities.isEmpty()) {
                // UserMain exists but no role assigned yet (edge case during signup)
                throw new UsernameNotFoundException("User has no roles: " + username);
            }

            // Determine disabled flag: for delivery person require admin approval
            boolean disabled = isAccountDisabled(userMain);

            return org.springframework.security.core.userdetails.User.builder()
                    .username(userMain.getPhoneNumber())
                    .password("{noop}")              // no password – Firebase JWT handles auth
                    .authorities(authorities)
                    .disabled(disabled)
                    .build();
        }

        throw new UsernameNotFoundException("User not found: " + username);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Build a list of GrantedAuthority objects from the comma-separated roles field.
     * e.g. roles = "CUSTOMER,DELIVERY_PERSON" → [ROLE_CUSTOMER, ROLE_DELIVERY_PERSON]
     */
    private List<SimpleGrantedAuthority> buildAuthorities(UserMain userMain) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String roles = userMain.getRoles();
        if (roles == null || roles.isBlank()) return authorities;

        for (String role : roles.split(",")) {
            String trimmed = role.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
            }
        }
        return authorities;
    }

    /**
     * Account is "disabled" only if the person is a DELIVERY_PERSON and has NOT
     * been approved by admin yet. Customers are always enabled once registered.
     */
    private boolean isAccountDisabled(UserMain userMain) {
        if (userMain.hasRole("DELIVERY_PERSON")) {
            Optional<DeliveryPerson> dpOpt =
                    deliveryPersonRepository.findByUserMainId(userMain.getId());
            if (dpOpt.isPresent()) {
                DeliveryPerson dp = dpOpt.get();
                // Allow login (not disabled) only when admin approved
                return !dp.isApprovedByAdmin();
            }
        }
        // Customer accounts are always active as long as isActive=true on Customer entity
        if (userMain.hasRole("CUSTOMER")) {
            Optional<Customer> custOpt =
                    customerRepository.findByUserMainId(userMain.getId());
            if (custOpt.isPresent()) {
                Customer cust = custOpt.get();
                return !Boolean.TRUE.equals(cust.getIsActive());
            }
        }
        return false;
    }
}