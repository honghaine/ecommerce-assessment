# ADR-001: Web MVC + Virtual Threads, Outbox Poller (Kafka later)

> Status: **Accepted** — implemented (see "Decision" at the end).
> Context: FlashSale Service (Java + Spring Boot), 500 TPS target, multi-instance ready, Docker-only dev environment.

---

## 0. TL;DR

**These are not alternatives to each other.** They solve two different problems:

| | Web MVC + Virtual Threads | Outbox + Poller / Kafka |
|---|---|---|
| Problem solved | **How HTTP requests are executed** (threading model) | **How events leave the DB and reach consumers** (e.g. inventory sync) |
| Alternative is | WebFlux (reactive) or MVC with platform threads | Kafka, RabbitMQ, Redis Streams, Debezium CDC |
| Where in the flow | `Client → Controller → Service → DB` | `DB (committed purchase) → event → inventory consumer` |

```
             ┌──────────── (A) request handling ────────────┐
Client ──HTTP──▶ Tomcat ──▶ Controller ──▶ PurchaseService ──▶ PostgreSQL
                (Web MVC + virtual threads)                      │  same TX:
                                                                 │  orders + outbox_events
                                                                 ▼
             ┌──────────── (B) event delivery ───────────────────────────────┐
             outbox_events ──▶ Poller (SKIP LOCKED) ──▶ InventorySyncConsumer
                          or ──▶ Relay ──▶ Kafka topic ──▶ InventorySyncConsumer
```

**Recommendation**
- (A) **Web MVC + virtual threads**: yes.
- (B) **Outbox + DB poller** now, behind an interface. **No Kafka** in the core build. Document the Kafka migration path; add it later as an optional profile only if time allows.

---

## Part A: Web MVC + Virtual Threads

### A.1 What it is

- **Web MVC** = classic Spring, **thread-per-request**. Code is plain blocking Java: JDBC, JPA, `RedisTemplate`.
- **Platform threads** (old default): Tomcat pool ≈ 200 OS threads. When all 200 are blocked waiting on DB or Redis, new requests queue.
- **Virtual threads** (Java 21+): JVM-managed lightweight threads. Blocking I/O *unmounts* the thread and frees the carrier OS thread, so you can have many thousands of concurrent requests with the **same simple blocking code**.
- Enable with one line: `spring.threads.virtual.enabled=true`.

### A.2 Options compared

| | MVC + platform threads | **MVC + virtual threads** | WebFlux (reactive) |
|---|---|---|---|
| Code style | Blocking, simple | Blocking, simple | `Mono`/`Flux`, callback chains |
| JPA / JDBC | ✅ | ✅ | ❌ blocks the event loop. Needs R2DBC and gives up JPA |
| Concurrency ceiling | ~200 in-flight requests | Thousands | Thousands |
| Debugging / stack traces | Easy | Easy | Hard |
| Learning curve / review | Low | Low | High |
| Spring Security, Flyway, ShedLock, Testcontainers | ✅ | ✅ | Partial / different APIs |
| Switch cost | n/a | 1 config flag, reversible | Rewrite |

### A.3 Pros / Cons of MVC + VT

**Pros**
- Same simple code as normal Spring; reviewers can read it.
- Handles traffic spikes (many requests waiting on I/O) without tuning Tomcat threads.
- Works with JPA, JDBC, Redis, Spring Security as-is.
- Reversible: turn the flag off and it's standard MVC.

