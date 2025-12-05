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
- Financial services or e-commerce transaction system
- High-volume, real-time transaction processing
- Need for both real-time monitoring and historical analysis

| Metric | Assumed Value | Notes |
|--------|---------------|-------|
| Transactions per day | 10,000,000 | Peak: 15M, Average: 8M |
| Transactions per hour | 500,000 | Peak hour: 1,000,000 |
| Average row size | 512 bytes | Including dimensions and metrics |
| Daily data volume | ~5 GB | Raw data size (10M rows × 512 bytes) |
| Retention period | 2 years | 730 days retention requirement |

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

**Selected Value:** `HOUR`

**Justification:**

Based on assumed transaction volume of 500,000 transactions per hour (peak: 1,000,000), hourly segmentation provides:

1. **Optimal Segment Size:**
   - Average hour: 500K transactions × 512 bytes = ~256 MB per segment
   - Peak hour: 1M transactions × 512 bytes = ~512 MB per segment
   - Both within recommended 300-700 MB range

2. **Query Performance:**
   - Most queries target specific time ranges (last hour, last day, last week)
   - Hourly segments enable efficient partition pruning
   - Reduces number of segments scanned for time-based queries

3. **Ingestion Efficiency:**
   - Hourly segments align with typical batch processing windows
   - Allows for timely data availability without excessive segment creation overhead

4. **Maintenance Balance:**
   - 24 segments per day provides manageable segment count
   - Not too granular (minute-level would create too many segments)
   - Not too coarse (daily would create segments too large for peak hours)

**Analysis:**

| Granularity Option | Pros | Cons | Recommendation |
|-------------------|------|------|----------------|
| MINUTE | Very fine-grained, excellent for real-time | Too many segments (1440/day), overhead | No |
| HOUR | Balanced segment size, good query performance | May be too large for very high volume | **Yes** |
| DAY | Simple, fewer segments | Segments too large (5GB+), poor query performance | No |
| MONTH | Minimal segments | Extremely large segments, poor performance | No |

**Decision Rationale:**

Hourly segmentation provides the optimal balance between segment size, query performance, and operational overhead for the assumed transaction volume. This granularity ensures segments remain within the recommended size range even during peak hours while maintaining efficient query execution.

### Query Granularity

**Selected Value:** `MINUTE`

**Justification:**

For transaction analysis, minute-level granularity is assumed to be necessary for:

1. **Business Requirements:**
   - Fraud detection requires minute-level precision
   - Real-time monitoring needs fine-grained time buckets
   - Transaction timing analysis for investigation

2. **Storage Efficiency:**
   - Minute granularity provides sufficient detail without excessive storage overhead
   - Allows aggregation to coarser granularities when needed
   - Balances precision with storage costs

3. **Query Flexibility:**
   - Supports both detailed minute-level analysis and aggregated hourly/daily reporting
   - Enables time-series analysis at appropriate resolution

**Analysis:**

| Granularity Option | Query Precision | Storage Impact | Performance Impact |
|-------------------|-----------------|----------------|-------------------|
| SECOND | Very high precision | High storage overhead | Slower queries | No |
| MINUTE | High precision | Moderate storage | Good performance | **Yes** |
| HOUR | Lower precision | Lower storage | Better performance | No (insufficient detail) |
| DAY | Very low precision | Minimal storage | Best performance | No (insufficient detail) |

**Decision Rationale:**

Minute-level query granularity provides the necessary precision for transaction analysis while maintaining reasonable storage and query performance. This granularity supports the assumed business requirements for fraud detection and real-time monitoring.

---

## Dimension Partitioning Strategy

### Primary Filter Dimensions

Based on assumed query patterns, the following dimensions are identified as primary filter dimensions:

1. **account_id** - Most queries filter by specific accounts or account ranges
2. **transaction_type** - Frequently used to filter transaction categories
3. **status** - Critical for filtering successful vs. failed transactions
4. **timestamp** - Always used in time-range queries (implicit primary filter)

**Query Pattern Analysis:**

| Dimension | Filter Frequency | Typical Filter Values | Cardinality |
|-----------|------------------|----------------------|-------------|
| account_id | 80% | Specific account IDs, account ranges | 1,000,000+ |
| transaction_type | 60% | 'payment', 'refund', 'transfer', etc. | 15-20 |
| status | 50% | 'success', 'failed', 'pending' | 3-5 |
| merchant_id | 40% | Specific merchant IDs | 10,000-50,000 |
| region | 30% | Geographic regions | 10-50 |

