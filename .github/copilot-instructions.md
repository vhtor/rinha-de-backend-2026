# Copilot Instructions — Rinha de Backend 2026

## Read this first

This is a **learning-first project**. The primary goal is deep understanding of every decision and implementation — not just shipping working code. Before implementing anything, always explain:

- **What** is being done
- **Why** this approach was chosen over alternatives
- **What alternatives** were considered and why they were discarded

Never implement silently. Teach as you build.

---

## Project Overview

**Challenge:** Rinha de Backend 2026 — build a fraud detection API using vector search (k-NN).  
**Deadline:** 2026-06-05T23:59:59-03:00  
**Submission repo:** this repository (`rinha-de-backend-2026-vhtor-kotlin`)

### What the API does

```
POST /fraud-score
  1. Transform transaction payload into a 14-dimension float vector
  2. Find the 5 nearest neighbors in 3M reference vectors (references.json.gz)
  3. fraud_score = number_of_frauds_among_5 / 5
  4. approved = fraud_score < 0.6

GET /ready → 200 OK (health check)
Port: 9999
```

### The 14-dimension vector (in order)

| # | Field | Formula |
|---|-------|---------|
| 0 | `amount` | `clamp(transaction.amount / 10000)` |
| 1 | `installments` | `clamp(transaction.installments / 12)` |
| 2 | `amount_vs_avg` | `clamp((transaction.amount / customer.avg_amount) / 10)` |
| 3 | `hour_of_day` | `hour(transaction.requested_at, UTC) / 23` |
| 4 | `day_of_week` | `weekday(transaction.requested_at) / 6` (Mon=0, Sun=6) |
| 5 | `minutes_since_last_tx` | `clamp(minutes / 1440)` or **-1** if `last_transaction == null` |
| 6 | `km_from_last_tx` | `clamp(last_transaction.km_from_current / 1000)` or **-1** if null |
| 7 | `km_from_home` | `clamp(terminal.km_from_home / 1000)` |
| 8 | `tx_count_24h` | `clamp(customer.tx_count_24h / 20)` |
| 9 | `is_online` | `1` if `terminal.is_online` else `0` |
| 10 | `card_present` | `1` if `terminal.card_present` else `0` |
| 11 | `unknown_merchant` | `1` if `merchant.id` NOT in `customer.known_merchants` else `0` |
| 12 | `mcc_risk` | `mcc_risk.json[merchant.mcc]` (default `0.5`) |
| 13 | `merchant_avg_amount` | `clamp(merchant.avg_amount / 10000)` |

`clamp(x)` = keep value in `[0.0, 1.0]`. Indices 5 and 6 use sentinel `-1` (not `0`) when `last_transaction` is null.

### Normalization constants (`normalization.json`)

```json
{
  "max_amount": 10000,
  "max_installments": 12,
  "amount_vs_avg_ratio": 10,
  "max_minutes": 1440,
  "max_km": 1000,
  "max_tx_count_24h": 20,
  "max_merchant_avg_amount": 10000
}
```

---

## Stack & Architecture Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Language | Kotlin | Expressive, coroutines-native, strong JVM ecosystem |
| Framework | Ktor (CIO engine) | Pure Kotlin, zero-friction GraalVM Native Image |
| Build | Gradle Kotlin DSL | Kotlin-native config, best GraalVM plugin support |
| Compilation | GraalVM Native Image (AOT) | ~30 MB footprint vs ~150 MB JVM — essential for 175 MB/instance budget |
| Load balancer | Nginx | Lightweight, round-robin out of the box |
| Vector quantization | int8 | 3M × 14 × float32 ≈ 168 MB → int8 halves that to ~84 MB |

### Infrastructure constraints

- **Total budget:** 1 CPU + 350 MB RAM (all services combined)
- **Minimum topology:** 1 load balancer + 2 API instances in round-robin
- **Network:** bridge mode only (no host, no privileged)
- **Port:** 9999
- **Images:** public, `linux-amd64` compatible
- **Submission:** `docker-compose.yml` must be at the root of the `submission` branch

---

