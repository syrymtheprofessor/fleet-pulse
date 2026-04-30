package com.pingine.fleetpulse.service;

import com.pingine.fleetpulse.api.dto.TripResponse;
import com.pingine.fleetpulse.api.dto.VehicleResponse;
import com.pingine.fleetpulse.domain.Trip;
import com.pingine.fleetpulse.persistence.mongo.TelemetryPoint;
import com.pingine.fleetpulse.persistence.mongo.TelemetryRepository;
import com.pingine.fleetpulse.service.trip.TripDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TelemetryRepository telemetryRepository;
    private final TripDetector tripDetector;
    private final VehicleService vehicleService;

    @Override
    public TripResponse getLastTrip(String vehicleId) {
        List<TelemetryPoint> points = telemetryRepository.findByVehicleId(vehicleId);
        List<Trip> trips = tripDetector.detect(points); // находим конечную остановку
        if (trips.isEmpty()) {
            throw new TripNotFoundException(vehicleId); // Если поездок нет — верни 404 (можно бросить VehicleNotFoundException или завести своё исключение).
        }

        Trip lastTrip = trips.get(trips.size() - 1);
        VehicleResponse vehicle = vehicleService.getById(vehicleId);

        return TripResponse.builder()
                .vehicle(vehicle)
                .startedAt(lastTrip.getStartedAt())
                .endedAt(lastTrip.getEndedAt())
                .distanceKm(lastTrip.getDistanceKm())
                .avgSpeedKph(lastTrip.getAvgSpeedKph())
                .pointCount(lastTrip.getPoints().size())
                .points(lastTrip.getPoints().stream()
                        .map(p -> TripResponse.PointDto.builder()
                                .ts(p.getTs())
                                .lat(p.getLat())
                                .lon(p.getLon())
                                .speedKph(p.getSpeedKph())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
