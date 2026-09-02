package com.project.Anusha.service;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.UserMain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps the complete delivery-person signup flow in ONE transaction.
 *
 * Problem solved:
 *   Without this, DeliveryAuthController called UserMainService (saves users_main)
 *   and then DeliveryPersonService (saves delivery_persons) in separate transactions.
 *   If the DeliveryPerson insert failed, users_main was already committed → orphan row.
 *
 * Solution:
 *   This service method is @Transactional — if createDeliveryPerson() throws,
 *   BOTH the users_main role update AND the delivery_persons insert are rolled back.
 */
@Service
public class DeliverySignupService {

    private final UserMainService userMainService;
    private final DeliveryPersonService deliveryPersonService;

    public DeliverySignupService(UserMainService userMainService,
                                 DeliveryPersonService deliveryPersonService) {
        this.userMainService = userMainService;
        this.deliveryPersonService = deliveryPersonService;
    }

    /**
     * Atomic signup:
     * Step 1 - Add DELIVERY_PERSON role to users_main
     * Step 2 - Create delivery_persons row
     *
     * If Step 2 fails → Step 1 is rolled back automatically.
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPerson signupDeliveryPerson(UserMain userMain,
                                               String firstName,
                                               String lastName,
                                               String vehicleType,
                                               String vehicleModel,
                                               String registrationNumber,
                                               String profilePhotoUrl) {
        // Step 1: Add role to users_main
        userMainService.addRole(userMain, "DELIVERY_PERSON");
 
        // Step 2: Create delivery_persons row — if this throws, Step 1 rolls back too
        return deliveryPersonService.createDeliveryPerson(userMain, firstName, lastName,
                vehicleType, vehicleModel, registrationNumber, profilePhotoUrl);
    }
}