## ADR Convention

Every architectural or technical decision must be documented as an ADR (Architecture Decision Record).

**Location in the Obsidian vault:**
```
Rinha de Backend 2026/📔 Progresso/🗺️ Tarefas/Fase {N} - {Nome}/📝 ADR-{NNN} {Título}.md
```

**Template:**
```markdown
---
tags: [rinha-backend, adr, fase-N]
---
# ADR-NNN — Title

**Date:** YYYY-MM-DD
**Status:** ✅ Accepted | 🔄 In discussion | ❌ Rejected
**Phase:** N — Name

## Context
[Why did this decision need to be made?]

## Decision
[What was decided, directly and objectively]

## Alternatives Considered
[Comparative table of evaluated options]

## Rationale
[Why this option beat the alternatives — focus on the why]

## Consequences
[What changes in the project from this decision]
```

**ADR index:** See `Rinha de Backend 2026/🤖 Instruções para o Agente.md` in the Personal Obsidian vault.

---

## Execution Plan — 9 Tasks, 3 Phases

| Phase | Task | Description | Status |
|-------|------|-------------|--------|
| 1 — Foundation | 1.1 | Project Scaffold — Gradle, Ktor, GraalVM, Docker | 🟡 In progress |
| | 1.2 | Data Pipeline — Load & pre-process 3M reference vectors | 🔴 Pending |
| | 1.3 | Vectorization Engine — Payload → 14D float vector | 🔴 Pending |
| 2 — Detection | 2.1 | Search Index — k-NN (HNSW or brute-force) | 🔴 Pending |
| | 2.2 | Fraud Detector — fraud_score calculation | 🔴 Pending |
| | 2.3 | API Endpoints — POST /fraud-score | 🔴 Pending |
| 3 — Scale | 3.1 | Multi-Instance Setup — Nginx + 2 instances docker-compose | 🔴 Pending |
| | 3.2 | Testing Local — k6 smoke + full test suite | 🔴 Pending |
| | 3.3 | Profiling & Optimization — memory and latency tuning | 🔴 Pending |

---

## Scoring Formula (know this to make good trade-offs)

```
final_score = score_p99 + score_det       (range: -6000 to +6000)

score_p99:
  if p99 > 2000ms  → -3000 (hard cut)
  else             → 1000 × log₁₀(1000 / max(p99, 1))   [+3000 cap at p99 ≤ 1ms]

score_det:
  E = 1×FP + 3×FN + 5×HTTP_errors
  if (FP + FN + Errors) / N > 15%  → -3000 (hard cut)
  else  → 1000 × log₁₀(1/ε) − 300 × log₁₀(1+E)
```

**Key trade-off insight:** HTTP 500 errors (weight 5) are far worse than FN (weight 3) or FP (weight 1). When in doubt, return an approximation rather than an error.

---

## Reference Files (loaded at startup, never change during test)

- `references.json.gz` — 3,000,000 labeled vectors (`fraud` | `legit`)
- `mcc_risk.json` — MCC code → risk score mapping
- `normalization.json` — normalization constants

Pre-process all three at container startup (or build time). Every millisecond saved here is a millisecond off your p99.

---

## Project Structure (target)

```
rinha-de-backend-2026-vhtor-kotlin/
├── .github/
│   └── copilot-instructions.md       ← this file
├── src/main/kotlin/com/vhtor/rinha/
│   ├── Application.kt                ← server entry point
│   ├── routes/
│   │   ├── HealthRoutes.kt           ← GET /ready
│   │   └── FraudRoutes.kt            ← POST /fraud-score
│   ├── vectorization/
│   │   └── Vectorizer.kt             ← payload → float[14]
│   ├── search/
│   │   └── VectorIndex.kt            ← k-NN search over 3M vectors
│   └── fraud/
│       └── FraudDetector.kt          ← fraud_score + approved decision
├── src/main/resources/
│   ├── logback.xml
│   └── application.conf
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── Dockerfile                        ← multi-stage: GraalVM compile → debian-slim
└── docker-compose.yml                ← submission branch only
```