### Secondary Partitioning Strategy

**Strategy Type:** `HASH`

**Selected Dimensions for Partitioning:**
1. **account_id** - Primary partitioning dimension
   - High cardinality (1M+ accounts)
   - Most frequently filtered dimension (80% of queries)
   - Enables efficient partition pruning for account-based queries

2. **transaction_type** - Secondary partitioning dimension (optional)
   - Used in combination with account_id for many queries
   - Lower cardinality allows for effective hash distribution

**Partitioning Configuration:**

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

**Justification:**

1. **Query Performance Benefits:**
   - Hash partitioning on account_id enables partition pruning for account-specific queries
   - Reduces segment scan overhead by 60-80% for account-filtered queries
   - Improves parallel query execution

2. **Data Distribution:**
   - Hash partitioning provides even distribution across partitions
   - Prevents hot-spotting on specific account ranges
   - Balances load across cluster nodes

3. **Cardinality Considerations:**
   - High cardinality of account_id ensures good hash distribution
   - Prevents partition skew that could occur with low-cardinality dimensions

4. **Balance:**
   - Target partition size of 5M rows keeps segments manageable
   - Max partition size of 10M rows prevents oversized segments
   - Results in approximately 2 segments per hour (500K rows/hour average)

**Expected Impact:**

- **Query Performance:** 40-60% reduction in query latency for account-filtered queries
- **Segment Distribution:** Even distribution across cluster, 2-3 segments per hour
- **Storage Efficiency:** Optimal segment size distribution, minimal overhead

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

| Order | Dimension Name | Type | Cardinality | Primary Filter | Notes |
|-------|----------------|------|-------------|----------------|-------|
| 1 | account_id | STRING | 1,000,000+ | Yes | Most frequently filtered |
| 2 | transaction_type | STRING | 15-20 | Yes | High filter frequency |
| 3 | status | STRING | 3-5 | Yes | Critical for filtering |
| 4 | merchant_id | STRING | 10,000-50,000 | No | Frequently used in joins |
| 5 | region | STRING | 10-50 | No | Geographic filtering |
| 6 | currency | STRING | 10-20 | No | Multi-currency support |
| 7 | payment_method | STRING | 5-10 | No | Payment type analysis |
| 8 | transaction_id | STRING | Very High | No | Unique identifier |
| 9 | user_id | STRING | 500,000+ | No | User-level analysis |
| 10 | device_type | STRING | 5-10 | No | Device analytics |

### Dimension Details

#### Primary Filter Dimensions

**account_id**
- **Type:** STRING
- **Cardinality:** 1,000,000+ unique accounts
- **Usage:** Primary dimension for account-based queries, account balance checks, transaction history
- **Filter Patterns:** 
  - Exact match: `account_id = 'ACC123456'`
  - Range queries: `account_id IN ('ACC1', 'ACC2', ...)`
  - Pattern matching: `account_id LIKE 'ACC%'`
- **Indexing:** High-priority indexing, used for hash partitioning

**transaction_type**
- **Type:** STRING
- **Cardinality:** 15-20 unique types
- **Usage:** Categorization of transactions (payment, refund, transfer, etc.)
- **Filter Patterns:**
  - Exact match: `transaction_type = 'payment'`
  - Multiple values: `transaction_type IN ('payment', 'refund')`
- **Indexing:** Standard indexing, used for secondary clustering

**status**
- **Type:** STRING
- **Cardinality:** 3-5 unique statuses
- **Usage:** Transaction status filtering (success, failed, pending)
- **Filter Patterns:**
  - Exact match: `status = 'success'`
  - Exclusions: `status != 'failed'`
- **Indexing:** Standard indexing

#### Secondary Dimensions

**merchant_id**
- **Type:** STRING
- **Cardinality:** 10,000-50,000 merchants
- **Usage:** Merchant-specific analysis, merchant performance reporting
- **Filter Patterns:** Exact match, range queries

**region**
- **Type:** STRING
- **Cardinality:** 10-50 geographic regions
- **Usage:** Geographic analysis, regional reporting
- **Filter Patterns:** Exact match, multiple values

### Ordering Rationale

