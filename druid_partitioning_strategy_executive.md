# Druid Partitioning Strategy
## IDM Settlement Transaction Datasource

**Document ID:** IDM-SETTLEMENT-DRUID-TRANSACTION-DATA-PARTITIONNING-2025-001  
**Version:** 1.1 
**Date:** December 18, 2025  
**Author:** 
**Status:** Pending Architecture Approval

---

<!-- TOC -->
* [Druid Partitioning Strategy](#druid-partitioning-strategy)
  * [IDM Settlement Transaction Datasource](#idm-settlement-transaction-datasource)
  * [1. Executive Summary](#1-executive-summary)
  * [2. Time Partitioning](#2-time-partitioning)
    * [2.1 Segment Granularity: DAY](#21-segment-granularity-day)
  * [3. Secondary Partitioning](#3-secondary-partitioning)
    * [3.1 Range Partitioning on Access Manager Identifier](#31-range-partitioning-on-access-manager-identifier)
  * [4. Dimension Ordering](#4-dimension-ordering)
  * [5. Segment Sizing and Storage](#5-segment-sizing-and-storage)
    * [5.1 Target Specifications](#51-target-specifications)
    * [5.2 Volume Projections](#52-volume-projections)
  * [6. Compaction Strategy](#6-compaction-strategy)
  * [7. Performance Testing Plan](#7-performance-testing-plan)
    * [7.1 Test Scenarios](#71-test-scenarios)
    * [7.2 Test Query Coverage](#72-test-query-coverage)
    * [7.3 Success Criteria](#73-success-criteria)
  * [8. Implementation Timeline](#8-implementation-timeline)
  * [9. Approval](#9-approval)
  * [Document History](#document-history)
<!-- TOC -->

## 1. Executive Summary

This document defines the time and dimension partitioning strategy for the `idm_settlement` datasource to maintain query performance at expected transaction volumes (138M rows/day average, 1.38B peak).

**Recommended Strategy:**
- Primary Partitioning: DAY segmentGranularity (time-based)
- Secondary Partitioning: Range partitioning on `payment_debited_access_manager_identifier`
- Target Segment Size: 5M rows / 500-700 MB
- Retention: 7-day hot window

**Expected Performance Gains for transaction status query(filtered by access manager sender/receiver identifier):**
- Query latency reduction 
- Segment scan reduction 
- Storage reduction 

---

## 2. Time Partitioning

### 2.1 Segment Granularity: DAY

**Configuration:**
```json
{
  "segmentGranularity": "DAY",
  "queryGranularity": "NONE"
}
```

**Rationale:**
- Query window: 7 days (per functional requirements)
- Daily segments: 28 segments/day at average volume
- Metadata overhead: Minimal (7 days = 196 segments total)
- HOUR granularity would create 168 segments per 7-day window (excessive)

**queryGranularity: NONE** preserves timestamp precision. Query patterns do not require time-based aggregation bucketing.

---

## 3. Secondary Partitioning

### 3.1 Range Partitioning on Access Manager Identifier

**Configuration:**
```json
{
  "partitionsSpec": {
    "type": "range",
    "partitionDimensions": ["payment_debited_access_manager_identifier"],
    "targetRowsPerSegment": 5000000,
    "maxRowsPerSegment": 10000000
  }
}
```

**Dimension Selection Rationale:**

| Dimension | Cardinality | Suitability | Decision |
|-----------|-------------|-------------|----------|
| payment_debited_access_manager_identifier | 5,500       | Optimal range (1K-50K) | Selected |
| payment_crebited_access_manager_identifier | 5,500       | Identical to debited | -/-      |
| settlement_transaction_type | --          | Too low (skew risk) | Rejected |
| settlement_transaction_status | --          | Too low (extreme skew) | Rejected |
| payment_currency_code | 1           | No variance | Rejected |
| settlement_transaction_identifier | Unique      | Too high (no locality) | Rejected |

**Functional Requirements Alignment:**

Per DESP_UR_SDCA_1000 (Transaction Status Query), the primary selection criterion is "the identifier of the Access Manager on the sender/receiver side." Query pattern analysis indicates Sender (debited) is filtered in 80%+ of queries, making it the optimal partitioning candidate.

---

## 4. Dimension Ordering

Dimensions are ordered to maximize bitmap index efficiency and compression:

```json
{
  "dimensions": [
    "payment_debited_access_manager_identifier",
    "payment_crebited_access_manager_identifier",
    "settlement_settlement_transaction_type",
    "settlement_settlement_transaction_status",
    "payment_currency_code",
    "uetr",
    "settlement_settlement_transaction_identifier",
    "payment_payer_identifier",
    "payment_payee_identifier",
    "payment_online_payment_identifier",
    "funding_online_funding_identifier",
    "defunding_online_defunding_identifier"
    ...
  ]
}

```

   > Primary filters are positioned first -> followed by medium-cardinality dimensions -> with high-cardinality identifiers last to minimize bitmap index overhead.


---

## 5. Segment Sizing and Storage

### 5.1 Target Specifications

- Rows per segment: 5,000,000
- Physical size: 500-700 MB (Druid recommended range)
- Compression ratio: 2:1 (columnar compression)

### 5.2 Volume Projections

| Scenario | Rows/Day | Segments/Day | Storage (7-day) |
|----------|----------|--------------|-----------------|
| Average  | 138M     | 28           | 245 GB          |
| Peak     | 1,382M   | 276          | 2,450 GB        |

---

## 6. Compaction Strategy

**Configuration:**
```json
{
  "skipOffsetFromLatest": "PT24H",
  "taskPriority": 25,
  "partitionsSpec": {
    "type": "range",
    "partitionDimensions": ["payment_debited_access_manager_identifier"],
    "targetRowsPerSegment": 5000000
  }
}
```

**Key Parameters:**
- PT24H offset prevents ingestion/compaction conflicts
- Priority 25 ensures ingestion precedence
- Range partitioning applied during compaction for optimal segment structure

---

## 7. Performance Testing Plan

### 7.1 Test Scenarios

**Scenario 1: Dynamic Partitioning (Baseline)**
- Establish baseline metrics without secondary partitioning
- Measure query latency, segment scan counts, storage utilization

**Scenario 2: Range Partitioning on Debited Access Manager**
- Validate performance improvements for primary query patterns
- Measure segment pruning effectiveness and compression gains

Testing debited and credited Access Manager identifiers separately is unnecessary as they share identical characteristics (cardinality: 5,500; distribution: balanced; partition count: ~28/day). Results would be mathematically equivalent.

### 7.2 Test Query Coverage

| Query Type | Filter Dimension |
|------------|------------------|
| Sender filter | payment_debited_AM |
| Receiver filter | payment_crebited_AM |
| Both filters | debited + crebited |
| Aggregation | None |
| Point lookup | transaction_id |

**Trade-off:** Receiver-only queries will not benefit from partition pruning. This is acceptable given their minority volume and the efficiency of bitmap indexes on 5,500-cardinality dimensions.

### 7.3 Success Criteria

- Query latency P95: Less than 500ms for Access Manager filtered queries
- Segment scan reduction: Greater than 70% for filtered queries
- Storage efficiency: __% reduction vs baseline
- Compaction lag: Less than 24 hours
- Near real-time latency: Less than __ seconds from Kafka to queryable

---

## 8. Implementation Timeline

| Phase | Activities |
|-------|------------|
| Phase 1: Baseline | Deploy dynamic partitioning, establish baseline metrics |
| Phase 2: Range Partitioning | Enable range partitioning, measure improvements |
| Phase 3: Optimization | Fine-tune compaction, monitor performance |

---

## 9. Approval


**Review by:**  
/___________________________________________________________

**Status:** (`Approved`    `Approved with Conditions`  `Requires Revision`)

**Comments:**
________________________________________________________________________________
________________________________________________________________________________
________________________________________________________________________________

---

## Document History

| Version | Date | Author | Changes                                         |
|---------|------|--------|-------------------------------------------------|
| 1.0     | 2025-12-10 | MK.    | Initial draft with assumptions                  |
| 1.1     | 2025-12-17 | MK.    | Revised to range partitioning on Access Manager |
