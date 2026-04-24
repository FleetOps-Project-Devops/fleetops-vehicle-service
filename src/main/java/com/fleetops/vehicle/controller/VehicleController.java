package com.fleetops.vehicle.controller;

import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.entity.Vehicle.VehicleStatus;
import com.fleetops.vehicle.service.VehicleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VehicleController â€” REST API for FleetOps vehicle management.
 *
 * Authorization matrix:
 *   GET /vehicles           DRIVER (assigned only), MANAGER (all), ADMIN (all)
 *   GET /vehicles/{id}      DRIVER (own only), MANAGER, ADMIN
 *   POST /vehicles          ADMIN only
 *   PUT /vehicles/{id}      ADMIN only
 *   DELETE /vehicles/{id}   ADMIN only
 *   PATCH /vehicles/{id}/status   MANAGER, ADMIN
 *   PATCH /vehicles/{id}/mileage  DRIVER (own), ADMIN
 *   GET /vehicles/alerts/insurance  MANAGER, ADMIN
 *   GET /vehicles/alerts/service    MANAGER, ADMIN
 *   GET /vehicles/dashboard         MANAGER, ADMIN
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    // â”€â”€â”€ READ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<?> getVehicles(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driverId,
            Authentication authentication) {

        // DRIVERs can only see their assigned vehicles
        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
        if (isDriver) {
            return ResponseEntity.ok(vehicleService.getVehiclesByDriver(authentication.getName()));
        }

        if (type != null && !type.isEmpty()) {
            return ResponseEntity.ok(vehicleService.getVehiclesByType(type));
        }
        if (status != null && !status.isEmpty()) {
            try {
                return ResponseEntity.ok(vehicleService.getVehiclesByStatus(VehicleStatus.valueOf(status.toUpperCase())));
            } catch (IllegalArgumentException e) {
                return buildErrorResponse(400, "Bad Request", "Invalid status. Valid values: ACTIVE, IN_SERVICE, BREAKDOWN, RETIRED");
            }
        }
        if (driverId != null && !driverId.isEmpty()) {
            return ResponseEntity.ok(vehicleService.getVehiclesByDriver(driverId));
        }
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DRIVER','MANAGER','ADMIN')")
    public ResponseEntity<Vehicle> getVehicle(@PathVariable Long id, Authentication authentication) {
        return vehicleService.getVehicleById(id)
                .map(v -> {
                    // DRIVERs can only view their own assigned vehicle
                    boolean isDriver = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
                    if (isDriver && !authentication.getName().equals(v.getAssignedDriverId())) {
                        return ResponseEntity.status(403).<Vehicle>build();
                    }
                    return ResponseEntity.ok(v);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // â”€â”€â”€ WRITE â€” ADMIN ONLY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Vehicle> createVehicle(@RequestBody Vehicle vehicle) {
        return ResponseEntity.status(201).body(vehicleService.createVehicle(vehicle));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Long id, @RequestBody Vehicle details) {
        return vehicleService.updateVehicle(id, details)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteVehicle(@PathVariable Long id) {
        return vehicleService.deleteVehicle(id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    // â”€â”€â”€ STATUS UPDATE â€” MANAGER, ADMIN â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        if (!payload.containsKey("status")) {
            return ResponseEntity.badRequest().body("Missing 'status' in payload");
        }
        try {
            VehicleStatus newStatus = VehicleStatus.valueOf(payload.get("status").toUpperCase());
            VehicleService.StatusUpdateResult result = vehicleService.updateStatus(id, newStatus);
            return switch (result) {
                case SUCCESS -> ResponseEntity.ok(vehicleService.findById(id).orElse(null));
                case NOT_FOUND -> ResponseEntity.status(404).body("Vehicle not found");
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status. Valid values: ACTIVE, IN_SERVICE, BREAKDOWN, RETIRED");
        }
    }

    // â”€â”€â”€ MILEAGE UPDATE â€” DRIVER (own vehicle), ADMIN â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PatchMapping("/{id}/mileage")
    @PreAuthorize("hasAnyRole('DRIVER','ADMIN')")
    public ResponseEntity<?> updateMileage(@PathVariable Long id,
                                           @RequestBody Map<String, Integer> payload,
                                           Authentication authentication) {
        if (!payload.containsKey("mileage")) {
            return ResponseEntity.badRequest().body("Missing 'mileage' in payload");
        }
        if (payload.get("mileage") == null) {
            return buildErrorResponse(400, "Bad Request", "Mileage is required");
        }

        // DRIVER can only update mileage for their own assigned vehicle
        boolean isDriver = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"));
        if (isDriver) {
            return vehicleService.getVehicleById(id).map(v -> {
                if (!authentication.getName().equals(v.getAssignedDriverId())) {
                    return ResponseEntity.status(403).<Object>body("You can only update mileage for your assigned vehicle");
                }
                VehicleService.MileageUpdateResult result = vehicleService.updateMileage(id, payload.get("mileage"));
                return mileageResponse(result, id);
            }).orElse(ResponseEntity.notFound().build());
        }

        VehicleService.MileageUpdateResult result = vehicleService.updateMileage(id, payload.get("mileage"));
        return mileageResponse(result, id);
    }

    private ResponseEntity<?> mileageResponse(VehicleService.MileageUpdateResult result, Long id) {
        return switch (result) {
            case SUCCESS -> ResponseEntity.ok(vehicleService.findById(id).orElse(null));
            case NOT_FOUND -> ResponseEntity.status(404).body("Vehicle not found");
            case INVALID -> ResponseEntity.badRequest().body("Mileage must be a non-negative number");
        };
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    // â”€â”€â”€ ALERTS â€” MANAGER, ADMIN â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/alerts/insurance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<Vehicle>> getInsuranceAlerts() {
        return ResponseEntity.ok(vehicleService.getInsuranceExpiringAlerts());
    }

    @GetMapping("/alerts/service")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<Vehicle>> getServiceAlerts() {
        return ResponseEntity.ok(vehicleService.getServiceDueAlerts());
    }

    // â”€â”€â”€ DASHBOARD KPIs â€” MANAGER, ADMIN â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Long>> getDashboard() {
        return ResponseEntity.ok(vehicleService.getDashboardStats());
    }
}

