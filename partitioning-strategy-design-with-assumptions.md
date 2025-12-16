# Druid Partitioning Strategy Design Document
## Transactions Datasource

**Document Version:** 3.0  
**Date:** December 10, 2025  
**Author:** zakaria.kimbembe.external@banque-france.fr  
**Status:** Draft - Pending Validation

---

## Quick Reference

```
DECISION SUMMARY
─────────────────────────────────────────────────────
Focus Area              : Performance (7-day hot data)
Segment Granularity     : DAY (to be validated)
Query Granularity       : MINUTE
Partitioning Approach   : Sequential Testing (Dynamic → Hash → Single_dim)
Target Segment Size     : 500 MB - 1 GB (~1M-2M rows)

KEY ASSUMPTIONS (REQUIRE VALIDATION)
• Transaction volume: 1,600 tx/sec avg, 16,000 tx/sec peak
• Hot data window: 7 days (performance-critical)
• Dimensions: account_id (320M), country (22), currency (1), PSP_id, access_manager_id
• Query pattern: Synchronous reporting queries (to be validated)
```

---

## Executive Summary (User Story Acceptance Criteria)

This section directly addresses the user story acceptance criteria:

### ✅ 1. Time Granularity Selection and Justification

**`segmentGranularity`: DAY**
- **Justification**: 
  - Aligns with daily data collection pattern (138M transactions/day avg, 1.38B peak)
  - 7 segments for 7-day hot data window (manageable metadata overhead)
  - Each daily segment (~70.8 GB avg, 707.8 GB peak) can be split into partitions
  - Most queries target daily or multi-day ranges
- **Alternative considered**: HOUR (168 segments for 7 days - too many to manage)

**`queryGranularity`: MINUTE**
- **Justification**:
  - Fine enough for reporting needs
  - Allows aggregation to hour/day as needed
  - Storage impact acceptable (~470 MB/min at peak after compression)

### ✅ 2. Secondary Partitioning Strategy

**Recommended Approach**: Sequential testing of partitioning types:

1. **Baseline**: `dynamic` partitioning (automatic based on size)
2. **Option A**: `hash` partitioning on `account_id` (if cardinality >100K confirmed)
   - Even distribution across partitions
   - Benefits queries filtering by account_id
3. **Option B**: `single_dim` partitioning on `country` (22 values)
   - Good for low-cardinality, frequently-filtered dimension
   - Automatic range optimization by Druid

**Decision**: Performance testing will determine optimal strategy (see Performance Scenarios section).

### ✅ 3. Sizing Note

**Target Segment Size:**
- **Rows per segment**: 1M-2M rows (~500 MB - 1 GB)
- **Rationale**: Proven sweet spot in Druid balancing I/O efficiency with metadata overhead

**Daily Segment Breakdown:**
- Average day: 138M rows → ~138 partitions at 1M rows each
- Peak day: 1,382M rows → ~1,382 partitions at 1M rows each

