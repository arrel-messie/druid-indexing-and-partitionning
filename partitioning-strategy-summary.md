# Druid Partitioning Strategy - Summary
## Transactions Datasource

**Document Version:** 1.0  
**Date:** December 10, 2025  
**Author:** zakaria.kimbembe.external@banque-france.fr  
**Status:** Draft - Pending Validation

---

## Architecture Context

### Query Processing for 7-Day Hot Data

**Nodes involved in queries:**

- **Historical Nodes**: Serve queries for **published segments** (most of the 7-day window)
  - Segments are published after `taskDuration` (typically 1 hour for Kafka ingestion)
  - Once published, segments are loaded by Historical nodes
  - Historical nodes handle the majority of queries for the 7-day hot data window

- **Middle Managers**: Serve queries for **segments currently being ingested** (last 1-6 hours)
  - Only segments that are still in the ingestion process (not yet published)
  - With `taskDuration: PT1H`, segments older than 1 hour are published → served by Historical nodes
  - Middle Managers handle queries on the most recent data (last hour) during ingestion

**For 7-day hot data queries:**
- **Historical nodes** serve ~99% of queries (segments older than 1 hour)
- **Middle Managers** serve ~1% of queries (segments in last hour, still ingesting)

**Note:** This architecture ensures that performance-critical queries on the 7-day window are primarily served by Historical nodes, which are optimized for query performance.

---

## Acceptance Criteria

### 1. Time Granularity Selection and Justification

#### `segmentGranularity`: DAY

**Decision:** `DAY` granularity for segment partitioning.

**Justification:**
- **Volume-based**: 138M transactions/day (avg), 1.38B (peak) → Daily segments of ~70.8 GB (avg), 707.8 GB (peak)
- **Query pattern alignment**: Most queries target daily or multi-day ranges
- **Segment management**: 7 segments for 7-day hot data window (manageable metadata overhead)
- **Alternative rejected (HOUR)**: 168 segments for 7 days creates too much metadata overhead

**Configuration:**
```json
{
  "granularitySpec": {
    "type": "uniform",
    "segmentGranularity": "DAY",
    "queryGranularity": "MINUTE"
  }
}
```

#### `queryGranularity`: MINUTE

**Decision:** `MINUTE` granularity for query time buckets.

**Justification:**
- Fine enough for reporting needs (fraud detection, real-time monitoring)
- Allows aggregation to hour/day for reports
- Storage impact acceptable (~470 MB/min at peak after compression)
- Alternative (SECOND) would be overkill (14 GB/sec at peak)

---

### 2. Secondary Partitioning Strategy

**Approach:** Sequential testing of partitioning types to determine optimal strategy.

#### Option A: Dynamic Partitioning (Baseline)

**Type:** `dynamic`

**When to use:** Default starting point for performance testing.

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "dynamic",
    "maxRowsPerSegment": 5000000
  }
}
```

**Characteristics:**
- Automatic partitioning based on target size
- No dimension-specific optimization
- Baseline for performance comparison

#### Option B: Hash Partitioning on account_id

**Type:** `hash`

**When to activate:** If `account_id` has high cardinality (>100K) and is frequently filtered.

**Justification:**
- `account_id` has ~320M cardinality (very high)
- Expected in ~80% of queries (to be validated)
- Hash partitioning ensures even distribution across partitions
- Queries filtering by `account_id` scan only 1-5 partitions instead of all ~138

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "hash",
    "targetPartitionSize": 1024000,
    "maxPartitionSize": 2048000,
    "partitionDimension": "account_id"
  }
}
```

**Trade-off:**
- Benefits: Queries filtering by `account_id` → 60-80% reduction in I/O
- Risk: Queries NOT filtering by `account_id` must scan all partitions

#### Option C: Hash Partitioning on PSP_id

**Type:** `hash`

**When to activate:** If `PSP_id` has high cardinality (>100K) and is frequently filtered.

**Justification:**
- Expected in ~60% of queries (to be validated)
- Alternative to `account_id` if cardinality is high
- Compare performance vs `account_id` partitioning

**Configuration:** Same as Option B, replace `partitionDimension` with `PSP_id`

#### Option D: Single Dimension Partitioning on country

**Type:** `single_dim`

**When to activate:** If `country` is frequently filtered and low cardinality benefits partitioning.

