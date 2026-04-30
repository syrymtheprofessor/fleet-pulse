package com.pingine.fleetpulse.api;

import com.pingine.fleetpulse.api.dto.TripResponse;
import com.pingine.fleetpulse.api.dto.VehicleResponse;
import com.pingine.fleetpulse.service.TripNotFoundException;
import com.pingine.fleetpulse.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripController.class)
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TripService tripService;

    private static final String VEHICLE_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Test
    void returnsLastTripJson() throws Exception {
        var vehicle = VehicleResponse.builder()
                .id(VEHICLE_ID)
                .licensePlate("B-PG-1001")
                .model("Mercedes Actros")
                .vin("TESTVIN0000000001")
                .driverName("Max Verstappen")
                .build();

        var trip = TripResponse.builder()
                .vehicle(vehicle)
                .startedAt(Instant.parse("2026-04-27T10:00:00Z"))
                .endedAt(Instant.parse("2026-04-27T10:30:00Z"))
                .distanceKm(12.5)
                .avgSpeedKph(25.0)
                .pointCount(3)
                .points(List.of(
                        TripResponse.PointDto.builder()
                                .ts(Instant.parse("2026-04-27T10:00:00Z"))
                                .lat(52.57)
                                .lon(13.50)
                                .speedKph(0.0)
                                .build()
                ))
                .build();

        // Проверка сервиса
        when(tripService.getLastTrip(VEHICLE_ID)).thenReturn(trip);

        mockMvc.perform(get("/api/v1/vehicles/{id}/last-trip", VEHICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.model").value("Mercedes Actros"))
                .andExpect(jsonPath("$.vehicle.driverName").value("Max Verstappen"))
                .andExpect(jsonPath("$.distanceKm").value(12.5))
                .andExpect(jsonPath("$.pointCount").value(3))
                .andExpect(jsonPath("$.points").isArray());
    }

    @Test
    void returns404WhenNoTrips() throws Exception {
        // Проверка сервиса - отсутствие trip
        when(tripService.getLastTrip(VEHICLE_ID)).thenThrow(new TripNotFoundException(VEHICLE_ID));

        mockMvc.perform(get("/api/v1/vehicles/{id}/last-trip", VEHICLE_ID))
                .andExpect(status().isNotFound());
    }
}