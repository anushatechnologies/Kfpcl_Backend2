package com.project.Anusha.service;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.FareRule;
import com.project.Anusha.repository.FareRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Transactional
public class FareRuleService {

    @Autowired
    private FareRuleRepository fareRuleRepository;

    /**
     * Seed default fare rules on startup if the table is empty.
     * BIKE / SCOOTY / EV  → baseFare ₹25, baseKm 2, perKmRate ₹7
     * AUTO                 → baseFare ₹30, baseKm 2, perKmRate ₹10, loading ₹20, unloading ₹20
     * HEAVY                → baseFare ₹50, baseKm 2, perKmRate ₹15, loading ₹30, unloading ₹30
     */
    @PostConstruct
    public void seedDefaultFareRulesIfAbsent() {
        for (DeliveryPerson.VehicleType vt : DeliveryPerson.VehicleType.values()) {
            if (!fareRuleRepository.existsByVehicleType(vt)) {
                FareRule rule = new FareRule();
                rule.setVehicleType(vt);
                switch (vt) {
                    case BIKE:
                    case SCOOTY:
                    case EV:
                        rule.setBaseFare(new BigDecimal("25.00"));
                        rule.setBaseKm(new BigDecimal("2.0"));
                        rule.setPerKmRate(new BigDecimal("7.00"));
                        rule.setRainSurcharge(new BigDecimal("30.00"));
                        rule.setLoadingCharge(BigDecimal.ZERO);
                        rule.setUnloadingCharge(BigDecimal.ZERO);
                        break;
                    case AUTO:
                        rule.setBaseFare(new BigDecimal("30.00"));
                        rule.setBaseKm(new BigDecimal("2.0"));
                        rule.setPerKmRate(new BigDecimal("10.00"));
                        rule.setRainSurcharge(new BigDecimal("40.00"));
                        rule.setLoadingCharge(new BigDecimal("20.00"));
                        rule.setUnloadingCharge(new BigDecimal("20.00"));
                        break;
                    case HEAVY:
                        rule.setBaseFare(new BigDecimal("50.00"));
                        rule.setBaseKm(new BigDecimal("2.0"));
                        rule.setPerKmRate(new BigDecimal("15.00"));
                        rule.setRainSurcharge(new BigDecimal("50.00"));
                        rule.setLoadingCharge(new BigDecimal("30.00"));
                        rule.setUnloadingCharge(new BigDecimal("30.00"));
                        break;
                }
                rule.setRainActive(false);
                rule.setActive(true);
                fareRuleRepository.save(rule);
            }
        }
    }

    public List<FareRule> getAllFareRules() {
        return fareRuleRepository.findAllByOrderByVehicleTypeAsc();
    }

    public FareRule getFareRuleByVehicleType(DeliveryPerson.VehicleType vehicleType) {
        return fareRuleRepository.findByVehicleType(vehicleType)
                .orElseThrow(() -> new IllegalArgumentException("No fare rule found for: " + vehicleType));
    }

    /**
     * Update fare rule fields (admin-only). Admin cannot change vehicleType.
     */
    public FareRule updateFareRule(Long id, FareRule updatedRule) {
        FareRule existing = fareRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fare rule not found with ID: " + id));

        existing.setBaseFare(updatedRule.getBaseFare());
        existing.setBaseKm(updatedRule.getBaseKm());
        existing.setPerKmRate(updatedRule.getPerKmRate());
        existing.setRainSurcharge(updatedRule.getRainSurcharge());
        existing.setLoadingCharge(updatedRule.getLoadingCharge());
        existing.setUnloadingCharge(updatedRule.getUnloadingCharge());
        existing.setActive(updatedRule.isActive());
        // Note: rainActive is NOT updated here — use toggleRain instead

        return fareRuleRepository.save(existing);
    }

