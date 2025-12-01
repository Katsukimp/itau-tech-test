# 🏦 Banking Transaction API

> Microserviço de transações bancárias com notificação BACEN para processamento de transferências com idempotência, resiliência e alta performance.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-Latest-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-3.9.1-black.svg)](https://kafka.apache.org/)

---

## Visão Geral

Sistema de transferências bancárias que processa ~150 req/s com latência P99 < 100ms, garantindo idempotência via Redis e resiliência com Circuit Breaker + Kafka.

**Principais recursos:**
- Transferências entre contas com validação de saldo e limites
- Notificação síncrona ao BACEN com fallback assíncrono via Kafka
- Idempotência para prevenir duplicações
- Cache Redis de dados do cliente (TTL 24h)

## Arquitetura
Arquitetura em Camadas Tradicional com padrões DDD (Domain-Driven Design) e Spring Framework

**Fluxo de Transferência:**
1. Verificação de idempotência (Redis)
2. Lock pessimista nas contas (PostgreSQL)
3. Validações em cadeia (conta ativa, saldo, limites)
4. Execução da transação ACID
5. Notificação BACEN síncrona com fallback Kafka
6. Resposta ao cliente

**Componentes:**
- **API REST**: Spring Boot 3.5.8 + Java 21
- **Banco de Dados**: PostgreSQL 15 (HikariCP pool: 50 conexões)
- **Cache**: Redis (Lettuce pool: 50 conexões, TTL 24h)
- **Mensageria**: Kafka 3.9.1 (5 threads consumidoras)
- **Resiliência**: Resilience4j (Circuit Breaker, Retry, Rate Limiter)
- **Observabilidade**: Spring Actuator + Prometheus

## Pré-requisitos

| Software | Versão | Comando |
|----------|--------|---------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker + Compose | 20+ / 2.0+ | `docker --version` |
| Scala | 2.13+ | `scala -version` (opcional - Maven baixa automaticamente) |

> **Nota sobre Scala:** O Gatling (testes de carga) usa Scala, mas o Maven baixa automaticamente via `scala-maven-plugin`. Não é necessário instalar Scala localmente.

## Instalação e Execução

### 1. Subir Infraestrutura

```bash
# Iniciar PostgreSQL, Redis, Kafka e Zookeeper
docker-compose up -d

# Verificar se os serviços estão rodando
docker-compose ps
```

**Serviços disponíveis:**
- PostgreSQL: `localhost:5432` (admin/admin)
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- PgAdmin: `http://localhost:5050` (admin@admin.com/admin)

### 2. Executar Aplicação

```bash
# Compilar e executar
./mvnw spring-boot:run

# Verificar se subiu
curl http://localhost:8080/actuator/health
```

**URLs principais:**
- API: `http://localhost:8080/api/v1`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Métricas: `http://localhost:8080/actuator/prometheus`

## API Endpoints

### 1. POST /api/v1/transaction/transfer
Realiza transferência entre contas com idempotência.

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/transaction/transfer \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "amount": 100.00,
    "description": "Pagamento de serviços"
  }'
```

**Request Body:**
```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 100.00,
  "description": "Pagamento de serviços"
}
```

**Response 200 OK:**
```json
{
  "transactionId": 123,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "sourceAccount": {
    "accountId": 1,
    "accountNumber": "ACC-001",
    "customerName": "João Silva"
  },
  "destinationAccount": {
    "accountId": 2,
    "accountNumber": "ACC-002",
    "customerName": "Maria Santos"
  },
  "amount": 100.00,
  "transactionDate": "2025-11-30T10:30:45",
  "message": "Transferência realizada com sucesso"
}
```

**Response 409 Conflict (Duplicação):**
```json
{
  "timestamp": "2025-11-30T10:30:46",
  "status": 409,
  "error": "Conflict",
  "message": "Transaction already processed with idempotency key: 550e8400-e29b-41d4-a716-446655440000. Transaction ID: 123",
  "path": "/api/v1/transaction/transfer"
}
```

**Response 400 Bad Request (Saldo Insuficiente):**
```json
{
  "timestamp": "2025-11-30T10:30:45",
  "status": 400,
  "error": "Bad Request",
  "message": "Saldo insuficiente. Saldo atual: R$ 50,00, Valor solicitado: R$ 100,00",
  "path": "/api/v1/transaction/transfer"
}
```

**Códigos HTTP:**
- `200 OK` - Transferência realizada com sucesso
- `400 Bad Request` - Saldo insuficiente, limite excedido, valor inválido
- `404 Not Found` - Conta não encontrada
- `409 Conflict` - Transação duplicada (idempotency key já usada)
- `422 Unprocessable Entity` - Conta inativa ou transferência para mesma conta

---

### 2. GET /api/v1/account?accountId={id}
Consulta informações de uma conta específica.

**Request:**
```bash
curl -X GET "http://localhost:8080/api/v1/account?accountId=1" \
  -H "Content-Type: application/json"
