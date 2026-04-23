package com.fleetops.vehicle.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vehicle entity â€” the core domain object of FleetOps.
 *
 * Status lifecycle:
 *   ACTIVE -> IN_SERVICE (when a request goes IN_PROGRESS)
 *   IN_SERVICE -> ACTIVE (when a request is COMPLETED)
 *   ACTIVE -> BREAKDOWN (emergency request raised)
 *   BREAKDOWN -> IN_SERVICE (when technician is assigned)
 *   * -> RETIRED (admin decommissions vehicle)
 *
 * Service due alerts:
 *   - Date-based: currentDate >= nextServiceDate
 *   - Mileage-based: currentMileage >= nextServiceMileage
 *
 * Insurance expiry alert:
 *   - insuranceExpiry within 30 days of today
 */
@Entity
@Table(name = "vehicles")
public class Vehicle implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum VehicleStatus {
        ACTIVE, IN_SERVICE, BREAKDOWN, RETIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_number", nullable = false, unique = true, length = 20)
    private String vehicleNumber;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "current_mileage", nullable = false)
    private Integer currentMileage = 0;

    @Column(name = "last_service_date")
    private LocalDate lastServiceDate;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "next_service_mileage")
    private Integer nextServiceMileage;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "assigned_driver_id", length = 100)
    private String assignedDriverId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Transient computed alert flags (not persisted)
    @Transient
    public boolean isServiceDueSoon() {
        boolean dateDue = nextServiceDate != null && !LocalDate.now().isBefore(nextServiceDate);
        boolean mileageDue = nextServiceMileage != null && currentMileage >= nextServiceMileage;
        return dateDue || mileageDue;
    }

    @Transient
    public boolean isInsuranceExpiringSoon() {
        if (insuranceExpiry == null) return false;
        return !LocalDate.now().plusDays(30).isBefore(insuranceExpiry);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }

    public Integer getCurrentMileage() { return currentMileage; }
    public void setCurrentMileage(Integer currentMileage) { this.currentMileage = currentMileage; }

    public LocalDate getLastServiceDate() { return lastServiceDate; }
    public void setLastServiceDate(LocalDate lastServiceDate) { this.lastServiceDate = lastServiceDate; }

    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }

    public Integer getNextServiceMileage() { return nextServiceMileage; }
    public void setNextServiceMileage(Integer nextServiceMileage) { this.nextServiceMileage = nextServiceMileage; }

    public LocalDate getInsuranceExpiry() { return insuranceExpiry; }
    public void setInsuranceExpiry(LocalDate insuranceExpiry) { this.insuranceExpiry = insuranceExpiry; }

    public String getAssignedDriverId() { return assignedDriverId; }
    public void setAssignedDriverId(String assignedDriverId) { this.assignedDriverId = assignedDriverId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

