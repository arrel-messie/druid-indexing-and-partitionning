# Druid Partitioning Strategy Design Document
## Transactions Datasource

**Document Version:** 1.0  
**Date:** [Date]  
**Author:** [Author Name]  
**Status:** Draft / Under Review / Approved

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
- Segment granularity: [To be determined]
- Query granularity: [To be determined]
- Secondary partitioning: [To be determined]
- Target segment size: [To be determined]

---

## Context and Objectives

### Business Context

The transactions datasource requires a partitioning strategy that supports:
- High-volume transaction data ingestion
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

| Metric | Value | Notes |
|--------|-------|-------|
| Transactions per day | [Value] | Peak and average |
| Transactions per hour | [Value] | Peak hour volume |
| Average row size | [Value] bytes | Estimated |
| Daily data volume | [Value] GB/TB | Raw data size |
| Retention period | [Value] days/months | Data retention requirement |

### Query Patterns

| Query Type | Frequency | Time Window | Typical Dimensions |
|------------|-----------|-------------|-------------------|
| Real-time monitoring | High | Last 1-24 hours | [Dimensions] |
| Daily reporting | Medium | Last 7-30 days | [Dimensions] |
| Historical analysis | Low | Months to years | [Dimensions] |
| Ad-hoc exploration | Variable | Variable | [Dimensions] |

### Data Distribution

- **Time distribution:** [Description of how data is distributed over time]
- **Dimension cardinality:** [High-level overview of dimension cardinalities]
- **Hot vs. cold data:** [Description of data access patterns]

---

## Time Granularity Strategy

### Segment Granularity

**Selected Value:** `[GRANULARITY]` (e.g., HOUR, DAY, MONTH)

**Justification:**

[Detailed justification based on:]
- Expected data volume per time unit
- Query time window patterns
- Segment size targets
- Ingestion frequency
- Query performance requirements

**Analysis:**

| Granularity Option | Pros | Cons | Recommendation |
|-------------------|------|------|----------------|
| HOUR | [Pros] | [Cons] | [Yes/No] |
| DAY | [Pros] | [Cons] | [Yes/No] |
| MONTH | [Pros] | [Cons] | [Yes/No] |

**Decision Rationale:**

[Explain why the selected granularity is optimal for this use case, considering:]
- Balance between segment count and segment size
- Query performance implications
- Ingestion overhead
- Maintenance complexity

### Query Granularity

**Selected Value:** `[GRANULARITY]` (e.g., MINUTE, HOUR, DAY)

**Justification:**

[Detailed justification based on:]
- Required query precision
- Aggregation needs
- Storage efficiency
- Query performance trade-offs

**Analysis:**

| Granularity Option | Query Precision | Storage Impact | Performance Impact |
|-------------------|-----------------|----------------|-------------------|
| MINUTE | [Impact] | [Impact] | [Impact] |
| HOUR | [Impact] | [Impact] | [Impact] |
| DAY | [Impact] | [Impact] | [Impact] |

**Decision Rationale:**

[Explain why the selected query granularity meets business requirements while maintaining performance]

---

## Dimension Partitioning Strategy

### Primary Filter Dimensions

The following dimensions are identified as primary filter dimensions based on query pattern analysis. These dimensions appear most frequently in WHERE clauses and are critical for query performance.

1. **[Dimension Name 1]** - [Description and usage pattern]
2. **[Dimension Name 2]** - [Description and usage pattern]
3. **[Dimension Name 3]** - [Description and usage pattern]

**Query Pattern Analysis:**

| Dimension | Filter Frequency | Typical Filter Values | Cardinality |
|-----------|------------------|----------------------|-------------|
| [Dim 1] | [%] | [Examples] | [Value] |
| [Dim 2] | [%] | [Examples] | [Value] |
| [Dim 3] | [%] | [Examples] | [Value] |

### Secondary Partitioning Strategy

**Strategy Type:** [HASH / RANGE / SINGLE_DIM]

**Selected Dimensions for Partitioning:**
1. **[Dimension Name]** - [Justification]
2. **[Dimension Name]** - [Justification]

**Partitioning Configuration:**

```json
{
  "partitionsSpec": {
    "type": "[hash/range/single_dim]",
    "targetPartitionSize": [value],
    "maxPartitionSize": [value],
    "partitionDimension": "[dimension_name]",
    "assumeGrouped": [true/false]
  }
}
```

