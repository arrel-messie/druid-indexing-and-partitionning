# Druid Partitioning Strategy Design Document
## Transactions Datasource

**Document Version:** 1.0  
**Date:** [Date]  
**Author:** [Author Name]  
**Status:** Draft - Based on Assumptions

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Context and Objectives](#context-and-objectives)
3. [Data Characteristics](#data-characteristics)
4. [Time Granularity Strategy](#time-granularity-strategy)
5. [Dimension Partitioning Strategy](#dimension-partitioning-strategy)
6. [Dimension List and Ordering](#dimension-list-and-ordering)
7. [Sizing and Capacity Planning](#sizing-and-capacity-planning)
8. [Compaction Strategy](#compaction-strategy)
9. [Query Performance Considerations](#query-performance-considerations)
10. [Validation and Approval](#validation-and-approval)

---

## Executive Summary

This document defines the partitioning and time granularity strategy for the transactions datasource in Apache Druid. The strategy is designed to ensure optimal query performance at expected transaction volumes while maintaining efficient data ingestion and storage.

**Key Decisions:**
- Segment granularity: `HOUR` (assumed based on high-volume transaction processing)
- Query granularity: `MINUTE` (assumed for detailed transaction analysis)
- Secondary partitioning: `HASH` partitioning on `account_id` dimension
- Target segment size: 500 MB (within recommended 300-700 MB range)

**Note:** This document is based on assumptions and should be validated with actual data characteristics and business requirements.

---

## Context and Objectives

### Business Context

The transactions datasource requires a partitioning strategy that supports:
- High-volume transaction data ingestion (assumed: millions of transactions per day)
- Efficient query performance across various time windows
- Scalability as transaction volumes grow
- Cost-effective storage and compute utilization

### Objectives

- Define optimal time granularity for segment and query processing
- Establish dimension-based partitioning strategy for query optimization
- Ensure segments are properly sized for efficient query execution
- Document compaction strategy for long-term data management

### Scope

**In Scope:**
- Partitioning strategy definition
- Time granularity configuration
- Dimension ordering and filtering strategy
- Sizing and capacity planning
- Compaction implementation approach

**Out of Scope:**
- Data source implementation details
- Ingestion pipeline configuration
- Query optimization beyond partitioning
- Infrastructure provisioning

---

## Data Characteristics

### Expected Data Volume

**Assumptions:**
- Settlement data transaction system
- High-volume, real-time transaction processing
- Need for both real-time monitoring and historical analysis

| Metric                  | Average     | Peak          | Notes                                        |
| ----------------------- | ----------- | ------------- | -------------------------------------------- |
| Transactions per second | 1,600       | 16,000        | Base pour tous les calculs                   |
| Transactions per hour   | 5,760,000   | 57,600,000    | Calculé à partir de tx/sec × 3,600 sec/heure |
| Transactions per day    | 138,240,000 | 1,382,400,000 | Calculé à partir de tx/sec × 86,400 sec/jour |
| Average row size        | 512 bytes   | 512 bytes     | Inclut dimensions et metrics                 |
| Daily data volume       | ~70.8 GB    | ~707.8 GB     | Transactions × 512 bytes                     |
| Retention period        | 10 years    | 10 years      | Total ~258 TB avg, ~2.58 PB peak             |


### Query Patterns

**Assumptions:**
- Real-time monitoring for fraud detection and system health
- Daily reporting for business intelligence
- Historical analysis for compliance and analytics
- Ad-hoc queries for investigation and exploration

| Query Type | Frequency | Time Window | Typical Dimensions |
|------------|-----------|-------------|-------------------|
| Real-time monitoring | High | Last 1-24 hours | account_id, transaction_type, status |
| Daily reporting | Medium | Last 7-30 days | account_id, merchant_id, region |
| Historical analysis | Low | Months to years | account_id, transaction_type, region |
| Ad-hoc exploration | Variable | Variable | Various combinations |

### Data Distribution

**Assumptions:**
- **Time distribution:** Relatively uniform distribution throughout the day with peak hours during business hours (9 AM - 5 PM)
- **Dimension cardinality:** 
  - account_id: High cardinality (assumed 1M+ unique accounts)
  - transaction_type: Low cardinality (assumed 10-20 types)
  - merchant_id: Medium cardinality (assumed 10K-50K merchants)
  - region: Low cardinality (assumed 10-50 regions)
- **Hot vs. cold data:** Recent data (last 7 days) accessed frequently, older data accessed less frequently but still needed for compliance

---

## Time Granularity Strategy


### Segment Granularity

**Selected Value:** `DAY`

**Justification:**

Based on transaction volume of **1,600 transactions/sec (average) and 16,000/sec (peak)**:

1. **Optimal Segment Size:**

    * Average day: 138,240,000 transactions × 512 bytes ≈ **70.8 GB per segment**
    * Peak day: 1,382,400,000 transactions × 512 bytes ≈ **707 GB per segment**
    * Hourly segments would create **2.8 GB (average) to 29.5 GB (peak)** per hour, resulting in **too many segments** for 10-year retention (~87,600 segments)
    * Daily segmentation keeps segment count manageable (~3,650 segments for 10 years)

2. **Query Performance:**

    * Most queries target day, week, or month ranges
    * Daily segments provide good partition pruning while keeping the number of segments reasonable
    * Hourly segments would improve fine-grained pruning but increase overhead massively

3. **Ingestion Efficiency:**

    * Daily ingestion batches align with high-volume transactional data
    * Peak load handled via `maxRowsPerSegment` and `maxPartitionSize` settings
    * Compaction can be used to adjust segment sizes if needed

4. **Maintenance Balance:**

    * Daily segments maintain a **manageable number of segments** for 10 years
    * Avoids segment explosion (hourly would create ~87,600, minute-level > 5M segments)
    * Segments remain within acceptable size for cluster capacity

**Analysis:**

| Granularity Option | Pros                                                                      | Cons                                                                | Recommendation |
| ------------------ | ------------------------------------------------------------------------- | ------------------------------------------------------------------- | -------------- |
| MINUTE             | Very fine-grained, excellent for real-time                                | Too many segments (>5M over 10 years), extreme overhead             | No             |
| HOUR               | Good for query pruning                                                    | Segment count too high (~87,600 for 10 years), operational overhead | No             |
| DAY                | Balanced segment size (~71 GB avg, 707 GB peak), reasonable segment count | Large segments, requires careful tuning for compaction              | **Yes**        |
| MONTH              | Minimal segments                                                          | Extremely large segments, inefficient queries                       | No             |

**Decision Rationale:**

Daily segmentation provides the optimal balance between **segment count, query performance, and operational overhead** for the given high-volume transactions and 10-year retention. Using daily segments ensures the system remains manageable, allows compaction to optimize segment sizes, and supports efficient query execution even at peak loads.


### Query Granularity

**Selected Value:** `MINUTE`

**Justification:**

For transaction analysis, **minute-level granularity** is necessary despite very high transaction volume:

1. **Business Requirements:**

    * Fraud detection requires **minute-level precision** to identify suspicious patterns quickly
    * Real-time monitoring dashboards need **fine-grained time buckets**
    * Detailed transaction timing analysis is needed for investigations and auditing

2. **Storage Considerations:**

    * Minute-level granularity produces **manageable number of segments per day** with daily segment granularity (`138M tx/day → ~2.8M rows/min`)
    * Allows aggregation to coarser granularities (hourly, daily) for reporting
    * Balances precision with storage cost — row sizes of 512 bytes × 2.8M rows/min ≈ 1.4 GB/minute → feasible with a medium-to-large cluster

3. **Query Flexibility:**

    * Supports **detailed minute-level analysis** and aggregated hourly/daily reporting
    * Enables fine-grained time-series analysis without losing precision
    * Works well with `DAY` segment granularity; queries can still target minute-level intervals




**Analysis:**

| Granularity Option | Query Precision     | Storage Impact                           | Performance Impact                      | Recommendation |
| ------------------ | ------------------- | ---------------------------------------- | --------------------------------------- | -------------- |
| SECOND             | Very high precision | Extremely high storage (~14 GB/sec peak) | Slow queries, heavy ingestion           | No             |
| MINUTE             | High precision      | Moderate (~1.4 GB/min peak)              | Good performance                        | **Yes**        |
| HOUR               | Lower precision     | Lower storage                            | Faster queries, but insufficient detail | No             |
| DAY                | Very low precision  | Minimal storage                          | Best performance                        | No             |

---

**Decision Rationale:**

Using **minute-level query granularity** allows the system to satisfy business requirements for **fraud detection and real-time monitoring**, while remaining feasible in terms of storage and performance. Aggregations can still be done at hourly or daily levels for reporting, and the data can be ingested into **daily segments** to maintain operational manageability.



## Dimension Partitioning Strategy

### Primary Filter Dimensions

Based on expected query patterns and new transaction volumes:

1. **account_id** – Most queries filter by specific accounts or ranges; **high cardinality (~1M+)**
2. **transaction_type** – Frequently filters by category; **low cardinality (15–20)**
3. **status** – Filters successful vs. failed transactions; **very low cardinality (3–5)**
4. **timestamp** – Always used in time-range queries; **implicit primary filter**
5. **region** – Data is partitioned across 3 geographic regions; **moderate cardinality (3)**

**Query Pattern Analysis (updated):**

| Dimension        | Filter Frequency | Typical Filter Values                 | Cardinality   |
| ---------------- | ---------------- | ------------------------------------- | ------------- |
| account_id       | 80%              | Specific accounts, ranges             | 1,000,000+    |
| transaction_type | 60%              | 'payment', 'refund', 'transfer', etc. | 15-20         |
| status           | 50%              | 'success', 'failed', 'pending'        | 3-5           |
| merchant_id      | 40%              | Specific merchant IDs                 | 10,000-50,000 |
| region           | 30%              | 3 geographic regions                  | 3             |

---

### Secondary Partitioning Strategy

**Strategy Type:** `HASH`

**Selected Dimensions for Partitioning:**

1. **account_id** – Primary partitioning dimension

    * High cardinality ensures even hash distribution
    * Frequently filtered -> enables efficient pruning

2. **transaction_type** – Optional secondary partitioning

    * Low cardinality -> combines well with account_id
    * Improves parallelism for queries filtered by type

3. **region** – Optional tertiary partitioning

    * Since there are only 3 regions, hash partitioning ensures **balanced storage across regions**
    * Avoids hot-spotting

**Partitioning Configuration (updated):**

```json
{
  "partitionsSpec": {
    "type": "hash",
    "targetPartitionSize": 5000000,
    "maxPartitionSize": 10000000,
    "partitionDimension": "account_id",
    "assumeGrouped": false
  }
}
```

**Justification (updated):**

1. **Query Performance Benefits:**

    * Hash partitioning on **account_id** reduces segment scans by 60–80% for account-filtered queries
    * Secondary partitioning by **transaction_type** improves parallelism
    * **Region awareness** ensures queries per region are balanced

2. **Data Distribution:**

    * Evenly distributed partitions -> no hot-spotting
    * Target partition size of 5M rows ensures manageable segment sizes even at peak (16,000 tx/sec -> ~29.5M rows/hour peak → ~6 partitions/hour)

3. **Cardinality Considerations:**

    * High cardinality of account_id ensures good hash distribution
    * Low cardinality dimensions (transaction_type, region) improve pruning without creating too many partitions

4. **Balance & Maintenance:**

    * Max partition size 10M rows prevents oversized segments
    * Daily segments with hash partitions result in ~14 segments/day per region (average)
    * Peak load may create ~60 segments/day/region, still manageable

**Expected Impact (updated):**

| Metric               | Expected Impact                                             |
| -------------------- | ----------------------------------------------------------- |
| Query Performance    | 40–60% reduction in latency for account-filtered queries    |
| Segment Distribution | Balanced across cluster; 14–60 segments/day/region          |
| Storage Efficiency   | Optimal segment size (5–10M rows) with minimal overhead     |
| Scalability          | Handles peak of 16,000 tx/sec across 3 regions without skew |



### Clustering Strategy

**Clustering Dimensions:** account_id, transaction_type

**Clustering Order:** 
1. account_id (primary)
2. transaction_type (secondary)

**Rationale:**

Clustering by account_id ensures that transactions for the same account are co-located within segments, improving query performance for account-specific queries. Secondary clustering by transaction_type further optimizes queries that filter by both dimensions.

---


## Dimension List and Ordering

### Complete Dimension List

Dimensions are ordered by query frequency and filtering importance. Primary filter dimensions appear first to optimize query performance.

| Order | Dimension Name   | Type   | Cardinality   | Primary Filter | Notes                                 |
| ----- | ---------------- | ------ | ------------- | -------------- | ------------------------------------- |
| 1     | account_id       | STRING | 1,000,000+    | Yes            | Most frequently filtered              |
| 2     | transaction_type | STRING | 15-20         | Yes            | High filter frequency                 |
| 3     | status           | STRING | 3-5           | Yes            | Critical for filtering                |
| 4     | merchant_id      | STRING | 10,000-50,000 | No             | Frequently used in joins              |
| 5     | region           | STRING | 3             | No             | Geographic filtering across 3 regions |
| 6     | currency         | STRING | 10-20         | No             | Multi-currency support                |
| 7     | payment_method   | STRING | 5-10          | No             | Payment type analysis                 |
| 8     | transaction_id   | STRING | Very High     | No             | Unique identifier                     |
| 9     | user_id          | STRING | 500,000+      | No             | User-level analysis                   |
| 10    | device_type      | STRING | 5-10          | No             | Device analytics                      |

---

### Dimension Details

#### Primary Filter Dimensions

**account_id**

* **Type:** STRING
* **Cardinality:** 1,000,000+ unique accounts
* **Usage:** Account-based queries, balance checks, transaction history
* **Filter Patterns:** Exact match, ranges, pattern matching (`LIKE 'ACC%'`)
* **Indexing:** High-priority indexing, used for hash partitioning

**transaction_type**

* **Type:** STRING
* **Cardinality:** 15–20 unique types
* **Usage:** Categorization of transactions
* **Filter Patterns:** Exact match, multiple values (`IN (...)`)
* **Indexing:** Standard indexing, used for secondary clustering

**status**

* **Type:** STRING
* **Cardinality:** 3–5 statuses
* **Usage:** Transaction status filtering (success, failed, pending)
* **Filter Patterns:** Exact match, exclusions
* **Indexing:** Standard indexing

#### Secondary Dimensions

**merchant_id**

* **Type:** STRING
* **Cardinality:** 10,000–50,000 merchants
* **Usage:** Merchant-specific analysis and reporting

**region**

* **Type:** STRING
* **Cardinality:** 3 geographic regions
* **Usage:** Regional analysis, distributed query performance
* **Filter Patterns:** Exact match, multiple values

---

### Ordering Rationale

* **Query frequency:** account_id appears in ~80% of queries → top priority
* **Selectivity:** High-cardinality dimensions reduce scan size effectively
* **Query performance:** Early indexing of primary filters accelerates query execution
* **Business importance:** account_id and transaction_type are critical for reporting and fraud detection

---

## Sizing and Capacity Planning

### Segment Sizing

**Target Segment Size:** 500 MB (recommended 300–700 MB range)

**Rows per Segment Estimation (updated):**

| Time Period  | Segment Granularity | Estimated Rows | Estimated Size | Notes                     |
| ------------ | ------------------- | -------------- | -------------- | ------------------------- |
| Peak hour    | HOUR                | 57,600,000     | ~29.5 GB       | 16,000 tx/sec × 3,600 sec |
| Average hour | HOUR                | 5,760,000      | ~2.95 GB       | 1,600 tx/sec × 3,600 sec  |
| Peak day     | DAY                 | 1,382,400,000  | ~707 GB        | Peak day volume           |
| Average day  | DAY                 | 138,240,000    | ~70.8 GB       | Average day volume        |

---

### Calculation Methodology (updated)

**Row Size Estimation:**

```
Average row size = Dimensions (400 bytes) + Metrics (100 bytes) + Overhead (12 bytes)
                 ≈ 512 bytes per row
```

**Rows per Segment:**

```
Target segment size = 500 MB = 524,288,000 bytes
Rows per segment = 524,288,000 / 512
                 ≈ 1,024,000 rows (~1M rows)
```

**Segments per Time Period (updated):**

```
Average hour: 5,760,000 rows
Segments per hour = 5,760,000 / 1,024,000 ≈ 5.6 → 6 segments/hour

Peak hour: 57,600,000 rows
Segments per hour = 57,600,000 / 1,024,000 ≈ 56 segments/hour
```

**Note:** For daily segments (recommended), peak day produces ~138 segments/day, manageable with compaction.

---

### Capacity Planning

**Assumptions:**

* Growth rate: 20% per year
* Replication factor: 2
* Compression ratio: 3:1

| Metric                   | Current      | 6 Months    | 12 Months  | Notes                       |
| ------------------------ | ------------ | ----------- | ---------- | --------------------------- |
| Daily ingestion          | 138M rows    | 165.6M rows | 198M rows  | 20% annual growth           |
| Total segments           | 138/day      | 166/day     | 198/day    | Daily segments (~1/day avg) |
| Storage required         | 70.8 GB/day  | 85 GB/day   | 102 GB/day | Raw data                    |
| Storage with replication | 141.6 GB/day | 170 GB/day  | 204 GB/day | 2x replication              |
| Storage with compression | 47.2 GB/day  | 56.7 GB/day | 68 GB/day  | 3:1 compression             |
| Annual storage           | ~17 TB       | ~20.7 TB    | ~25 TB     | Compressed, replicated      |
| Query load               | 1,000/hour   | 1,200/hour  | 1,400/hour | Growing usage               |


### Performance Targets (updated)

* **Query latency (P95):** < 500 ms for account-filtered queries (last 24h)
* **Query latency (P99):** < 1,000 ms for account-filtered queries (last 24h)
* **Query latency (P95):** < 2,000 ms for historical queries (30+ days)
* **Ingestion throughput:** 16,000 events/sec sustained peak
* **Segment scan efficiency:** > 80%

---



## Compaction Strategy 

### Compaction Objectives

* Target ~**500 MB** per final segment (≈ **1,024,000 rows** @512 B/row).
* Consolidate small/fragmented segments created by high ingestion rate.
* Keep data queryable and fresh (skip recent data).
* Minimize compaction impact on cluster (controlled concurrency / memory).

### Compaction Configuration

**Compaction Granularity:** `DAY` (matches chosen segmentGranularity)
**Skip recent data:** `PT6H` (do not compact last 6 hours) — safe with high-volume streaming.
**Compaction cadence:** tiered (see below).

**Example compaction spec:**

```json
{
  "dataSource": "transactions",
  "taskPriority": 25,
  "inputSegmentSizeBytes": 536870912,
  "maxRowsPerSegment": 1024000,
  "skipOffsetFromLatest": "PT6H",
  "tuningConfig": {
    "maxNumConcurrentSubTasks": 4,
    "maxRowsInMemory": 500000,
    "partitionsSpec": {
      "type": "hash",
      "targetPartitionSize": 1024000,
      "maxPartitionSize": 2048000,
      "partitionDimension": "account_id",
      "assumeGrouped": false
    }
  }
}
```

### Compaction Schedule & Scope

* **Initial short-term compaction:** rolling every **6 hours**, compaction window targets segments **6–24 hours old**. Purpose: quickly consolidate newly closed segments without touching hottest data.
* **Ongoing near-term compaction:** **daily** job (night window, low-traffic) compacting data **1–7 days old** to consolidate into target-sized daily-partitioned files.
* **Long-term compaction:** **weekly** for data **>7 days and ≤ 30 days**, **monthly** for data **>30 days**, and **quarterly** (or annually) for very old data to coalesce historic segments and reduce segment count for 10-year retention. Run long-term jobs in low-usage windows.
* **Retention safety:** keep originals for 24–72h before deletion (depends on recovery/RPO policy).

### Why these choices

* With **138M avg rows/day**, a 1M-rows target → **~138 segments/day** (avg) across dataset; compaction prevents explosion of even smaller segments produced by ingestion bursts.
* `skipOffsetFromLatest = PT6H` protects active ingestion windows and reduces risk of duplicate/partial compaction.
* `maxNumConcurrentSubTasks = 4` balances speed vs cluster load; scale up if you have spare capacity.

### Expected Impact & SLAs

* **Segment count reduction:** initial compaction rounds aim to reduce very small segments and stabilize distribution; long-term compaction reduces segment count by **20–40%** for older data.
* **Query performance:** expect **15–35%** improvement for multi-day queries after compaction (fewer, better-packed segments).
* **Storage:** small gain from compaction due to better compression; expect **5–15%** improvement.
* **Compaction reliability targets:** success rate >99%; average compaction run for a 1–day window ideally < 1 hour (depends on cluster size and I/O).

### Operational notes

* Monitor compaction task queue — avoid backlog.
* Prefer running heavy compaction on dedicated worker nodes or during off-peak windows.
* Use compaction logs/metrics to tune `maxRowsInMemory` and concurrency.

---

## Query Performance Considerations (revised for DAY segments + MINUTE queries)

### Partition pruning & per-segment behavior

* **SegmentGranularity = DAY** → queries target at most one (or few) day segments per region for recent data.

    * Example: query for last hour ⇒ Druid will read the current day’s segment(s) (1 day segment per partition).
* **Hash partitioning on `account_id`** allows pruning *within* a daily segment: only the partitions (shards) containing the hashed account_id are scanned. That reduces IO dramatically even though the segment covers a full day.

### Trade-offs (DAY segments + MINUTE queryGranularity)

* **Pros**

    * Manageable segment count for 10-year retention (~3,650 days × partitions).
    * Easier compaction/maintenance and fewer segment files to track.
    * Minute-level queryGranularity still supported at query time — you can request minute buckets during aggregation; pruning and vectorized scans handle granularity efficiently.
* **Cons**

    * Day-sized segments are large; queries touching a small recent window still read larger segment files from disk (but partition pruning mitigates reading whole segment).
    * Need careful partition sizing (1M rows target) so partitions remain reasonably small inside the daily segment.

### Practical numbers (with current load)

* **Average hour:** 5,760,000 rows → ≈ 5.6 partitions (1M rows target) ⇒ expect **~6 partitions/hour** created by ingestion before compaction.
* **Peak hour:** 57,600,000 rows → ≈ 56 partitions/hour.
* **Daily average partitions:** ~138 partitions/day (avg) → *per region* divide further if you pre-split by region (recommended).

### Time-based queries / examples

* **Last hour (minute-level aggregation):** Druid reads the current day’s partitions that intersect the hour. With hash partitioning on `account_id` and a predicate on account_id, only a small fraction of partitions are scanned.
* **Last day:** reads all daily partitions for that day (≈138 partitions — still reasonable if queries are parallelized).
* **30+ days historical:** compaction reduces number of partitions and improves scan locality for historical queries.

### Benchmarks (revised expectations)

| Query Type                       | Expected P95 (target) | Notes                                     |
| -------------------------------- | --------------------: | ----------------------------------------- |
| Account query (last 24h)         |              < 500 ms | With partition pruning + segment cache    |
| Account + type (7d)              |             < 1500 ms | Depends on nodes/parallelism              |
| Historical (30d)                 |        < 2000–6000 ms | Heavier, improved by long-term compaction |
| Real-time monitoring (last hour) |              < 500 ms | Minute aggregation + caching helps        |

### Monitoring & Tuning (essential)

* Track: query latency (P50/P95/P99), segment scan efficiency, compaction task success, compaction duration, ingestion lag, segment sizes distribution.
* Tune levers:

    * `targetPartitionSize` and `maxRowsPerSegment` — reduce if query latency suffers, increase if too many tiny partitions.
    * `maxNumConcurrentSubTasks`, `maxRowsInMemory` — adjust for compaction throughput vs memory.
    * Segment cache and segment replication — to reduce repeated disk reads for hot segments.

---

## Validation checklist (actions before rollout)

* Verify cluster I/O and worker capacity for compaction concurrency (test with a representative day).
* Run a canary compaction on a short interval (e.g., 1–2 days) and measure compaction time / query impact.
* Confirm `skipOffsetFromLatest` semantics with your streaming supervisor to avoid races.
* Validate partitionDimension hashing distribution (no hot shards per region).
* Adjust compaction cadence if compaction tasks start to backlog.

---

Si tu veux, je peux maintenant :

* générer la **spec Druid complète** (ingestion + compaction + supervisor) prête à coller dans l’API, avec `DAY` segments, partitioning `account_id`, et les paramètres ci-dessus ; ou
* créer un **plan d’exécution** (canary steps + tests + monitoring queries) pour déployer la compaction sans risque.

Que préfères-tu ?


### Approval Status

| Reviewer | Role | Date | Status | Comments |
|----------|------|------|--------|----------|
| [Name] | Data Architect | [Date] | Pending | [Comments] |
| [Name] | Platform Engineer | [Date] | Pending | [Comments] |
| [Name] | Business Analyst | [Date] | Pending | [Comments] |

### Sign-off

**Data Architecture Approval:**

- **Approved by:** [Name]
- **Title:** [Title]
- **Date:** [Date]
- **Signature:** [Signature/Approval]

---

## Appendices

### Appendix A: Configuration Examples

**Complete Ingestion Spec Example:**


---

## 1) Streaming ingestion — Kafka Supervisor (transactions flink output topic)

```json
{
  "type": "kafka",
  "dataSchema": {
    "dataSource": "transactions",
    "timestampSpec": {
      "column": "timestamp",
      "format": "iso"
    },
    "dimensionsSpec": {
      "dimensions": [
        "account_id",
        "transaction_type",
        "status",
        "merchant_id",
        "region",
        "currency",
        "payment_method",
        "transaction_id",
        "user_id",
        "device_type"
      ]
    },
    "metricsSpec": [],
    "granularitySpec": {
      "type": "uniform",
      "segmentGranularity": "DAY",
      "queryGranularity": "MINUTE",
      "rollup": false
    }
  },
  "ioConfig": {
    "topic": "transactions",
    "consumerProperties": {
      "bootstrap.servers": "<kafka-bootstrap:9092>"
    },
    "taskCount": 4,
    "replicas": 1,
    "taskDuration": "PT1H"
  },
  "tuningConfig": {
    "type": "kafka",
    "maxRowsPerSegment": 1024000,
    "maxRowsInMemory": 500000,
    "maxNumConcurrentSubTasks": 4,
    "skipOffsetFromLatest": "PT6H",
    "intermediatePersistPeriod": "PT5M",
    "handoffConditionTimeout": 0
  }
}
```

Notes:

* `taskCount` → multiple tasks (adjust to Kafka partitions).
* `taskDuration` controls segment creation windows for supervisor tasks; with DAY segments the supervisor still rolls/hands off segments per its internals — leave `taskDuration` as needed.

---

## 2) Compaction task (indexing task — run via /druid/indexer/v1/task)

```json
{
  "type": "compact",
  "dataSource": "transactions",
  "taskPriority": 25,
  "inputSegmentSizeBytes": 536870912,
  "maxRowsPerSegment": 1024000,
  "skipOffsetFromLatest": "PT6H",
  "tuningConfig": {
    "maxNumConcurrentSubTasks": 4,
    "maxRowsInMemory": 500000,
    "type": "index_parallel",
    "partitionsSpec": {
      "type": "hash",
      "targetPartitionSize": 1024000,
      "maxPartitionSize": 2048000,
      "partitionDimension": "account_id",
      "assumeGrouped": false
    }
  }
}
```

Usage:

* POST this JSON to `POST /druid/indexer/v1/task` to start a compaction job for the whole datasource.
* For controlled compaction windows, add `"interval": "YYYY-MM-DD/YYYY-MM-DD"` to limit scope, or run per-day intervals in a scheduler.

---



### Appendix B: Reference Documentation

- Apache Druid Partitioning Documentation
- Segment Granularity Best Practices
- Compaction Strategy Guide
- Performance Tuning Guide

### Appendix C: Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | [Date] | [Name] | Initial design document with assumptions |

---

**Document End**

**Note:** This document contains assumptions that must be validated with actual business requirements and data characteristics before final approval and implementation.

