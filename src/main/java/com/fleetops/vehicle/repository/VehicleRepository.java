package com.fleetops.vehicle.repository;

import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.entity.Vehicle.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByType(String type);

    List<Vehicle> findByStatus(VehicleStatus status);

    List<Vehicle> findByAssignedDriverId(String driverId);

    // Vehicles whose insurance expires within N days (sorted soonest first)
    @Query("SELECT v FROM Vehicle v WHERE v.insuranceExpiry IS NOT NULL " +
           "AND v.insuranceExpiry <= :cutoffDate AND v.status != 'RETIRED' " +
           "ORDER BY v.insuranceExpiry ASC")
    List<Vehicle> findVehiclesWithExpiringInsurance(@Param("cutoffDate") LocalDate cutoffDate);

    // Vehicles that are service-due by date
    @Query("SELECT v FROM Vehicle v WHERE v.nextServiceDate IS NOT NULL " +
           "AND v.nextServiceDate <= :today AND v.status = 'ACTIVE'")
    List<Vehicle> findVehiclesDueForServiceByDate(@Param("today") LocalDate today);

    // Vehicles that are service-due by mileage
    @Query("SELECT v FROM Vehicle v WHERE v.nextServiceMileage IS NOT NULL " +
           "AND v.currentMileage >= v.nextServiceMileage AND v.status = 'ACTIVE'")
    List<Vehicle> findVehiclesDueForServiceByMileage();

    // Update status in-place (used by Request Service callback)
    @Modifying
    @Transactional
    @Query("UPDATE Vehicle v SET v.status = :status, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") VehicleStatus status);

    // Update mileage in-place
    @Modifying
    @Transactional
    @Query("UPDATE Vehicle v SET v.currentMileage = :mileage, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateMileage(@Param("id") Long id, @Param("mileage") Integer mileage);
}

