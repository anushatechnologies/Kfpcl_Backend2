package com.project.Anusha.repository;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.FareRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FareRuleRepository extends JpaRepository<FareRule, Long> {

    Optional<FareRule> findByVehicleType(DeliveryPerson.VehicleType vehicleType);

    Optional<FareRule> findByVehicleTypeAndActiveTrue(DeliveryPerson.VehicleType vehicleType);

    List<FareRule> findAllByOrderByVehicleTypeAsc();

    /** Toggle rain mode for a single vehicle type. */
    @Modifying
    @Transactional
    @Query("UPDATE FareRule f SET f.rainActive = :rainActive WHERE f.vehicleType = :vehicleType")
    int setRainActive(@Param("vehicleType") DeliveryPerson.VehicleType vehicleType,
                      @Param("rainActive") boolean rainActive);

    /**
     * Set rain surcharge ON for all three light vehicles at once
     * (BIKE, SCOOTY, EV share the same rain fare group by convention).
     */
    @Modifying
    @Transactional
    @Query("UPDATE FareRule f SET f.rainActive = :rainActive " +
           "WHERE f.vehicleType IN (com.project.Anusha.model.DeliveryPerson.VehicleType.BIKE, " +
           "com.project.Anusha.model.DeliveryPerson.VehicleType.SCOOTY, " +
           "com.project.Anusha.model.DeliveryPerson.VehicleType.EV)")
    int setRainActiveForLightVehicles(@Param("rainActive") boolean rainActive);

    boolean existsByVehicleType(DeliveryPerson.VehicleType vehicleType);
}