**Justification:**

[Explain why these dimensions were chosen for secondary partitioning:]
- Query performance benefits
- Data distribution characteristics
- Cardinality considerations
- Balance between partition count and partition size

**Expected Impact:**

- **Query Performance:** [Expected improvement in query performance]
- **Segment Distribution:** [How segments will be distributed]
- **Storage Efficiency:** [Impact on storage and indexing]

### Clustering Strategy

**Clustering Dimensions:** [List of dimensions used for clustering]

**Clustering Order:** [Order of clustering dimensions]

**Rationale:**

[Explain the clustering strategy and its benefits for query performance]

---

## Dimension List and Ordering

### Complete Dimension List

Dimensions are ordered by query frequency and filtering importance. Primary filter dimensions appear first to optimize query performance.

| Order | Dimension Name | Type | Cardinality | Primary Filter | Notes |
|-------|----------------|------|-------------|----------------|-------|
| 1 | [Dim 1] | [Type] | [Value] | Yes | [Notes] |
| 2 | [Dim 2] | [Type] | [Value] | Yes | [Notes] |
| 3 | [Dim 3] | [Type] | [Value] | Yes | [Notes] |
| 4 | [Dim 4] | [Type] | [Value] | No | [Notes] |
| 5 | [Dim 5] | [Type] | [Value] | No | [Notes] |
| ... | ... | ... | ... | ... | ... |

### Dimension Details

#### Primary Filter Dimensions

