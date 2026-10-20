package io.railflow.catalog;

import io.railflow.contracts.ApiDtos;
import io.railflow.inventory.core.BerthType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class CatalogController {

    private final List<ApiDtos.StationDto> stations = new ArrayList<>();
    private final List<TrainRoute> trains = new ArrayList<>();

    public record TrainRoute(
        String trainNumber,
        String trainName,
        String origin,
        String destination,
        List<String> stopCodes,
        String depTime,
        String arrTime,
        String duration,
        List<ClassFare> classes
    ) {}

    public record ClassFare(
        String classCode,
        String className,
        double baseFare,
        int totalBerths,
        int defaultTatkalQuota
    ) {}

    public record CoachBerthOccupancy(
        int berthNumber,
        String berthType,
        int bayNumber,
        long segmentMask,
        String status, // FREE, PARTIAL, FULL
        List<String> occupiedLegs,
        double fragmentationWaste
    ) {}

    public record CoachLayoutResponse(
        String trainNumber,
        String journeyDate,
        String coachId,
        String classCode,
        int totalBerths,
        List<String> routeStops,
        List<CoachBerthOccupancy> berths
    ) {}

    public CatalogController() {
        initSeedData();
    }

    private void initSeedData() {
        // Core Hub Stations
        stations.add(new ApiDtos.StationDto("NDLS", "New Delhi", "Delhi", 28.6424, 77.2195));
        stations.add(new ApiDtos.StationDto("BCT", "Mumbai Central", "Maharashtra", 18.9696, 72.8193));
        stations.add(new ApiDtos.StationDto("CSTM", "Chhatrapati Shivaji Maharaj Terminus", "Maharashtra", 18.9401, 72.8354));
        stations.add(new ApiDtos.StationDto("HWH", "Howrah Junction", "West Bengal", 22.5839, 88.3433));
        stations.add(new ApiDtos.StationDto("MAS", "Chennai Central", "Tamil Nadu", 13.0827, 80.2755));
        stations.add(new ApiDtos.StationDto("SBC", "KSR Bengaluru City", "Karnataka", 12.9781, 77.5696));
        stations.add(new ApiDtos.StationDto("SC", "Secunderabad Junction", "Telangana", 17.4344, 78.5011));
        stations.add(new ApiDtos.StationDto("BPL", "Bhopal Junction", "Madhya Pradesh", 23.2599, 77.4126));
        stations.add(new ApiDtos.StationDto("NGP", "Nagpur Junction", "Maharashtra", 21.1500, 79.0882));
        stations.add(new ApiDtos.StationDto("CNB", "Kanpur Central", "Uttar Pradesh", 26.4539, 80.3507));
        stations.add(new ApiDtos.StationDto("PRYJ", "Prayagraj Junction", "Uttar Pradesh", 25.4358, 81.8463));
        stations.add(new ApiDtos.StationDto("DDU", "Pt. Deen Dayal Upadhyaya Junction", "Uttar Pradesh", 25.2818, 83.1197));
        stations.add(new ApiDtos.StationDto("KOTA", "Kota Junction", "Rajasthan", 25.2215, 75.8679));
        stations.add(new ApiDtos.StationDto("BRC", "Vadodara Junction", "Gujarat", 22.3107, 73.1812));
        stations.add(new ApiDtos.StationDto("ST", "Surat", "Gujarat", 21.2049, 72.8406));
        stations.add(new ApiDtos.StationDto("PUNE", "Pune Junction", "Maharashtra", 18.5284, 73.8743));
        stations.add(new ApiDtos.StationDto("ADI", "Ahmedabad Junction", "Gujarat", 23.0225, 72.6015));

        // Trains
        trains.add(new TrainRoute(
            "12951", "Mumbai Rajdhani Express", "NDLS", "BCT",
            List.of("NDLS", "KOTA", "BRC", "ST", "BCT"),
            "16:55", "08:35", "15h 40m",
            List.of(
                new ClassFare("1A", "First AC", 4850.0, 24, 6),
                new ClassFare("2A", "2 Tier AC", 2980.0, 54, 16),
                new ClassFare("3A", "3 Tier AC", 2150.0, 72, 24),
                new ClassFare("3E", "3 AC Economy", 1950.0, 80, 28)
            )
        ));

        trains.add(new TrainRoute(
            "12301", "Howrah Rajdhani Express", "NDLS", "HWH",
            List.of("NDLS", "CNB", "PRYJ", "DDU", "HWH"),
            "16:50", "09:55", "17h 05m",
            List.of(
                new ClassFare("1A", "First AC", 4750.0, 24, 6),
                new ClassFare("2A", "2 Tier AC", 2890.0, 54, 16),
                new ClassFare("3A", "3 Tier AC", 2090.0, 72, 24)
            )
        ));

        trains.add(new TrainRoute(
            "12627", "Karnataka Express", "NDLS", "SBC",
            List.of("NDLS", "BPL", "NGP", "SC", "SBC"),
            "20:20", "13:40", "41h 20m",
            List.of(
                new ClassFare("1A", "First AC", 5350.0, 18, 4),
                new ClassFare("2A", "2 Tier AC", 3450.0, 48, 12),
                new ClassFare("3A", "3 Tier AC", 2390.0, 64, 20),
                new ClassFare("SL", "Sleeper Class", 890.0, 72, 32)
            )
        ));

        trains.add(new TrainRoute(
            "12002", "Bhopal Shatabdi Express", "NDLS", "BPL",
            List.of("NDLS", "CNB", "BPL"),
            "06:00", "14:40", "8h 40m",
            List.of(
                new ClassFare("EC", "Executive Class", 2620.0, 46, 8),
                new ClassFare("CC", "AC Chair Car", 1490.0, 78, 24)
            )
        ));
    }

    @GetMapping("/stations")
    public ResponseEntity<List<ApiDtos.StationDto>> searchStations(@RequestParam(required = false, defaultValue = "") String q) {
        String query = q.trim().toUpperCase();
        if (query.isEmpty()) return ResponseEntity.ok(stations);

        List<ApiDtos.StationDto> matches = stations.stream()
            .filter(s -> s.stationCode().contains(query) || s.name().toUpperCase().contains(query))
            .limit(10)
            .toList();
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/trains/search")
    public ResponseEntity<List<ApiDtos.TrainSearchResultDto>> searchTrains(
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam String date,
        @RequestParam(required = false) String classCode,
        @RequestParam(required = false, defaultValue = "GN") String quotaCode
    ) {
        List<ApiDtos.TrainSearchResultDto> results = new ArrayList<>();

        for (TrainRoute route : trains) {
            boolean fromMatches = route.stopCodes().contains(from.toUpperCase());
            boolean toMatches = route.stopCodes().contains(to.toUpperCase());

            if (fromMatches && toMatches && route.stopCodes().indexOf(from.toUpperCase()) < route.stopCodes().indexOf(to.toUpperCase())) {
                List<ApiDtos.ClassAvailabilityDto> classAvails = new ArrayList<>();
                for (ClassFare cf : route.classes()) {
                    if (classCode != null && !classCode.equalsIgnoreCase(cf.classCode())) {
                        continue;
                    }
                    int availCount = "TQ".equalsIgnoreCase(quotaCode) ? cf.defaultTatkalQuota() : (int)(cf.totalBerths() * 0.45);
                    String status = availCount > 0 ? "AVAILABLE-" + availCount : "RAC 12 / WL 4";
                    classAvails.add(new ApiDtos.ClassAvailabilityDto(
                        cf.classCode(), quotaCode, status, availCount, cf.baseFare(), "authoritative"
                    ));
                }

                results.add(new ApiDtos.TrainSearchResultDto(
                    route.trainNumber(), route.trainName(), from.toUpperCase(), to.toUpperCase(),
                    route.depTime(), route.arrTime(), route.duration(), classAvails
                ));
            }
        }

        return ResponseEntity.ok()
            .header("as_of", "2026-10-21T10:00:00.120Z")
            .header("staleness_ms", "120")
            .body(results);
    }

    /**
     * Showpiece 1: Interactive Coach Map.
     * Returns 72 berths in bays of 8 with realistic segment occupancy and hover Gantt strips.
     */
    @GetMapping("/coaches/{train}/{date}/{classCode}/layout")
    public ResponseEntity<CoachLayoutResponse> getCoachLayout(
        @PathVariable String train,
        @PathVariable String date,
        @PathVariable String classCode,
        @RequestParam(defaultValue = "B3") String coachId
    ) {
        List<String> stops = List.of("NDLS", "KOTA", "BRC", "ST", "BCT");
        int totalSegments = stops.size() - 1; // 4 segments: [NDLS-KOTA, KOTA-BRC, BRC-ST, ST-BCT]

        List<CoachBerthOccupancy> berths = new ArrayList<>();
        BerthType[] standardPattern = new BerthType[]{
            BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
            BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
            BerthType.SIDE_LOWER, BerthType.SIDE_UPPER
        };

        Random rng = new Random((train + date + coachId).hashCode());

        for (int b = 1; b <= 72; b++) {
            int bayNumber = (b - 1) / 8 + 1;
            BerthType type = standardPattern[(b - 1) % 8];

            // Deterministic realistic occupancy:
            // ~35% completely free, ~40% partially booked on some segments, ~25% fully occupied
            int roll = rng.nextInt(100);
            long segmentMask = 0L;
            List<String> occupiedLegs = new ArrayList<>();
            String status;

            if (roll < 35) {
                status = "FREE";
            } else if (roll < 75) {
                status = "PARTIAL";
                // Takes 1 or 2 intermediate segments
                int start = rng.nextInt(totalSegments);
                int end = Math.min(totalSegments, start + 1 + rng.nextInt(2));
                for (int s = start; s < end; s++) {
                    segmentMask |= (1L << s);
                    occupiedLegs.add(stops.get(s) + " -> " + stops.get(s + 1));
                }
            } else {
                status = "FULL";
                segmentMask = (1L << totalSegments) - 1;
                for (int s = 0; s < totalSegments; s++) {
                    occupiedLegs.add(stops.get(s) + " -> " + stops.get(s + 1));
                }
            }

            // Fragmentation waste: how much open span is trapped
            double waste = (status.equals("PARTIAL")) ? 0.45 : (status.equals("FREE") ? 0.0 : 0.0);

            berths.add(new CoachBerthOccupancy(
                b, type.code(), bayNumber, segmentMask, status, occupiedLegs, waste
            ));
        }

        return ResponseEntity.ok(new CoachLayoutResponse(
            train, date, coachId, classCode.toUpperCase(), 72, stops, berths
        ));
    }
}
