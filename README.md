# 🏦 Banking Transaction API

> Microserviço de transações bancárias com notificação BACEN para processamento de transferências com idempotência, resiliência e alta performance.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-Latest-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-3.9.1-black.svg)](https://kafka.apache.org/)

---

# Banking Transaction API

Microserviço de transações bancárias com notificação BACEN para processamento de transferências com idempotência, resiliência e alta performance.

**Stack:** Java 21 · Spring Boot 3.5.8 · PostgreSQL 15 · Redis · Kafka 3.9.1

## Visão Geral

Sistema de transferências bancárias que processa ~150 req/s com latência P99 < 100ms, garantindo idempotência via Redis e resiliência com Circuit Breaker + Kafka.

**Principais recursos:**
- Transferências entre contas com validação de saldo e limites
- Notificação síncrona ao BACEN com fallback assíncrono via Kafka
- Idempotência para prevenir duplicações
- Cache Redis de dados do cliente (TTL 24h)
- 52 testes automatizados (100% cobertura)  

## Arquitetura

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

### POST /api/v1/transaction/transfer
Realiza transferência entre contas.

**Headers:**
```http
Content-Type: application/json
Idempotency-Key: <UUID único>
```

**Request:**
```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 100.00
}
```

**Resposta 200 OK:**
```json
{
  "transactionId": 123,
  "status": "SUCCESS",
  "sourceAccount": { "accountId": 1, "balance": 4900.00 },
  "destinationAccount": { "accountId": 2, "balance": 5100.00 },
  "amount": 100.00,
  "timestamp": "2025-11-30T10:30:45"
}
```

**Códigos de erro:** 400 (saldo insuficiente), 404 (conta não encontrada), 409 (duplicação), 422 (conta inativa)

### GET /api/v1/transaction/get-accounts
Lista todas as contas disponíveis.

**Exemplo cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/transaction/transfer \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":100.00}'
```

**Swagger UI:** http://localhost:8080/swagger-ui.html

## Testes

### Testes Automatizados
```bash
# Executar todos os testes (52 testes: 38 unit + 6 integration + 8 component)
./mvnw clean test

# Gerar relatório de cobertura
./mvnw test jacoco:report
start target/site/jacoco/index.html
```

### Testes de Carga (Gatling)
```bash
# 1. Iniciar aplicação
./mvnw spring-boot:run

# 2. Executar Gatling (em outro terminal)
./mvnw gatling:test

# 3. Ver relatório
start target/gatling/bankingtransactionloadtest-*/index.html
```

**Resultado esperado:** ~8.000 requests, P99 < 100ms, taxa de sucesso > 95%

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