**Cons / caveats**
- **Does not make the DB faster.** The real ceiling is the **HikariCP pool** (e.g. 20–30 connections). VT just let more requests *wait* cheaply, so pool size and timeouts must be tuned.
- **Pinning** (Java 21–23): `synchronized` blocks around I/O pin the carrier thread. Use `ReentrantLock` in our code and pgjdbc ≥ 42.6. **Java 25 LTS removes this issue (JEP 491)**, so consider bumping from Java 21 to 25.
- Heavy `ThreadLocal` caching is wasteful with millions of threads (not an issue for us).
- CPU-bound work gains nothing (not our case, we're I/O-bound).

### A.4 Is it enough for 500 TPS?

Rough math for the purchase hot path:
- Redis Lua gate ≈ 1 ms, which rejects most requests once sold out or already bought.
- DB transaction (3 conditional updates + 3 inserts) ≈ 5–10 ms.
- Pool 30 connections × (1000 ms / 10 ms) ≈ **3,000 TX/s theoretical** → 500 TPS leaves a big margin.
- Plus most flash-sale traffic is rejected at Redis and never touches the DB.

**Verdict A: MVC + virtual threads.** WebFlux adds complexity with no benefit here.

---

## Part B: Event delivery (Outbox + Poller vs Kafka)

### B.1 Why an outbox at all: the dual-write problem

A purchase must (1) commit the order to PostgreSQL **and** (2) tell inventory sync about it.

```
❌ Naive:  commit DB  ──▶  kafka.send()      → app crashes between = order exists, inventory never updated
❌ Naive:  kafka.send() ──▶ commit DB        → TX rolls back = inventory updated for an order that doesn't exist
✅ Outbox: INSERT order + INSERT outbox_event in ONE DB transaction → event exists iff order exists
```

**Key point: Kafka does not remove the need for the outbox.** With Kafka you still write to the outbox and then a relay publishes to Kafka. Kafka changes only the *transport after the outbox*, not the correctness mechanism.

### B.2 Option 1: Outbox + DB poller (current pick)

```
@Scheduled every 500ms (every instance):
  BEGIN
  SELECT * FROM outbox_events WHERE status='PENDING'
    ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED;   -- instances never grab the same row
  for each → handler (dedupe via processed_events) → mark PROCESSED / attempts++
  COMMIT
```

| Pros | Cons |
|---|---|
| **Zero extra infra**: just PostgreSQL, which we already have | Polling adds some DB load (tiny at our volume) |
| Same-DB transaction means strongest consistency, simple reasoning | Latency = poll interval (~0.5–1 s). Fine for inventory sync |
| Multi-instance safe via `SKIP LOCKED` (no leader election needed) | No replay or fan-out to many independent services |
| Easy to test with Testcontainers (Postgres only) | Doesn't scale to very high event volume (100k+/s) |
| Easy to explain in a presentation | Need own retry, backoff and "FAILED/dead" handling (small code) |
| Docker Compose stays 3 services | Ordering only per query order, not per partition key |

### B.3 Option 2: Outbox + relay → Kafka

```
outbox_events ──▶ relay (poller or Debezium CDC) ──▶ Kafka topic "order-events" (key=productId)
                                                   └─▶ consumer group "inventory-sync"
```

| Pros | Cons |
|---|---|
| Industry-standard, looks "production-like" to reviewers | **Still needs the outbox + relay**, so it's additive complexity, not a replacement |
| Fan-out: many services consume the same event independently | Extra infra: broker container, topics, partitions, consumer groups, offsets |
| Replay / retention: rebuild a consumer from history | Still **at-least-once**, so still need `processed_events` dedupe |
| Per-key ordering via partitions (e.g. by `productId`) | Retry + dead letter topic (DLT) + serialization config (JSON/Avro) to design |
| Very high throughput, decouples producer and consumer speed | Heavier Docker (~1 GB RAM for broker), slower startup, flaky-test risk |
| Natural path to microservices | More config and failure modes to explain in the presentation; time taken from the core concurrency work |

**"Kafka is hard to config": how true is it now?**
- Broker setup got easier: **KRaft mode, no ZooKeeper**. A single-node `apache/kafka` container is ~15 lines of Compose.
- The hard part is **not the container**, it's the semantics: partitions and keys, consumer group rebalancing, offset commits vs DB commit, retry/DLT topology, idempotent consumers, schema evolution, and testing all of that. That is where bugs and presentation questions come from.

### B.4 Option 3 (for completeness): Redis Streams

| Pros | Cons |
|---|---|
| Already have Redis, so no new infra | Still needs outbox (same dual-write issue) |
| Consumer groups, ack, pending-entries list | Weaker durability than Kafka/Postgres (depends on AOF config) |
| Lighter than Kafka | Less standard, and the ecosystem is thinner |

### B.5 Does our workload need Kafka?

- Events are produced **only on successful purchases** plus admin product/stock changes.
- Successful purchases are **bounded by quota**: e.g. 10 sessions/day × 20 items × 100 quota = **~20k events/day**, peaking at maybe tens per second.
- The 500 TPS is mostly **rejected** traffic (sold out / already bought / outside window), which creates **no events**.
- There's only **one consumer** today (inventory sync).

→ Event volume is tiny; a DB poller handles it easily. Kafka's strengths (fan-out, replay, huge throughput) aren't exercised.

### B.6 Decision matrix (1 = poor, 5 = great)

| Criterion (assignment priority) | DB Poller | Kafka | Redis Streams |
|---|---|---|---|
| Correctness / consistency | 5 | 4 (extra commit boundary) | 3 |
| Simplicity / readability | 5 | 2 | 3 |
| Multi-instance safe | 5 | 5 | 4 |
| Throughput headroom | 3 | 5 | 4 |
| Extensibility (new consumers/services) | 3 | 5 | 3 |
| Docker "just run" + test reliability | 5 | 3 | 4 |
| Time cost for the take-home | 5 | 2 | 3 |
| **Total** | **31** | **26** | **24** |

The assignment says *"design thinking, correctness and extensibility over feature count"*, so the poller wins **as long as extensibility is shown through the design**.

---

## Part C: Recommended approach

1. **Web MVC + virtual threads** (`spring.threads.virtual.enabled=true`), HikariCP tuned. Consider **Java 25 LTS** to avoid pinning.
2. **Outbox + DB poller** as the only delivery mechanism in the core build.
3. **Design the seam so Kafka is a plug-in, not a rewrite:**
   ```java
   // domain side: unchanged whichever transport is used
   interface DomainEventPublisher { void publish(DomainEvent e); }      // writes to outbox_events in current TX

   // transport side: swappable
   interface OutboxDispatcher { void dispatch(OutboxEvent e); }
   class InProcessDispatcher implements OutboxDispatcher { ... }       // default: calls handlers directly
   class KafkaDispatcher     implements OutboxDispatcher { ... }       // future: kafkaTemplate.send(topic, key, payload)

   // consumers: idempotent regardless of transport
   interface EventHandler<T> { void handle(T event); }                 // guarded by processed_events
   ```
4. **README / presentation:** a "Scaling path" section:
   - Stage 1 (now): outbox + poller, single DB.
   - Stage 2: `KafkaDispatcher` + separate inventory service consuming `order-events`.
   - Stage 3: Debezium CDC on `outbox_events` (no poller), multiple consumers (notifications, analytics).
5. **Optional bonus** (only after everything else is done and tested): `docker compose --profile kafka` + `KafkaDispatcher` enabled via Spring profile. Shows the seam works without making Kafka mandatory.

---

## Decision (accepted)

| Question | Decision | Where in the code |
|---|---|---|
| Request model | **Spring Web MVC + virtual threads**, no WebFlux | `spring.threads.virtual.enabled=true` |
| Java version | **Java 25 LTS** (no virtual-thread pinning on `synchronized`) | `pom.xml`, `Dockerfile` |
| Event transport | **Transactional outbox + DB poller** only; Kafka documented as the scaling path, no Kafka container | `outbox/` module |
| Poll interval | **500 ms**, one event per transaction, `FOR UPDATE SKIP LOCKED`, backoff + dead letter after 10 attempts | `OutboxPoller`, `OutboxProcessor` |
| Kafka seam | `DomainEventPublisher` (domain side) → `outbox_events`; `OutboxDispatcher` (transport side) with `InProcessOutboxDispatcher` today; handlers implement `EventHandler`, deduped by `processed_events` | `outbox/service` |

Consequences observed after implementation: with inventory settled once per slot (not per sale), event volume is one
event per flash-sale item per slot plus product changes — far below what a DB poller handles; the outbox lag is
monitored (`outbox_lag_seconds`, "Outbox stuck" alert).
