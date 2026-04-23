package com.fleetops.vehicle.service;

import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.entity.Vehicle.VehicleStatus;
import com.fleetops.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * VehicleService â€” Business logic for the FleetOps vehicle domain.
 *
 * Cache strategy (inherits from Redis CacheConfig):
 *   "vehicles"  â†’ lists (all, by-type, by-status, by-driver)
 *   "vehicle"   â†’ single vehicle by ID
 *
 * Alert logic (computed at query time, not cached â€” always fresh):
 *   - Insurance expiry within 30 days
 *   - Service due by date or mileage
 */
@Service
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);
    private static final int INSURANCE_ALERT_DAYS = 30;

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // â”€â”€â”€ READ OPERATIONS (cached) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Cacheable(value = "vehicles", key = "'all'")
    public List<Vehicle> getAllVehicles() {
        log.debug("Cache MISS vehicles:all â€” loading from DB");
        return vehicleRepository.findAll();
    }

    @Cacheable(value = "vehicles", key = "'type:' + #type")
    public List<Vehicle> getVehiclesByType(String type) {
        log.debug("Cache MISS vehicles:type:{} â€” loading from DB", type);
        return vehicleRepository.findByType(type);
    }

    @Cacheable(value = "vehicles", key = "'status:' + #status")
    public List<Vehicle> getVehiclesByStatus(VehicleStatus status) {
        log.debug("Cache MISS vehicles:status:{} â€” loading from DB", status);
        return vehicleRepository.findByStatus(status);
    }

    @Cacheable(value = "vehicles", key = "'driver:' + #driverId")
    public List<Vehicle> getVehiclesByDriver(String driverId) {
        log.debug("Cache MISS vehicles:driver:{} â€” loading from DB", driverId);
        return vehicleRepository.findByAssignedDriverId(driverId);
    }

    @Cacheable(value = "vehicle", key = "#id")
    public Optional<Vehicle> getVehicleById(Long id) {
        log.debug("Cache MISS vehicle:{} â€” loading from DB", id);
        return vehicleRepository.findById(id);
    }

    // â”€â”€â”€ ALERT QUERIES (never cached â€” must always be real-time) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public List<Vehicle> getInsuranceExpiringAlerts() {
        LocalDate cutoff = LocalDate.now().plusDays(INSURANCE_ALERT_DAYS);
        return vehicleRepository.findVehiclesWithExpiringInsurance(cutoff);
    }

    public List<Vehicle> getServiceDueAlerts() {
        List<Vehicle> byDate = vehicleRepository.findVehiclesDueForServiceByDate(LocalDate.now());
        List<Vehicle> byMileage = vehicleRepository.findVehiclesDueForServiceByMileage();
        // Merge and deduplicate by ID
        byDate.addAll(byMileage.stream()
                .filter(v -> byDate.stream().noneMatch(d -> d.getId().equals(v.getId())))
                .toList());
        return byDate;
    }

    /** Manager Dashboard KPI summary */
    public Map<String, Long> getDashboardStats() {
        long total = vehicleRepository.count();
        long active = vehicleRepository.findByStatus(VehicleStatus.ACTIVE).size();
        long inService = vehicleRepository.findByStatus(VehicleStatus.IN_SERVICE).size();
        long breakdown = vehicleRepository.findByStatus(VehicleStatus.BREAKDOWN).size();
        long insuranceExpiring = getInsuranceExpiringAlerts().size();
        long serviceDue = getServiceDueAlerts().size();

        return Map.of(
                "total", total,
                "active", active,
                "inService", inService,
                "breakdown", breakdown,
                "insuranceExpiring", insuranceExpiring,
                "serviceDue", serviceDue
        );
    }

    // â”€â”€â”€ WRITE OPERATIONS (evict cache) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @CacheEvict(value = "vehicles", allEntries = true)
    @Transactional
    public Vehicle createVehicle(Vehicle vehicle) {
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Cache EVICT vehicles:all â€” new vehicle created id={}", saved.getId());
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicle", key = "#id"),
            @CacheEvict(value = "vehicles", allEntries = true)
    })
    @Transactional
    public Optional<Vehicle> updateVehicle(Long id, Vehicle details) {
        return vehicleRepository.findById(id).map(v -> {
            v.setVehicleNumber(details.getVehicleNumber());
            v.setModel(details.getModel());
            v.setBrand(details.getBrand());
            v.setType(details.getType());
            v.setStatus(details.getStatus());
            v.setCurrentMileage(details.getCurrentMileage());
            v.setLastServiceDate(details.getLastServiceDate());
            v.setNextServiceDate(details.getNextServiceDate());
            v.setNextServiceMileage(details.getNextServiceMileage());
            v.setInsuranceExpiry(details.getInsuranceExpiry());
            v.setAssignedDriverId(details.getAssignedDriverId());
            Vehicle saved = vehicleRepository.save(v);
            log.info("Cache EVICT vehicle:{} + vehicles:all â€” updated", id);
            return saved;
        });
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicle", key = "#id"),
            @CacheEvict(value = "vehicles", allEntries = true)
    })
    @Transactional
    public StatusUpdateResult updateStatus(Long id, VehicleStatus newStatus) {
        return vehicleRepository.findById(id).map(v -> {
            v.setStatus(newStatus);
            vehicleRepository.save(v);
            log.info("Cache EVICT vehicle:{} + vehicles:all â€” status changed to {}", id, newStatus);
            return StatusUpdateResult.SUCCESS;
        }).orElse(StatusUpdateResult.NOT_FOUND);
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicle", key = "#id"),
            @CacheEvict(value = "vehicles", allEntries = true)
    })
    @Transactional
    public MileageUpdateResult updateMileage(Long id, Integer newMileage) {
        if (newMileage < 0) return MileageUpdateResult.INVALID;
        return vehicleRepository.findById(id).map(v -> {
            v.setCurrentMileage(newMileage);
            vehicleRepository.save(v);
            log.info("Cache EVICT vehicle:{} + vehicles:all â€” mileage updated to {}", id, newMileage);
            return MileageUpdateResult.SUCCESS;
        }).orElse(MileageUpdateResult.NOT_FOUND);
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicle", key = "#id"),
            @CacheEvict(value = "vehicles", allEntries = true)
    })
    @Transactional
    public boolean deleteVehicle(Long id) {
        return vehicleRepository.findById(id).map(v -> {
            vehicleRepository.delete(v);
            log.info("Cache EVICT vehicle:{} + vehicles:all â€” vehicle deleted", id);
            return true;
        }).orElse(false);
    }

    public Optional<Vehicle> findById(Long id) {
        return vehicleRepository.findById(id);
    }

    // â”€â”€â”€ Result enums â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public enum StatusUpdateResult { SUCCESS, NOT_FOUND }
    public enum MileageUpdateResult { SUCCESS, NOT_FOUND, INVALID }
}

