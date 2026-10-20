package railflow.load.gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID

class TatkalStormSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/TatkalStorm-3.10")

  val feeder = Iterator.continually(Map(
    "userId" -> ("usr_" + UUID.randomUUID().toString.substring(0, 8)),
    "idempotencyKey" -> UUID.randomUUID().toString,
    "trainNumber" -> "12951",
    "journeyDate" -> "2026-10-22",
    "sourceStation" -> "NDLS",
    "destinationStation" -> "BCT",
    "classCode" -> "3A",
    "quotaCode" -> "TQ"
  ))

  // Scenario 1: Tatkal Rush Intent Submission & Virtual Queue Advance
  val tatkalRushScenario = scenario("Tatkal 10:00:00 Rush")
    .feed(feeder)
    .exec(
      http("01_Submit_Booking_Intent")
        .post("/api/v1/booking/intent")
        .header("X-Idempotency-Key", "#{idempotencyKey}")
        .body(StringBody(
          """{
            "userId": "#{userId}",
            "trainNumber": "#{trainNumber}",
            "journeyDate": "#{journeyDate}",
            "sourceStation": "#{sourceStation}",
            "destinationStation": "#{destinationStation}",
            "classCode": "#{classCode}",
            "quotaCode": "#{quotaCode}",
            "passengerCount": 1
          }"""
        )).asJson
        .check(status.in(200, 202, 429))
        .check(
          checkIf((response: io.gatling.http.response.Response, session: Session) => response.status.code == 200 || response.status.code == 202) {
            jsonPath("$.queueToken").optional.saveAs("queueToken")
          }
        )
    )
    .pause(10.milliseconds, 100.milliseconds)
    .doIf("#{queueToken.exists()}") {
      exec(
        http("02_Poll_Virtual_Queue")
          .get("/api/v1/queue/#{queueToken}")
          .check(status.in(200, 202))
          .check(jsonPath("$.status").saveAs("queueStatus"))
      )
      .pause(200.milliseconds)
      .exec(
        http("03_Hold_Berth")
          .post("/api/v1/booking/hold")
          .header("X-Idempotency-Key", "#{idempotencyKey}")
          .body(StringBody(
            """{
              "queueToken": "#{queueToken}",
              "trainNumber": "#{trainNumber}",
              "journeyDate": "#{journeyDate}",
              "passengers": [
                {
                  "name": "Storm Passenger",
                  "age": 30,
                  "gender": "M",
                  "berthPreference": "LB"
                }
              ]
            }"""
          )).asJson
          .check(status.in(200, 409))
          .check(
            checkIf((response: io.gatling.http.response.Response, session: Session) => response.status.code == 200) {
              jsonPath("$.holdId").saveAs("holdId")
            }
          )
      )
      .doIf("#{holdId.exists()}") {
        exec(
          http("04_Process_Payment")
            .post("/api/v1/payments")
            .header("X-Idempotency-Key", "#{idempotencyKey}")
            .body(StringBody(
              """{
                "holdId": "#{holdId}",
                "amount": 2150.00,
                "currency": "INR",
                "paymentMethod": "UPI"
              }"""
            )).asJson
            .check(status.is(200))
            .check(jsonPath("$.paymentReference").saveAs("paymentRef"))
        )
        .exec(
          http("05_Confirm_Booking")
            .post("/api/v1/booking/confirm")
            .header("X-Idempotency-Key", "#{idempotencyKey}")
            .body(StringBody(
              """{
                "holdId": "#{holdId}",
                "paymentReference": "#{paymentRef}"
              }"""
            )).asJson
            .check(status.is(200))
            .check(jsonPath("$.pnr").saveAs("confirmedPnr"))
        )
      }
    }

  setUp(
    tatkalRushScenario.inject(
      rampUsers(1000).during(2.seconds),    // Intense initial burst
      constantUsersPerSec(500).during(10.seconds), // Sustained Tatkal pressure
      rampUsers(100).during(5.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.successfulRequests.percent.gte(95.0),
     details("01_Submit_Booking_Intent").responseTime.percentile(99).lte(15) // Edge admission <15ms
   )
}