The dimension ordering is based on:
1. **Query frequency:** account_id appears in 80% of queries, making it the top priority
2. **Selectivity:** High-cardinality dimensions like account_id significantly reduce scan size when filtered
3. **Query performance impact:** Dimensions used in WHERE clauses provide greatest benefit when indexed early
4. **Business importance:** account_id and transaction_type are critical for core business operations

---

## Sizing and Capacity Planning

### Segment Sizing

**Target Segment Size:** 500 MB (within recommended 300-700 MB range)

**Rows per Segment Estimation:**

| Time Period | Segment Granularity | Estimated Rows | Estimated Size | Notes |
|-------------|-------------------|---------------|----------------|-------|
| Peak hour | HOUR | 1,000,000 | ~512 MB | Maximum expected |
| Average hour | HOUR | 500,000 | ~256 MB | Typical volume |
| Peak day | HOUR | 24,000,000 | ~12 GB | 24 segments × 512 MB |
| Average day | HOUR | 12,000,000 | ~6 GB | 24 segments × 256 MB |

### Calculation Methodology

**Row Size Estimation:**
```
Average row size = Dimensions (400 bytes) + Metrics (100 bytes) + Overhead (12 bytes)
                 = 512 bytes per row
```

**Rows per Segment:**
```
Target segment size = 500 MB = 524,288,000 bytes
Rows per segment = 524,288,000 bytes / 512 bytes
                 = 1,024,000 rows
                 ≈ 1M rows per segment
```

**Segments per Time Period:**
```
Average hour: 500K rows
Segments per hour = 500K rows / 1M rows per segment
                  = 0.5 segments (will create 1 segment per hour)
                  
Peak hour: 1M rows
Segments per hour = 1M rows / 1M rows per segment
                  = 1 segment per hour
```

### Capacity Planning

**Assumptions:**
- Growth rate: 20% year-over-year
- Replication factor: 2 (for high availability)
- Compression ratio: 3:1 (Druid compression)

| Metric | Current | 6 Months | 12 Months | Notes |
|--------|---------|----------|-----------|-------|
| Daily ingestion | 10M rows | 11M rows | 12M rows | 20% annual growth |
| Total segments | 24/day | 24/day | 24/day | 1 segment per hour |
| Storage required | 12 GB/day | 13.2 GB/day | 14.4 GB/day | Raw data |
| Storage with replication | 24 GB/day | 26.4 GB/day | 28.8 GB/day | 2x replication |
| Storage with compression | 8 GB/day | 8.8 GB/day | 9.6 GB/day | 3:1 compression |
| Annual storage | ~3 TB | ~3.2 TB | ~3.5 TB | Compressed, replicated |
| Query load | 1,000/hour | 1,200/hour | 1,400/hour | Growing usage |

### Performance Targets

- **Query latency (P95):** < 500 ms for account-filtered queries on last 24 hours
- **Query latency (P99):** < 1,000 ms for account-filtered queries on last 24 hours
- **Query latency (P95):** < 2,000 ms for historical queries (30+ days)
- **Ingestion throughput:** 5,000 events/second sustained
- **Segment scan efficiency:** > 80% (percentage of segments scanned vs. total)

---

## Compaction Strategy

### Compaction Objectives

- Optimize segment size distribution to target 500 MB per segment
- Improve query performance through better segment organization
- Reduce storage overhead by consolidating small segments
- Maintain data freshness and availability during compaction

### Compaction Configuration

**Compaction Granularity:** HOUR (matches segment granularity)

**Compaction Schedule:**
- **Frequency:** Every 4 hours
- **Time window:** Compacts segments older than 4 hours
- **Priority:** Medium (allows real-time queries on recent data)

**Compaction Spec:**

```json
{
  "dataSource": "transactions",
  "taskPriority": 25,
  "inputSegmentSizeBytes": 268435456,
  "maxRowsPerSegment": 1048576,
  "skipOffsetFromLatest": "PT4H",
  "tuningConfig": {
    "maxNumConcurrentSubTasks": 2,
    "maxRowsInMemory": 100000,
    "partitionsSpec": {
      "type": "hash",
      "targetPartitionSize": 5000000,
      "maxPartitionSize": 10000000,
      "partitionDimension": "account_id"
    }
  }
}
```

### Compaction Strategy Details

**Initial Compaction:**
- **Trigger:** Segments older than 4 hours
- **Target:** Consolidate small segments and optimize segment size to 500 MB
- **Configuration:** Hash partitioning on account_id maintained