**Compaction Strategy:**
- Consolidate small segments to target size (1M rows, ~500 MB)
- Maintain or optimize partitioning strategy during compaction
- Skip last 6 hours to avoid conflicts with active ingestion
- See [Compaction Strategy](#compaction-strategy) section for full details

### ✅ 4. Primary Filter Dimensions (Ordered)

**Dimension List (ordered by filter frequency and cardinality):**

1. **`account_id`** (~320M cardinality) - **PRIMARY FILTER**
   - Highest cardinality, expected in ~80% of queries
   - Best candidate for hash partitioning

2. **`PSP_id`** (cardinality TBD) - **PRIMARY FILTER**
   - Expected in ~60% of queries (to be validated)
   - Candidate for hash partitioning if high cardinality

3. **`access_manager_id`** (cardinality TBD) - **PRIMARY FILTER**
   - Expected in queries (frequency TBD)
   - Candidate for hash partitioning if high cardinality

4. **`country`** (22 cardinality) - **PRIMARY FILTER**
   - Expected in ~30% of queries
   - Best candidate for single_dim partitioning

5. **`transaction_id`** (unique) - Secondary
   - Guaranteed unique, but query pattern unknown

6. **`currency`** (1 value) - Not useful for partitioning

**Note**: Filter frequencies are assumptions requiring validation from 30 days of query logs.

### ✅ 5. Design Review and Approval

**Review Status**: Draft - Pending Validation

**Required Actions Before Approval:**
- [ ] Validate dimension cardinalities from production sample
- [ ] Analyze 30 days of query logs to confirm filter frequencies
- [ ] Execute performance testing scenarios
- [ ] Data Architecture review and sign-off

**Approval Section**: See [Validation Plan](#validation-plan) for review workflow.

---

## Table of Contents

1. [Context and Objectives](#context-and-objectives)
2. [Data Characteristics](#data-characteristics)
3. [Partitioning Strategy](#partitioning-strategy)
4. [Dimension Design](#dimension-design)
5. [Performance Scenarios](#performance-scenarios)
6. [Compaction Strategy](#compaction-strategy)
7. [Validation Plan](#validation-plan)

---

## Context and Objectives

### Business Context

The transactions datasource supports a high-volume settlement data transaction system with synchronous query requirements:

- **Query Pattern**: Synchronous reporting queries (no real-time streaming)
- **Hot Data Window**: 7 days (performance-critical)
- **Data Access**: Queries primarily target recent data (last 7 days)
- **Availability**: 24/7/365 availability required

### Objectives

**Primary Objective: Performance**

The main driver for this partitioning strategy is **query performance** for the 7-day hot data window. All design decisions prioritize:

1. **Query Latency**: Sub-second response times for queries on recent data (P95 < 500ms)
2. **Segment Scan Efficiency**: Minimize the number of segments scanned per query
3. **Partition Pruning**: Optimize partition selection based on query filters

**Secondary Considerations:**
- Segment management overhead (avoid too many small segments)
- Storage efficiency (avoid segments that are too large)

### Scope

**In Scope:**
- Time granularity selection (DAY/HOUR)
- Secondary partitioning strategy (DYNAMIC/HASH/SINGLE_DIM)
- Dimension ordering for query optimization
- Performance scenarios and testing approach

**Out of Scope:**
- Long-term storage planning (beyond 7 days)
- Historical data optimization
- Infrastructure provisioning details
- Compaction implementation details (separate user story)

---

## Data Characteristics

### Volume Projections (7-Day Hot Data Window)

Focusing on the performance-critical 7-day window:

| Metric | Average | Peak |
|--------|---------|------|
| **Transactions/day** | 138,240,000 | 1,382,400,000 |
| **Daily data volume** | 70.8 GB | 707.8 GB |
| **7-day total (avg)** | 495.6 GB | 4,954.6 GB |
| **7-day total (peak)** | ~5 TB | ~5 TB |

**Calculation basis:**
- 512 bytes per row (400B dimensions + 100B metrics + 12B overhead)
- Average: 1,600 transactions/sec
- Peak: 16,000 transactions/sec

**Note:** These volumes drive the performance requirements. The 7-day window is what matters for query performance optimization.

### Query Patterns (To Be Validated)

**Assumed patterns (require validation from actual query logs):**
- Synchronous reporting queries
- Primary filters: account_id, country, PSP_id, access_manager_id
- Time range: Primarily last 7 days
- Query frequency: To be determined

**Action Required:** Export and analyze 30 days of query logs to validate:
- Most common filter dimensions
- Time range patterns
- Query frequency and patterns

---

## Partitioning Strategy

### Understanding Druid Partitioning

**Important:** In Druid, all dimensions are automatically indexed with bitmap indexes. There is no separate "indexation" step to activate. The partitioning strategy determines how data is physically organized within time-based segments.

**Partitioning in Druid works as follows:**
1. **Temporal Partitioning (Mandatory)**: Data is first partitioned by time using `segmentGranularity`
2. **Secondary Partitioning (Optional)**: Within each time segment, data can be further partitioned by dimension using:
   - `dynamic`: Automatic partitioning based on target size (default)
   - `hash`: Hash-based partitioning on a specific dimension
   - `single_dim`: Single-dimension partitioning with dynamic ranges

### Approach: Sequential Testing by Partitioning Type

The partitioning strategy follows a **sequential testing approach** where each partitioning type is tested independently:

```
Scenario 1: Dynamic Partitioning (Baseline)
    ↓
Scenario 2: Hash Partitioning on account_id
    ↓
Scenario 3: Hash Partitioning on PSP_id
    ↓
Scenario 4: Single_dim Partitioning on country
    ↓
Scenario 5: Best combination from above
```

**Why Sequential Testing?**
- Each partitioning type is tested independently to measure performance gains
- Allows incremental optimization without over-engineering
- Performance testing will determine which partitioning type is optimal
- Can combine approaches (e.g., hash + single_dim) if beneficial

### Stage 1: Temporal Granularity (Mandatory)

#### Decision Criteria

The segment granularity must balance:
1. **Segment size**: Avoid segments too large (slow queries) or too small (metadata overhead)
2. **Query patterns**: Align with common time ranges in queries
3. **Ingestion frequency**: Match data collection patterns

#### Granularity Options Analysis

| Granularity | Segments (7d) | Avg Size | Pros | Cons |
|-------------|---------------|----------|------|------|
| **HOUR** | 168 | 2.95 GB | Fine-grained, good for recent data | Many segments to manage |
| **DAY** | 7 | 70.8 GB | Aligns with daily collection, manageable | Larger segments |
| **WEEK** | 1 | 495.6 GB | Minimal segments | Too large, poor query performance |

#### Recommended: DAY Granularity

**Justification:**
- Aligns with daily data collection pattern
- 7 segments for 7-day window (manageable)
- Each segment can be split into partitions via secondary partitioning
- Most queries likely target daily or multi-day ranges

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

**Query Granularity: MINUTE**
- Fine enough for reporting needs
- Allows aggregation to hour/day as needed
- Storage impact acceptable

#### Validation Required

Before finalizing DAY granularity, validate:
- Actual segment sizes after ingestion
- Query performance with DAY segments
- Whether HOUR granularity provides better performance for recent data

### Stage 2: Secondary Partitioning Options

#### Option A: Dynamic Partitioning (Default/Baseline)

**Type:** `dynamic`

**When to use:** Default starting point. Druid automatically creates partitions based on target size.

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "dynamic",
    "maxRowsPerSegment": 5000000,
    "maxTotalRows": null
  }
}
```

**Characteristics:**
- Automatic partitioning based on row count
- No dimension-specific optimization
- Good for general-purpose workloads
- Baseline for performance comparison

**Use Case:** Scenario 1 - Baseline testing

#### Option B: Hash Partitioning

**Type:** `hash`

**When to use:** High-cardinality dimension frequently used in query filters (e.g., account_id).

**How it works:** Data is partitioned using a hash function on the specified dimension, ensuring even distribution across partitions.

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "hash",
    "targetPartitionSize": 1024000,
    "maxPartitionSize": 2048000,
    "partitionDimension": "account_id",
    "assumeGrouped": false
  }
}
```

**Key Parameters:**
- `partitionDimension`: Dimension to hash on (must be high cardinality)
- `targetPartitionSize`: Target rows per partition
- `maxPartitionSize`: Maximum rows before splitting

**Benefits:**
- Even distribution across partitions
- Queries filtering by partitioned dimension scan fewer partitions
- Good for high-cardinality dimensions (account_id with 320M values)

**Trade-offs:**
- Only benefits queries filtering by the partitioned dimension
- Queries not filtering by this dimension scan all partitions
- Requires high cardinality to be effective

**Use Cases:** 
- Scenario 2: Hash on `account_id` (if high cardinality confirmed)
- Scenario 3: Hash on `PSP_id` (if high cardinality and frequently filtered)

#### Option C: Single Dimension Partitioning

**Type:** `single_dim`

**When to use:** Low-to-medium cardinality dimension with clear value ranges (e.g., country with 22 values).

**How it works:** Partitions are created based on ranges of values for a single dimension. Druid automatically determines optimal ranges.

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

**Key Parameters:**
- `partitionDimension`: Dimension to partition on
- `targetRowsPerSegment`: Target rows per segment
- `maxRowsPerSegment`: Maximum rows before splitting

**Benefits:**
- Queries filtering by partitioned dimension scan only relevant partitions
- Good for low-to-medium cardinality dimensions (country with 22 values)
- Automatic range optimization by Druid

**Trade-offs:**
- Queries NOT filtering by partitioned dimension must scan all partitions
- May create many small partitions if cardinality is high
- Less effective for very high cardinality dimensions

**Use Case:** 
- Scenario 4: Single_dim on `country` (22 values, frequently filtered)

### Partition Sizing

**Target Size:** 500 MB - 1 GB per partition (~1M-2M rows)

**Rationale:**
- Proven sweet spot in Druid for query performance
- Balances I/O efficiency with metadata overhead
- Allows parallel processing across partitions

**Configuration Guidelines:**
- For `hash`: Use `targetPartitionSize: 1024000` (1M rows)
- For `single_dim`: Use `targetRowsPerSegment: 5000000` (5M rows, will be split)
- For `dynamic`: Use `maxRowsPerSegment: 5000000` (5M rows)

---

## Dimension Design

### Known Dimensions

Based on system requirements, the following dimensions are available:

| Dimension | Expected Cardinality | Notes |
|-----------|----------------------|-------|
| `account_id` | ~320M | Very high cardinality - good for hash partitioning |
| `country` | 22 | Low cardinality - good for single_dim partitioning |
| `currency` | 1 | Single value - not useful for partitioning |
| `PSP_id` | To be determined | Requires validation - may be good for hash if high cardinality |
| `access_manager_id` | To be determined | Requires validation |
| `transaction_id` | Unique | Guaranteed unique, but query pattern unknown |
| `timestamp` | N/A | Temporal dimension (handled separately) |

### Dimension Ordering Strategy

**Principle:** Order dimensions by:
1. **Cardinality** (highest first)
2. **Filter frequency** (most filtered first)

**Proposed Ordering (to be validated):**
1. `account_id` (highest cardinality, likely most filtered)
2. `PSP_id` (if high cardinality and frequently filtered)
3. `access_manager_id` (if high cardinality and frequently filtered)
4. `country` (low cardinality, but may be frequently filtered)

**Action Required:**
- Validate actual cardinality for all dimensions
- Analyze query logs to determine filter frequency
- Adjust ordering based on real usage patterns

### Clustering Strategy

**Note:** In Druid, clustering refers to the physical ordering of data within segments. This is different from partitioning.

**Primary cluster by:** `account_id` (if high cardinality confirmed)
- Co-locates transactions for same account
- Benefits queries filtering by account
- Improves compression

**Secondary cluster by:** Most frequently filtered dimension (to be determined from query logs)

---

## Performance Scenarios

### Testing Approach

Each scenario will be tested sequentially, measuring performance metrics to determine the optimal configuration. All dimensions are automatically indexed in Druid - the difference is in how data is physically partitioned.

### Scenario 1: Dynamic Partitioning (Baseline)

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `dynamic` (automatic based on size)
- No dimension-specific partitioning

**Purpose:** Establish baseline performance metrics.

**Configuration Example:**
```json
{
  "partitionsSpec": {
    "type": "dynamic",
    "maxRowsPerSegment": 5000000
  }
}
```

**Metrics to Measure:**
- Query latency (P50, P95, P99)
- Number of segments scanned per query
- Data scanned (MB) per query
- Memory usage
- Cache hit rate

**Expected Results:**
- All queries scan full daily segments
- Performance acceptable for simple queries
- May not meet SLA for complex queries
- Baseline for comparison

### Scenario 2: Hash Partitioning on account_id

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `hash` on `account_id`
- Target: 1M rows per partition

**Purpose:** Measure performance gain from hash partitioning on high-cardinality dimension.

**Configuration Example:**
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

**Metrics to Measure:**
- Query latency (P50, P95, P99) vs Scenario 1
- Number of segments/partitions scanned per query vs Scenario 1
- Data scanned (MB) per query vs Scenario 1
- Performance for account-filtered queries
- Performance for queries NOT filtering by account (may degrade)

**Expected Results:**
- Queries filtering by `account_id`: Should scan fewer partitions, better performance
- Queries NOT filtering by account: May scan all partitions, potentially slower
- Net benefit depends on query pattern distribution

### Scenario 3: Hash Partitioning on PSP_id

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `hash` on `PSP_id`
- Target: 1M rows per partition

**Purpose:** Measure performance gain from hash partitioning on PSP_id (if high cardinality).

**Configuration Example:**
```json
{
  "partitionsSpec": {
    "type": "hash",
    "targetPartitionSize": 1024000,
    "maxPartitionSize": 2048000,
    "partitionDimension": "PSP_id"
  }
}
```

**Metrics to Measure:** (Same as Scenario 2, but for PSP_id)

**Expected Results:**
- Performance improvement for PSP-filtered queries
- Comparison with Scenario 2 to determine best dimension for hash partitioning

### Scenario 4: Single Dimension Partitioning on country

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Partitioning: `single_dim` on `country`
- Target: 5M rows per segment (will be split by country)

**Purpose:** Measure performance gain/loss from single_dim partitioning on low-cardinality dimension.

**Configuration Example:**
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

**Metrics to Measure:**
- Query latency (P50, P95, P99) vs Scenario 1
- Number of segments/partitions scanned per query
- Performance for country-filtered queries
- Performance for queries NOT filtering by country (may degrade)
- Partition count per day segment

**Expected Results:**
- Country-filtered queries: Should scan fewer partitions, better performance
- Non-country-filtered queries: May scan all partitions, potentially slower
- Net benefit depends on query pattern distribution
- Should create ~22 partitions per day (one per country)

### Scenario 5: Combined Partitioning (if beneficial)

**Configuration:**
- Segment Granularity: DAY
- Query Granularity: MINUTE
- Primary Partitioning: Best from Scenarios 2-4
- Note: Druid supports one partitioning strategy per ingestion. For combined benefits, may need to:
  - Use hash on high-cardinality dimension (account_id)
  - Use compaction with different strategy if needed
  - Or accept trade-offs

**Purpose:** Measure combined optimization impact (if applicable).

**Note:** Druid typically uses one partitioning strategy per ingestion. However, compaction can use a different strategy, potentially combining benefits.

**Metrics to Measure:**
- Query latency vs all previous scenarios
- Overall performance across different query patterns
- Resource usage (memory, CPU)
- Operational complexity

**Expected Results:**
- Best performance for queries matching the chosen partitioning
- Trade-offs for other query patterns
- Higher operational complexity

### Performance Metrics Definition

For each scenario, measure:

| Metric | Description | Target |
|--------|-------------|--------|
| **Query Latency P95** | 95th percentile query response time | < 500ms |
| **Segments Scanned** | Number of segments opened per query | Minimize |
| **Partitions Scanned** | Number of partitions read per query | Minimize |
| **Data Scanned** | MB of data read per query | Minimize |
| **Cache Hit Rate** | Percentage of queries served from cache | > 60% |
| **Memory Usage** | Memory overhead from partitions | Monitor |
| **Query Success Rate** | Percentage of successful queries | > 99% |

### Decision Criteria

**Choose Hash Partitioning if:**
- Scenario 2/3 shows >20% improvement in latency for filtered queries
- Dimension has high cardinality (>100K values)
- Dimension is frequently used in query filters
- Trade-off for non-filtered queries is acceptable

**Choose Single_dim Partitioning if:**
- Scenario 4 shows >20% improvement in latency for filtered queries
- Dimension has low-to-medium cardinality (10-1000 values)
- Dimension is frequently used in query filters
- Trade-off for non-filtered queries is acceptable

**Stay with Dynamic if:**
- No partitioning strategy shows significant improvement
- Query patterns are too diverse
- Operational simplicity is preferred

**Final Selection:**
- Choose scenario with best overall performance
- Consider operational complexity
- Document trade-offs for future reference
- Validate with production-like query patterns

---

## Compaction Strategy

### Why Compaction is Needed

**Problem:** High-volume ingestion creates many small segments initially, leading to:
- Metadata overhead
- Slower queries (many segments to scan)
- Management complexity

**Solution:** Compaction consolidates small segments into optimal-sized partitions while optionally changing the partitioning strategy.

### Approach

**Core Principle:** Consolidate segments to target size (1M-2M rows, ~500 MB - 1 GB) while maintaining or optimizing partitioning strategy.

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
- `skipOffsetFromLatest: PT6H` - Don't compact last 6 hours (active ingestion window)
- `taskPriority: 25` - Lower than ingestion priority (50) to avoid interference
- `maxRowsInMemory: 500K` - Conservative to avoid OOM
- `partitionsSpec`: Can use different partitioning strategy than initial ingestion

**Important:** Compaction can use a different partitioning strategy than initial ingestion. This allows optimization after understanding query patterns.

**Note:** The exact compaction strategy (frequency, schedule) will be defined in a separate user story. This section focuses on the approach and configuration.

### Expected Outcomes

- Segment count reduction: 20-40% for historical data
- Query performance: 15-35% faster for multi-day queries
- Storage: 5-15% savings from better compression
- Option to optimize partitioning strategy based on learned query patterns

---

## Validation Plan

### Pre-Implementation Validation

Before finalizing the strategy, validate:

| Task | Method | Success Criteria |
|------|--------|------------------|
| **Dimension Cardinality** | Sample 1M rows from source | Confirm actual cardinalities |
| **Query Log Analysis** | Export 30 days from Druid console | Identify filter frequencies |
| **Volume Validation** | Measure actual ingestion | Confirm 7-day volume estimates |
| **Segment Size Test** | Ingest 1 day of data | Verify segment sizes match expectations |

### Performance Testing Plan

**Phase 1: Baseline (Scenario 1)**
- Ingest 7 days of data with DAY granularity and dynamic partitioning
- Run representative query set
- Measure all performance metrics
- Document baseline performance

**Phase 2: Hash Partitioning Testing (Scenarios 2-3)**
- Test hash partitioning on `account_id`
- Test hash partitioning on `PSP_id` (if high cardinality)
- Compare performance vs baseline
- Document improvements/degradations

**Phase 3: Single_dim Partitioning Testing (Scenario 4)**
- Test single_dim partitioning on `country`
- Measure performance for different query patterns
- Document trade-offs

**Phase 4: Final Selection**
- Compare all scenarios
- Select optimal partitioning strategy
- Validate overall performance meets targets
- Finalize configuration

### Success Criteria

**Must meet before production:**
- P95 query latency < 500ms for 7-day queries
- Segment scan efficiency > 80% (partitions read vs total)
- All performance scenarios documented with results
- Dimension cardinalities confirmed
- Query patterns validated

**Documentation Required:**
- Performance test results for each scenario
- Comparison matrix showing trade-offs
- Final configuration with justification
- Operational runbook for chosen strategy

---

## Appendices

### Appendix A: Ingestion Configuration Example

**Kafka Supervisor (example - to be finalized):**

```json
{
  "type": "kafka",
  "spec": {
    "ioConfig": {
      "type": "kafka",
      "consumerProperties": {
        "bootstrap.servers": "kafka-broker:9092"
      },
      "topic": "transactions",
      "inputFormat": {
        "type": "json"
      },
      "useEarliestOffset": true,
      "taskCount": 4,
      "replicas": 1,
      "taskDuration": "PT1H"
    },
    "tuningConfig": {
      "type": "kafka",
      "maxRowsInMemory": 100000,
      "maxRowsPerSegment": 5000000,
      "partitionsSpec": {
        "type": "dynamic"
      }
    },
    "dataSchema": {
      "dataSource": "transactions",
      "timestampSpec": {
        "column": "timestamp",
        "format": "millis"
      },
      "dimensionsSpec": {
        "dimensions": [
          "account_id",
          "country",
          "currency",
          "PSP_id",
          "access_manager_id",
          "transaction_id"
        ]
      },
      "metricsSpec": [
        {
          "type": "doubleSum",
          "name": "amount",
          "fieldName": "amount"
        }
      ],
      "granularitySpec": {
        "type": "uniform",
        "segmentGranularity": "DAY",
        "queryGranularity": "MINUTE",
        "rollup": false
      }
    }
  }
}
```

**Note:** Final dimensions and metrics to be confirmed from actual schema. Partitioning strategy will be updated based on performance testing results.

### Appendix B: Performance Testing Query Examples

**Query 1: Single Account, Last 24 Hours**
```sql
SELECT COUNT(*) 
FROM transactions 
WHERE account_id = 'XXX' 
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY
```

**Query 2: Country Filter, Last 7 Days**
```sql
SELECT country, SUM(amount) 
FROM transactions 
WHERE country = 'FR' 
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
GROUP BY country
```

**Query 3: PSP Filter, Last Day**
```sql
SELECT COUNT(*) 
FROM transactions 
WHERE PSP_id = 'YYY' 
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY
```

**Query 4: Multi-dimension Filter**
```sql
SELECT account_id, COUNT(*) 
FROM transactions 
WHERE account_id = 'XXX' 
  AND country = 'FR'
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
GROUP BY account_id
```

**Note:** Actual query set to be defined based on real usage patterns.

### Appendix C: Druid Partitioning Types Reference

**Dynamic Partitioning:**
- Type: `dynamic`
- Use case: Default, general-purpose
- Configuration: `maxRowsPerSegment`
- Pros: Automatic, simple
- Cons: No dimension-specific optimization

**Hash Partitioning:**
- Type: `hash`
- Use case: High-cardinality dimension (e.g., account_id with 320M values)
- Configuration: `partitionDimension`, `targetPartitionSize`
- Pros: Even distribution, good for filtered queries
- Cons: Only benefits queries filtering by partitioned dimension

**Single Dimension Partitioning:**
- Type: `single_dim`
- Use case: Low-to-medium cardinality dimension (e.g., country with 22 values)
- Configuration: `partitionDimension`, `targetRowsPerSegment`
- Pros: Good for filtered queries, automatic range optimization
- Cons: May create many partitions, only benefits filtered queries

### Appendix D: Reference Links

- [Druid Partitioning Guide](https://druid.apache.org/docs/latest/ingestion/partitioning.html)
- [Druid Segment Optimization](https://druid.apache.org/docs/latest/operations/segment-optimization.html)
- [Compaction Documentation](https://druid.apache.org/docs/latest/data-management/compaction.html)
- [Druid Indexing](https://druid.apache.org/docs/latest/ingestion/index.html)

---

## Document Status

**Current state:** Strategy defined with correct Druid concepts, awaiting validation and performance testing

**Next actions:**
1. Validate dimension cardinalities
2. Analyze query logs (30 days)
3. Execute performance testing scenarios
4. Finalize configuration based on test results
5. Document final strategy with justifications

---

**End of Document**
