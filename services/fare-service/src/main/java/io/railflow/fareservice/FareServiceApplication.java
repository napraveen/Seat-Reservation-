package io.railflow.fareservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
@RequestMapping("/api/v1/fare")
@CrossOrigin(origins = "*")
public class FareServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FareServiceApplication.class, args);
    }

    public record FareQuote(
        String trainNumber,
        String classCode,
        double baseFare,
        double reservationCharge,
        double superfastSurcharge,
        double flexiMultiplier,
        double gst,
        double totalFare,
        String ruleVersion
    ) {}

    @GetMapping("/quote")
    public ResponseEntity<FareQuote> getQuote(
        @RequestParam String trainNumber,
        @RequestParam String classCode,
        @RequestParam(defaultValue = "1380") int distanceKm,
        @RequestParam(defaultValue = "0.65") double occupancyRatio
    ) {
        // Deterministic versioned rule computation
        double base = 0.85 * distanceKm;
        double reservation = 40.0;
        double superfast = 45.0;
        // Dynamic flexi-fare: base rises 10% for every 10% of berths sold above 50%, capped at 1.5x
        double flexi = 1.0;
        if (occupancyRatio > 0.50) {
            int slabs = (int)((occupancyRatio - 0.50) / 0.10);
            flexi = Math.min(1.5, 1.0 + (slabs * 0.10));
        }

        double subtotal = (base * flexi) + reservation + superfast;
        double gst = classCode.endsWith("A") || classCode.equals("CC") || classCode.equals("EC") ? subtotal * 0.05 : 0.0;
        double total = Math.round((subtotal + gst) * 100.0) / 100.0;

        return ResponseEntity.ok(new FareQuote(
            trainNumber, classCode, base, reservation, superfast, flexi, gst, total, "2026.10-R1"
        ));
    }
}