**Ongoing Compaction:**
- **Schedule:** Every 4 hours, compacting segments from 4-8 hours ago
- **Scope:** All segments within the time window
- **Retention:** Original segments retained for 24 hours before deletion

**Long-term Compaction:**
- **Strategy:** Weekly compaction for data older than 30 days
- **Frequency:** Weekly, during low-traffic periods
- **Benefits:** 
  - Further optimize segment sizes for historical data
  - Reduce segment count for older data
  - Improve query performance on historical queries

### Compaction Impact

**Expected Improvements:**
- Segment count reduction: 20-30% (consolidating small segments)
- Query performance improvement: 15-25% (fewer segments to scan)
- Storage efficiency: 10-15% improvement (better compression)
- Maintenance overhead: Low (4-hour schedule prevents excessive load)

**Monitoring Metrics:**
- Compaction task success rate: Target > 99%
- Average compaction duration: Target < 30 minutes per run
- Segment size distribution: 80% of segments within 400-600 MB range
- Query performance metrics: Monitor for degradation during compaction

---

## Query Performance Considerations

### Query Optimization

**Partition Pruning:**
- Hash partitioning on account_id enables efficient partition pruning
- Queries filtering by account_id scan only relevant partitions
- Expected reduction: 60-80% of segments can be skipped for account-filtered queries
- Impact: Significant reduction in query latency for account-specific queries

**Dimension Filtering:**
- Primary filter dimensions (account_id, transaction_type, status) are indexed and ordered first
- Early filtering reduces scan size and improves query performance
- Index utilization: High for primary filter dimensions
- Scan reduction: 70-90% for queries using primary filter dimensions

**Time-based Queries:**
- Hourly segment granularity enables efficient time-range filtering
- Queries for last hour scan 1 segment, last day scans 24 segments
- Query granularity of MINUTE provides necessary precision without excessive overhead
- Time-based partition pruning: Very effective for recent data queries

### Performance Benchmarks

**Assumptions based on typical Druid performance:**

| Query Type | Without Optimization | With Optimization | Improvement |
|------------|---------------------|-------------------|-------------|
| Account query (last 24h) | 2,000 ms | 500 ms | 75% |
| Account + type filter (last 7d) | 5,000 ms | 1,500 ms | 70% |
| Historical analysis (30d) | 10,000 ms | 6,000 ms | 40% |
| Real-time monitoring (last hour) | 1,000 ms | 300 ms | 70% |

### Monitoring and Tuning

**Key Metrics to Monitor:**
- Query latency (P50, P95, P99) by query type
- Segment scan efficiency (segments scanned vs. total segments)
- Cache hit rates (query result cache, segment cache)
- Ingestion lag (time from event to queryable)
- Segment count and size distribution
- Compaction task performance

**Tuning Parameters:**
- **Segment cache size:** Adjust based on query patterns and available memory
- **Query timeout:** Set appropriate timeouts based on query complexity
- **Compaction frequency:** Adjust based on segment size distribution
- **Partition size:** Fine-tune targetPartitionSize based on actual data distribution

---

## Validation and Approval

### Design Review

**Reviewers:**
- Data Architecture Team
- Platform Engineering Team
- Business Stakeholders (for query requirements validation)

**Review Criteria:**
- Alignment with business requirements
- Technical feasibility
- Performance expectations
- Operational considerations
- Cost implications

### Assumptions to Validate

**Critical Assumptions Requiring Validation:**
1. Transaction volume: 10M transactions/day (peak: 15M)
2. Query patterns: 80% of queries filter by account_id
3. Retention period: 2 years
4. Row size: 512 bytes average
5. Growth rate: 20% year-over-year
6. Primary filter dimensions: account_id, transaction_type, status

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

```json
{
  "type": "index_parallel",
  "spec": {
    "ioConfig": {
      "type": "index_parallel",
      "inputSource": {
        "type": "[source_type]"
      }
    },
    "tuningConfig": {
      "type": "index_parallel",
      "partitionsSpec": {
        "type": "hash",
        "targetPartitionSize": 5000000,
        "maxPartitionSize": 10000000,
        "partitionDimension": "account_id",
        "assumeGrouped": false
      },
      "maxRowsPerSegment": 1048576
    },
    "dataSchema": {
      "dataSource": "transactions",
      "granularitySpec": {
        "type": "uniform",
        "segmentGranularity": "HOUR",
        "queryGranularity": "MINUTE"
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
      }
    }
  }
}
```

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

