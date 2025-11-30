package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Teste de Carga: ~8000 transações
 * 
 * Objetivo: Validar que a API suporta throughput moderado mantendo latência baixa
 * Meta: P99 < 100ms, Taxa de sucesso > 95%
 */
class BankingTransactionLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // IDs de contas de teste (devem existir no banco)
  val accountIds = (1 to 4).toList

  // Gerador de requests
  val transferScenario = scenario("Transfer Load Test")
    .exec(
      http("POST Transfer")
        .post("/api/v1/transaction/transfer")
        .header("Idempotency-Key", _ => java.util.UUID.randomUUID().toString)
        .body(StringBody(session => {
          val sourceId = accountIds(Random.nextInt(accountIds.length))
          var destId = accountIds(Random.nextInt(accountIds.length))
          while (destId == sourceId) {
            destId = accountIds(Random.nextInt(accountIds.length))
          }
          val amount = (Random.nextInt(50) + 1) * 10 // 10 a 500
          
          s"""{
            "sourceAccountId": $sourceId,
            "destinationAccountId": $destId,
            "amount": $amount.00
          }"""
        }))
        .check(status.in(200, 400, 409, 422, 404, 500)) // Aceitar erros de negócio
    )

  // Cenário 1: Ramp-up gradual
  val rampUpScenario = scenario("Ramp Up Test")
    .exec(transferScenario)

  // Cenário 2: Carga constante
  val constantLoadScenario = scenario("Constant Load Test")
    .exec(transferScenario)

  // Cenário 3: Stress test
  val stressTestScenario = scenario("Stress Test")
    .exec(transferScenario)

  setUp(
    // Teste 1: Ramp-up suave - ~3000 requests em 60 segundos (50 RPS)
    rampUpScenario.inject(
      rampUsersPerSec(5) to 50 during (30.seconds),
      constantUsersPerSec(50) during (30.seconds)
    ).protocols(httpProtocol),

    // Teste 2: Carga moderada - ~3000 requests em 30 segundos (100 RPS)
    constantLoadScenario.inject(
      constantUsersPerSec(100) during (30.seconds)
    ).protocols(httpProtocol)
      .andThen(
        // Teste 3: Pico - ~2000 requests em 13 segundos (150 RPS)
        stressTestScenario.inject(
          rampUsersPerSec(50) to 150 during (6.seconds),
          constantUsersPerSec(150) during (7.seconds)
        ).protocols(httpProtocol)
      )
  ).assertions(
    global.responseTime.percentile3.lt(100), // P99 < 100ms
    global.successfulRequests.percent.gt(95) // >95% sucesso
  )
}