**Justification:**
- `country` has 22 values (low cardinality, clear ranges)
- Expected in ~30% of queries (to be validated)
- Good for low-cardinality, frequently-filtered dimensions
- Druid automatically optimizes ranges

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "single_dim",
    "partitionDimension": "country",
    "targetRowsPerSegment": 5000000,
    "maxRowsPerSegment": 10000000
  }
}
```

**Trade-off:**
- Benefits: Country-filtered queries scan only relevant partitions (~1-2 out of 22)
-  Risk: Queries NOT filtering by country must scan all partitions

**Decision Process:**
1. Start with `dynamic` (baseline)
2. Test `hash` on `account_id` if cardinality confirmed >100K
3. Test `hash` on `PSP_id` if high cardinality
4. Test `single_dim` on `country`
5. Select best performing strategy based on query patterns

---

### 3. Sizing Note

#### Target Segment Size

**Rows per segment:** 1M-2M rows  
**Size per segment:** 500 MB - 1 GB

**Justification:**
- Proven sweet spot in Druid for query performance
- Balances I/O efficiency with metadata overhead
- Allows parallel processing across partitions

#### Daily Segment Breakdown

| Scenario | Rows/Day | Partitions (at 1M rows) | Storage (raw) |
|----------|----------|------------------------|---------------|
| Average | 138M | ~138 | 70.8 GB |
| Peak | 1,382M | ~1,382 | 707.8 GB |

#### Compaction Strategy

**Purpose:** Consolidate small segments into optimal-sized partitions.

**Configuration:**
```json
{
  "type": "compact",
  "dataSource": "transactions",
  "taskPriority": 25,
  "maxRowsPerSegment": 1024000,
  "skipOffsetFromLatest": "PT6H",
  "tuningConfig": {
    "type": "index_parallel",
    "maxNumConcurrentSubTasks": 4,
    "maxRowsInMemory": 500000,
    "partitionsSpec": {
      "type": "hash",
      "targetPartitionSize": 1024000,
      "maxPartitionSize": 2048000,
      "partitionDimension": "account_id"
    }
  }
}
```

**Key Parameters:**
- `skipOffsetFromLatest: PT6H` - Don't compact last 6 hours (active ingestion)
- `taskPriority: 25` - Lower than ingestion (50) to avoid interference
- `partitionsSpec`: Can use different strategy than initial ingestion (allows optimization)

**Expected Outcomes:**
- Segment count reduction: 20-40%
- Query performance: 15-35% faster
- Storage: 5-15% savings from better compression

---

### 4. Primary Filter Dimensions (Ordered)

**Dimension List** (ordered by filter frequency and cardinality):

1. **`account_id`** (~320M cardinality) - **PRIMARY FILTER**
   - Highest cardinality
   - Expected in ~80% of queries (to be validated)
   - Best candidate for hash partitioning

2. **`PSP_id`** (cardinality TBD) - **PRIMARY FILTER**
   - Expected in ~60% of queries (to be validated)
   - Candidate for hash partitioning if high cardinality

3. **`access_manager_id`** (cardinality TBD) - **PRIMARY FILTER**
   - Expected in queries (frequency TBD)
   - Candidate for hash partitioning if high cardinality

4. **`country`** (22 cardinality) - **PRIMARY FILTER**
   - Expected in ~30% of queries (to be validated)
   - Best candidate for single_dim partitioning

5. **`transaction_id`** (unique) - Secondary
   - Guaranteed unique, but query pattern unknown

6. **`currency`** (1 value) - Not useful for partitioning

**Note:** Filter frequencies are assumptions requiring validation from 30 days of query logs.

**Dimension Ordering Rationale:**
- Highest cardinality dimensions first (better for hash partitioning)
- Most frequently filtered dimensions first (better query performance)
- Low cardinality dimensions can use single_dim partitioning

---

### 5. Design Review and Approval

**Review Status:** Draft - Pending Validation

**Required Actions Before Approval:**

- [ ] Execute performance testing scenarios (see below)
- [ ] Data Architecture review and sign-off

---

## Performance Scenarios (Levers)

### Testing Approach

Each scenario tests a different partitioning strategy to measure performance gains. All dimensions are automatically indexed in Druid - the difference is in how data is physically partitioned.

### Scenario 1: Dynamic Partitioning (Baseline)

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `dynamic`

**Purpose:** Establish baseline performance metrics.

**Metrics:**
- Query latency (P50, P95, P99)
- Segments scanned per query
- Data scanned (MB) per query
- Cache hit rate

**Expected:** All queries scan full daily segments. Baseline for comparison.

**Activation:** Default starting point.

---

### Scenario 2: Hash Partitioning on account_id

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `hash` on `account_id`
- Target: 1M rows per partition

**Purpose:** Measure performance gain from hash partitioning on high-cardinality dimension.

**Metrics vs Baseline:**
- Query latency improvement for account-filtered queries
- Reduction in partitions scanned (target: 1-5 instead of ~138)
- Data scanned reduction (target: 60-80% reduction)

**Expected Results:**
- Account-filtered queries: Scan 1-5 partitions → 60-80% I/O reduction
- Non-account queries: May scan all partitions → potential degradation

**Activation Criteria:**
- `account_id` cardinality confirmed >100K
- >20% latency improvement for account-filtered queries
- Trade-off acceptable for non-account queries

---

### Scenario 3: Hash Partitioning on PSP_id

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `hash` on `PSP_id`
- Target: 1M rows per partition

**Purpose:** Measure performance gain from hash partitioning on PSP_id (if high cardinality).

**Metrics vs Baseline:**
- Same as Scenario 2, but for PSP-filtered queries

**Expected Results:**
- PSP-filtered queries: Performance improvement
- Comparison with Scenario 2 to determine best dimension

**Activation Criteria:**
- `PSP_id` cardinality confirmed >100K
- >20% latency improvement for PSP-filtered queries
- Better than Scenario 2 for overall query patterns

---

### Scenario 4: Single Dimension Partitioning on country

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `single_dim` on `country`
- Target: 5M rows per segment (will be split by country)

**Purpose:** Measure performance gain/loss from single_dim partitioning on low-cardinality dimension.

**Metrics vs Baseline:**
- Query latency for country-filtered queries
- Partitions scanned (target: 1-2 instead of all)
- Performance for non-country queries (may degrade)

**Expected Results:**
- Country-filtered queries: Scan 1-2 partitions out of ~22 → better performance
- Non-country queries: May scan all partitions → potential degradation
- Should create ~22 partitions per day (one per country)

**Activation Criteria:**
- >20% latency improvement for country-filtered queries
- Net benefit (improvement for common queries > degradation for others)
- Query pattern shows significant country filtering

---

### Scenario 5: Combined Optimization (if beneficial)

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Primary Partitioning: Best from Scenarios 2-4
- Note: Druid uses one partitioning strategy per ingestion, but compaction can use different strategy

**Purpose:** Measure combined optimization impact (if applicable).

**Note:** Druid typically uses one partitioning strategy per ingestion. However, compaction can use a different strategy, potentially combining benefits.

**Activation Criteria:**
- Best overall performance across query patterns
- Operational complexity acceptable
- Trade-offs documented

---

## Decision Matrix

| Scenario | Partitioning | Best For | Trade-off |
|----------|--------------|----------|-----------|
| 1 (Baseline) | `dynamic` | General-purpose, diverse queries | No optimization |
| 2 | `hash` on `account_id` | Queries filtering by account | Non-account queries slower |
| 3 | `hash` on `PSP_id` | Queries filtering by PSP | Non-PSP queries slower |
| 4 | `single_dim` on `country` | Queries filtering by country | Non-country queries slower |

**Selection Criteria:**
- Choose scenario with >20% latency improvement for most common queries
- Acceptable trade-off for less common queries
- Operational complexity manageable

---

## Performance Metrics

For each scenario, measure:

| Metric | Target | Description |
|--------|--------|-------------|
| **Query Latency P95** | < 500ms | 95th percentile response time |
| **Segments Scanned** | Minimize | Number of segments opened |
| **Partitions Scanned** | Minimize | Number of partitions read |
| **Data Scanned** | Minimize | MB of data read per query |
| **Cache Hit Rate** | > 60% | Percentage from cache |
| **Query Success Rate** | > 99% | Percentage successful |

---

## Validation Requirements

**Before Production:**

1. Execute all performance scenarios
2. Document results and select optimal strategy
3. Data Architecture approval

---

**End of Document**