```

**Response 200 OK:**
```json
{
  "id": 1,
  "accountNumber": "ACC-001",
  "balance": 5000.00,
  "dailyLimit": 1000.00,
  "status": "ACTIVE",
  "customerId": 1,
  "version": 0,
  "createdAt": "2025-11-01T10:00:00",
  "updatedAt": "2025-11-30T10:30:45"
}
```

**Response 404 Not Found:**
```json
{
  "timestamp": "2025-11-30T10:30:45",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found with id: 999",
  "path": "/api/v1/account"
}
```

---

### 3. GET /api/v1/account/all
Lista todas as contas disponíveis (cenário de teste).

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/account/all \
  -H "Content-Type: application/json"
```

**Response 200 OK:**
```json
[
  {
    "id": 1,
    "accountNumber": "ACC-001",
    "balance": 5000.00,
    "dailyLimit": 1000.00,
    "status": "ACTIVE",
    "customerId": 1,
    "version": 0,
    "createdAt": "2025-11-01T10:00:00",
    "updatedAt": "2025-11-30T10:30:45"
  },
  {
    "id": 2,
    "accountNumber": "ACC-002",
    "balance": 5000.00,
    "dailyLimit": 1000.00,
    "status": "ACTIVE",
    "customerId": 2,
    "version": 0,
    "createdAt": "2025-11-01T10:00:00",
    "updatedAt": "2025-11-30T10:30:45"
  }
]
```

---

### PowerShell Examples

```powershell
# 1. Transferência
$headers = @{
    "Content-Type" = "application/json"
    "Idempotency-Key" = [guid]::NewGuid().ToString()
}
$body = @{
    sourceAccountId = 1
    destinationAccountId = 2
    amount = 100.00
    description = "Pagamento de serviços"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/transaction/transfer" `
  -Headers $headers `
  -Body $body

# 2. Consultar conta
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/account?accountId=1"

# 3. Listar todas as contas
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/account/all"
```

---

### Documentação Interativa

**Swagger UI:** http://localhost:8080/swagger-ui.html

Teste todos os endpoints diretamente pelo navegador com interface interativa.

---

## Testes

### Testes Automatizados
```bash
# Executar todos os testes (52 testes: 38 unit + 6 integration + 8 component)
./mvnw clean test

# Gerar relatório de cobertura
./mvnw test jacoco:report
start target/site/jacoco/index.html
```

### Testes de Carga (Gatling + Scala)

**Pré-requisito:** Aplicação rodando em `http://localhost:8080`

```bash
# 1. Iniciar aplicação
./mvnw spring-boot:run

# 2. Executar Gatling (em outro terminal)
./mvnw gatling:test

# 3. Ver relatório HTML
start target/gatling/bankingtransactionloadtest-*/index.html
```

**Sobre o Gatling:**
- Testes escritos em **Scala** (`src/test/scala/simulations/BankingTransactionLoadTest.scala`)
- Maven baixa Scala automaticamente via `scala-maven-plugin` (versão 2.13.12)
- Não é necessário instalar Scala localmente

**Cenários de teste:**
- **Ramp-up**: 5→50 RPS durante 30s (~2.300 requests)
- **Constant Load**: 100 RPS durante 30s (~3.000 requests)  
- **Stress Test**: 50→150 RPS durante 10s (~1.300 requests)
- **Total**: ~8.000 requests em ~77s

**Métricas esperadas:**
- ✅ P99 < 100ms
- ✅ Taxa de sucesso > 95%
- ✅ Throughput: 80-150 RPS

## Configuração

**Principais configurações** (`src/main/resources/application.properties`):

```properties
# PostgreSQL
spring.datasource.hikari.maximum-pool-size=50

# Redis
spring.data.redis.lettuce.pool.max-active=50

# Kafka
spring.kafka.listener.concurrency=5

# Regras de negócio
banking.transfer.minimum-amount=0.01
banking.cache.customer.ttl-hours=24
banking.notification.max-retry-attempts=3
```

## Monitoramento

```bash
# Health check
curl http://localhost:8080/actuator/health

# Métricas Prometheus
curl http://localhost:8080/actuator/prometheus
```

**Principais métricas:**
- `http_server_requests_seconds` - Latência HTTP
- `hikaricp_connections_active` - Pool de conexões
- `resilience4j_circuitbreaker_state` - Estado do Circuit Breaker

## Comandos Úteis

```bash
# Ver logs
docker-compose logs -f

# Reiniciar serviço
docker-compose restart postgres

# Parar tudo (mantém dados)
docker-compose stop

# Limpar tudo (APAGA DADOS)
docker-compose down -v

# Acessar PostgreSQL
docker-compose exec postgres psql -U admin -d itau_banking
```

## Troubleshooting

**Aplicação não inicia:** Verificar se `docker-compose ps` mostra todos os serviços UP  
**Testes falhando:** `docker system prune -a` para limpar Docker  
**Kafka não conecta:** `docker-compose restart zookeeper kafka`  
**Redis timeout:** `docker-compose exec redis redis-cli ping` (deve retornar PONG)