    /**
     * Toggle rain surcharge ON or OFF for a single vehicle type.
     * When toggling light vehicles (BIKE/SCOOTY/EV), all three are toggled together.
     */
    public Map<String, Object> toggleRain(DeliveryPerson.VehicleType vehicleType, boolean rainActive) {
        int updated;
        if (vehicleType == DeliveryPerson.VehicleType.BIKE
                || vehicleType == DeliveryPerson.VehicleType.SCOOTY
                || vehicleType == DeliveryPerson.VehicleType.EV) {
            // Light vehicle group — toggle all three together
            updated = fareRuleRepository.setRainActiveForLightVehicles(rainActive);
        } else {
            updated = fareRuleRepository.setRainActive(vehicleType, rainActive);
        }
        return Map.of(
                "success", true,
                "vehicleType", vehicleType.name(),
                "rainActive", rainActive,
                "rowsUpdated", updated
        );
    }

    /**
     * Core fare calculation engine.
     *
     * Formula:
     *   fare = baseFare                                     (if distanceKm <= baseKm)
     *   fare = baseFare + floor(distanceKm - baseKm) * perKmRate  (if distanceKm > baseKm)
     *   fare += rainSurcharge   (if rainActive)
     *   fare += loadingCharge + unloadingCharge  (always included for AUTO/HEAVY)
     *
     * Example — BIKE, 9.7 km, rain ₹30:
     *   25 + floor(9.7 - 2) * 7 + 30 = 25 + 49 + 30 = ₹104
     */
    public BigDecimal calculateDeliveryCharge(DeliveryPerson.VehicleType vehicleType, double distanceKm) {
        return fareRuleRepository.findByVehicleTypeAndActiveTrue(vehicleType)
                .map(rule -> {
                    BigDecimal fare = rule.getBaseFare();

                    double extra = distanceKm - rule.getBaseKm().doubleValue();
                    if (extra > 0) {
                        long extraFullKm = (long) Math.floor(extra);
                        fare = fare.add(rule.getPerKmRate().multiply(BigDecimal.valueOf(extraFullKm)));
                    }

                    if (rule.isRainActive()) {
                        fare = fare.add(rule.getRainSurcharge());
                    }

                    fare = fare.add(rule.getLoadingCharge()).add(rule.getUnloadingCharge());

                    return fare.setScale(2, RoundingMode.HALF_UP);
                })
                .orElse(new BigDecimal("25.00"));
    }

    /**
     * Returns a detailed fare breakdown for display in partner app or admin UI.
     */
    public Map<String, Object> calculateFareBreakdown(DeliveryPerson.VehicleType vehicleType, double distanceKm) {
        FareRule rule = fareRuleRepository.findByVehicleTypeAndActiveTrue(vehicleType)
                .orElseThrow(() -> new IllegalArgumentException("No active fare rule for: " + vehicleType));

        BigDecimal baseFare = rule.getBaseFare();
        BigDecimal extraCharge = BigDecimal.ZERO;
        long extraFullKm = 0;

        double extra = distanceKm - rule.getBaseKm().doubleValue();
        if (extra > 0) {
            extraFullKm = (long) Math.floor(extra);
            extraCharge = rule.getPerKmRate().multiply(BigDecimal.valueOf(extraFullKm));
        }

        BigDecimal rainCharge = rule.isRainActive() ? rule.getRainSurcharge() : BigDecimal.ZERO;
        BigDecimal loadingTotal = rule.getLoadingCharge().add(rule.getUnloadingCharge());
        BigDecimal total = baseFare.add(extraCharge).add(rainCharge).add(loadingTotal);

        Map<String, Object> result = new HashMap<>();
        result.put("vehicleType", vehicleType.name());
        result.put("distanceKm", distanceKm);
        result.put("baseFare", baseFare);
        result.put("baseKm", rule.getBaseKm());
        result.put("extraKm", extraFullKm);
        result.put("perKmRate", rule.getPerKmRate());
        result.put("extraCharge", extraCharge);
        result.put("rainActive", rule.isRainActive());
        result.put("rainSurcharge", rainCharge);
        result.put("loadingCharge", rule.getLoadingCharge());
        result.put("unloadingCharge", rule.getUnloadingCharge());
        result.put("totalFare", total.setScale(2, RoundingMode.HALF_UP));
        return result;
    }
}