**[Dimension Name 1]**
- **Type:** [STRING / LONG / FLOAT / etc.]
- **Cardinality:** [Value]
- **Usage:** [Description of how it's used in queries]
- **Filter Patterns:** [Common filter patterns]
- **Indexing:** [Any special indexing considerations]

**[Dimension Name 2]**
- [Similar structure]

**[Dimension Name 3]**
- [Similar structure]

#### Secondary Dimensions

[Details for other dimensions as needed]

### Ordering Rationale

The dimension ordering is based on:
1. **Query frequency:** Dimensions used most often in WHERE clauses
2. **Selectivity:** High-selectivity dimensions that significantly reduce scan size
3. **Query performance impact:** Dimensions that provide the greatest performance benefit when filtered early
4. **Business importance:** Dimensions critical to business reporting and analysis

---

## Sizing and Capacity Planning

### Segment Sizing

**Target Segment Size:** [Value] MB (recommended range: 300-700 MB)

**Rows per Segment Estimation:**

| Time Period | Segment Granularity | Estimated Rows | Estimated Size | Notes |
|-------------|-------------------|---------------|----------------|-------|
| Peak hour | [Granularity] | [Value] | [Value] MB | [Notes] |
| Average hour | [Granularity] | [Value] | [Value] MB | [Notes] |
| Peak day | [Granularity] | [Value] | [Value] MB | [Notes] |
| Average day | [Granularity] | [Value] | [Value] MB | [Notes] |

### Calculation Methodology

**Row Size Estimation:**
```
Average row size = [dimension sizes] + [metric sizes] + [overhead]
                 = [X] bytes
```

**Rows per Segment:**
```
Rows per segment = Target segment size / Average row size
                 = [X] MB / [Y] bytes
                 = [Z] rows
```

**Segments per Time Period:**
```
Segments per [period] = Total rows / Rows per segment
                      = [X] rows / [Y] rows
                      = [Z] segments
```

### Capacity Planning

| Metric | Current | 6 Months | 12 Months | Notes |
|--------|---------|----------|-----------|-------|
| Daily ingestion | [Value] | [Value] | [Value] | [Growth rate] |
| Total segments | [Value] | [Value] | [Value] | [Calculation] |
| Storage required | [Value] | [Value] | [Value] | [Including replication] |
| Query load | [Value] | [Value] | [Value] | [Queries per hour] |

### Performance Targets

- **Query latency (P95):** [Value] ms for [query type]
- **Query latency (P99):** [Value] ms for [query type]
- **Ingestion throughput:** [Value] events/second
- **Segment scan efficiency:** [Value]% (percentage of segments scanned vs. total)

---

## Compaction Strategy

### Compaction Objectives

- Optimize segment size distribution
- Improve query performance through better segment organization
- Reduce storage overhead
- Maintain data freshness and availability

### Compaction Configuration

**Compaction Granularity:** [Value] (e.g., HOUR, DAY)

**Compaction Schedule:**
- **Frequency:** [Schedule, e.g., hourly, daily]
- **Time window:** [When compaction runs]
- **Priority:** [Compaction priority settings]

**Compaction Spec:**

```json
{
  "dataSource": "transactions",
  "taskPriority": [priority],
  "inputSegmentSizeBytes": [value],
  "maxRowsPerSegment": [value],
  "skipOffsetFromLatest": "[period]",
  "tuningConfig": {
    "maxNumConcurrentSubTasks": [value],
    "maxRowsInMemory": [value],
    "partitionsSpec": {
      "type": "[type]",
      "targetPartitionSize": [value]
    }
  }
}
```

### Compaction Strategy Details

**Initial Compaction:**
- **Trigger:** [When initial compaction occurs]
- **Target:** [What initial compaction achieves]
- **Configuration:** [Specific settings]

**Ongoing Compaction:**
- **Schedule:** [Regular compaction schedule]
- **Scope:** [Which segments are compacted]
- **Retention:** [How long to keep original segments]

**Long-term Compaction:**
- **Strategy:** [Approach for older data]
- **Frequency:** [How often long-term compaction runs]
- **Benefits:** [Expected improvements]

### Compaction Impact

**Expected Improvements:**
- Segment count reduction: [Percentage]
- Query performance improvement: [Percentage]
- Storage efficiency: [Percentage improvement]
- Maintenance overhead: [Impact on system resources]

**Monitoring Metrics:**
- Compaction task success rate
- Average compaction duration
- Segment size distribution before/after
- Query performance metrics

---

## Query Performance Considerations

### Query Optimization

**Partition Pruning:**
- How the partitioning strategy enables partition pruning
- Expected reduction in scanned segments
- Impact on query latency

**Dimension Filtering:**
- How primary filter dimensions optimize queries
- Index utilization
- Scan reduction benefits

**Time-based Queries:**
- Optimization for time-range queries
- Segment granularity impact on time-based filtering
- Query granularity considerations

### Performance Benchmarks

| Query Type | Without Optimization | With Optimization | Improvement |
|------------|---------------------|-------------------|-------------|
| [Query 1] | [Latency] | [Latency] | [%] |
| [Query 2] | [Latency] | [Latency] | [%] |
| [Query 3] | [Latency] | [Latency] | [%] |

### Monitoring and Tuning

**Key Metrics to Monitor:**
- Query latency (P50, P95, P99)
- Segment scan efficiency
- Cache hit rates
- Ingestion lag
- Segment count and distribution

**Tuning Parameters:**
- [Parameter 1]: [Description and tuning approach]
- [Parameter 2]: [Description and tuning approach]
- [Parameter 3]: [Description and tuning approach]

---

## Validation and Approval

### Design Review

**Reviewers:**
- Data Architecture Team
- Platform Engineering Team
- Business Stakeholders

**Review Criteria:**
- Alignment with business requirements
- Technical feasibility
- Performance expectations
- Operational considerations
- Cost implications

### Approval Status

| Reviewer | Role | Date | Status | Comments |
|----------|------|------|--------|----------|
| [Name] | Data Architect | [Date] | [Pending/Approved/Rejected] | [Comments] |
| [Name] | [Role] | [Date] | [Pending/Approved/Rejected] | [Comments] |
| [Name] | [Role] | [Date] | [Pending/Approved/Rejected] | [Comments] |

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
        "type": "[partition_type]",
        "targetPartitionSize": [value]
      },
      "maxRowsPerSegment": [value]
    },
    "dataSchema": {
      "dataSource": "transactions",
      "granularitySpec": {
        "type": "uniform",
        "segmentGranularity": "[granularity]",
        "queryGranularity": "[granularity]"
      },
      "dimensionsSpec": {
        "dimensions": [
          "[dimension_list_in_order]"
        ]
      }
    }
  }
}
```

### Appendix B: Reference Documentation

- Apache Druid Partitioning Documentation: [Link]
- Segment Granularity Best Practices: [Link]
- Compaction Strategy Guide: [Link]
- Performance Tuning Guide: [Link]

### Appendix C: Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | [Date] | [Name] | Initial design document |

---

**Document End**

