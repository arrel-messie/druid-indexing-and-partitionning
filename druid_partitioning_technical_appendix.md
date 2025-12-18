# Technical Appendix
## Druid Partitioning Strategy - IDM Settlement Transaction 

**Document ID:** IDM-SETTLEMENT-DRUID-TRANSACTION-DATA-PARTITIONNING-2025-001-APPENDIX  
**Version:** 1.0  
**Date:** December 17, 2025  
**Related Document:** IDM-SETTLEMENT-DRUID-TRANSACTION-DATA-PARTITIONNING-2025-001

---

## Appendix A: Detailed Dimension Analysis

This appendix provides comprehensive analysis of main dimensions in the schema used on `transaction status query` to justify the selection of `payment_debited_access_manager_identifier` as the partitioning dimension.

### A.1 payment_debited_access_manager_identifier (SELECTED)

**Characteristics:**
- Cardinality: 5,500 values
- Type: long
- Distribution: Balanced across values
- Query pattern: Primary filter in Transaction Status Query
- Filter frequency: High 

**Suitability:**
Cardinality falls within optimal range (1,000-50,000) per Druid documentation. Expected partition count: approximately 28 per day (138M rows / 5M target). Data locality ensures all transactions from same Access Manager grouped in same segments, enabling effective query pruning.

**Decision:** Selected as partitioning dimension.

---

### A.2 payment_crebited_access_manager_identifier

**Characteristics:**
- Cardinality: 5,500 values
- Type: long
- Distribution: Balanced
- Query pattern: Secondary filter (receiver side)
- Filter frequency: Medium 

**Analysis:**
Identical cardinality and distribution to debited identifier. Only difference is query optimization target. Partitioning on debited optimizes sender queries `assumption on sender query frequency > receiver query frequency`, while partitioning on crebited would optimize receiver queries `assume: (20% volume)`. Mathematical properties identical: same partition count, storage cost, compression ratio.

**Decision:** Not selected. Testing separately would yield redundant results.

---

### A.3 settlement_settlement_transaction_type

**Characteristics:**
- Cardinality: too low (__values)
- Type: string
- Distribution: Likely skewed
- Query pattern: Filtered occasionally
- Filter frequency: Medium

**Analysis:**
Cardinality too low for effective range partitioning. Druid official documentation states this "can often create partitions that are much larger than the target size." Example with 138M rows/day: popular type (50% volume) = 69M rows = 14 segments, while rare type (1% volume) = 1.4M rows = 0.28 segments. Variable segment sizes degrade performance.

**Decision:** Rejected due to low cardinality and skew risk.

---

### A.4 settlement_settlement_transaction_status

**Characteristics:**
- Cardinality:  too low (__values)
- Type: string
- Distribution: Extremely skewed
- Filter frequency: High

**Analysis:**
Typical distribution: `assume status: COMPLETED (70%), PENDING (15%), PROCESSING (10%), FAILED (3%), CANCELLED (2%)`. Extreme skew makes this unsuitable. COMPLETED status would dominate most segments. Queries filtering by status would scan most partitions with minimal pruning benefit.

**Decision:** Rejected due to extreme skew.

---

### A.5 payment_currency_code

**Characteristics:**
- Cardinality: 1 (EUR only)
- Type: string

**Analysis:**
With cardinality of 1, partitioning is impossible. All rows fall into single partition.

**Decision:** Rejected due to no variance.

---

### A.6 settlement_settlement_transaction_identifier

**Characteristics:**
- Cardinality: Unique (~138M values/day)
- Type: long
- Query pattern: Point lookups

**Analysis:**
Druid documentation: "Since the dimension is high cardinality, the bitmap index filtering would likely eliminate many rows from the segment, and the query wouldn't have to scan too much per segment, regardless of where the dimension sits in the ordering." Range partitioning on unique values equivalent to dynamic partitioning: no data locality, no pruning benefit. Bitmap indexes already optimal for point lookups.

**Decision:** Rejected. Too high cardinality, no locality benefit.

---

### A.7 High-Cardinality Identifiers

Dimensions: uetr, payment_payer_identifier, payment_payee_identifier, payment_online_payment_identifier, funding_online_funding_identifier, defunding_online_defunding_identifier

**Common Analysis:**
Very high cardinality (unique or near-unique), even/random distribution, occasional point lookups. Same issues as transaction_identifier: no data locality, bitmap indexes sufficient, partitioning provides no benefit.

**Decision:** Rejected due to high cardinality without query pattern justification.

---

### A.8 Business Date Dimensions

Dimensions: funding_rtgs_business_date, defunding_rtgs_business_date

**Analysis:**
Low cardinality (30-365 values per year), highly correlated with time. Business dates within 1-2 days of event time. Time pruning already filters by date range, making additional partitioning redundant. Additionally, only populated for funding/defunding transactions (minority of volume).

**Decision:** Rejected. Redundant with time partitioning, sparse population.

---

## Appendix B: Query Pattern Analysis

### B.1 Functional Requirements Review

DESP_UR_SDCA_1000 states: "The Settlement and DCA Management component shall provide a Transaction status query to actors, which returns the current status of one transaction. The query shall support the following selection criteria: the identifier of the Access Manager on the sender/receiver side; transaction identifier."

**Query Pattern Frequency (estimated):**
- Access Manager queries: 80-85%
    - Sender (debited): 65-70%
    - Receiver (crebited): 15-20%
    - Both: 5-10%
- Transaction ID lookups: 10-15%
- Aggregation queries: 5-10%

Question: Requirement mentions "sender/receiver" with sender first,does it  indicating priority? In settlement component, senders monitor transaction status more frequently than receivers?

---

### B.2 Performance Analysis by Query Type

#### Sender Filter Query (Optimized)

```sql
WHERE payment_debited_access_manager_identifier = 1234
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
```

**Performance:**
- Time pruning: 7 day-segments
- Dimension pruning: 7 partitions (1 per day for AM 1234)
- Net: 96.4% segment reduction (196 → 7)
- Expected latency: Less than 200ms

---

#### Receiver Filter Query (Not Optimized)

```sql
WHERE payment_crebited_access_manager_identifier = 5678
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
```

**Performance:**
- Time pruning: 7 day-segments
- Dimension pruning: None (not partitioning key)
- Bitmap filtering: Within 196 partitions
- Expected latency: 500-800ms

**Acceptance Rationale:**
Represents 15-20% of query volume. Latency under 1 second acceptable for monitoring use case. Bitmap indexes on 5,500-cardinality dimension highly efficient.

---

#### Both Filters Query (Optimized)

```sql
WHERE payment_debited_access_manager_identifier = 1234
  AND payment_crebited_access_manager_identifier = 5678
  AND __time >= CURRENT_TIMESTAMP - INTERVAL '7' DAY
```

**Performance:**
- Debited filter prunes to 7 partitions
- Crebited filter applied via bitmap within 7 partitions
- Expected latency: Less than 150ms

---

## Appendix C: Configuration Specifications

### C.1 Complete Ingestion Specification

```json
{
  "type": "kafka",
  "spec": {
    "dataSchema": {
      "dataSource": "idm_settlement_transaction",
      "timestampSpec": {
        "column": "__time",
        "format": "millis"
      },
      "dimensionsSpec": {
        "dimensions": [
          {"type": "long", "name": "payment_debited_access_manager_identifier"},
          {"type": "long", "name": "payment_crebited_access_manager_identifier"},
          {"type": "string", "name": "settlement_settlement_transaction_type"},
          {"type": "string", "name": "settlement_settlement_transaction_status"},
          {"type": "string", "name": "payment_currency_code"},
          {"type": "string", "name": "uetr"},
          {"type": "long", "name": "settlement_settlement_transaction_identifier"},
          {"type": "string", "name": "payment_payer_identifier"},
          {"type": "string", "name": "payment_payee_identifier"},
          {"type": "string", "name": "payment_online_payment_identifier"},
          {"type": "string", "name": "funding_online_funding_identifier"},
          {"type": "string", "name": "defunding_online_defunding_identifier"},
          {"type": "string", "name": "funding_rtgs_business_date"},
          {"type": "string", "name": "defunding_rtgs_business_date"}
        ]
      },
      "metricsSpec": [
        {"type": "count", "name": "count"},
        {"type": "longSum", "name": "payment_amount_sum", "fieldName": "payment_payment_amount"}
      ],
      "granularitySpec": {
        "type": "uniform",
        "segmentGranularity": "DAY",
        "queryGranularity": "NONE",
        "rollup": false
      }
    },
    "tuningConfig": {
      "type": "kafka",
      "maxRowsPerSegment": 5000000,
      "maxRowsInMemory": 500000,
      "intermediatePersistPeriod": "PT10M",
      "maxPendingPersists": 2,
      "partitionsSpec": {
        "type": "range",
        "partitionDimensions": ["payment_debited_access_manager_identifier"],
        "targetRowsPerSegment": 5000000,
        "maxRowsPerSegment": 10000000,
        "assumeGrouped": false
      }
    },
    "ioConfig": {
      "type": "kafka",
      "topic": "settlement-transactions",
      "consumerProperties": {
        "bootstrap.servers": "kafka-broker:9092",
        "group.id": "druid-idm-settlement-consumer"
      },
      "taskDuration": "PT1H",
      "useEarliestOffset": false,
      "completionTimeout": "PT30M",
      "inputFormat": {"type": "json"}
    }
  }
}
```

---

### C.2 Complete Compaction Configuration

```json
{
  "dataSource": "idm_settlement_snapshot",
  "taskPriority": 25,
  "inputSegmentSizeBytes": 500000000,
  "skipOffsetFromLatest": "PT24H",
  "tuningConfig": {
    "type": "index_parallel",
    "maxRowsPerSegment": 5000000,
    "maxRowsInMemory": 500000,
    "maxNumConcurrentSubTasks": 4,
    "maxRetry": 3,
    "taskStatusCheckPeriodMs": 60000,
    "partitionsSpec": {
      "type": "range",
      "partitionDimensions": ["payment_debited_access_manager_identifier"],
      "targetRowsPerSegment": 5000000,
      "maxRowsPerSegment": 10000000
    },
    "pushTimeout": 0,
    "splitHintSpec": {
      "type": "maxSize",
      "maxSplitSize": 1073741824,
      "maxInputSegmentBytesPerTask": 10737418240
    },
    "forceGuaranteedRollup": false
  },
  "granularitySpec": {
    "segmentGranularity": "DAY",
    "queryGranularity": "NONE"
  },
  "taskContext": {"priority": 25}
}
```

---

## Appendix D: Testing Methodology

### D.1 Test Environment
```NOTE

```
---

### D.2 Test Execution

**Phase 1: Baseline**
- Data ingestion with dynamic partitioning
- Query performance measurement (100 executions per query)
- Analysis and documentation

**Phase 2: Range Partitioning**
- Configuration change and reingestion
- Compaction
- Query performance measurement
- Comparative analysis

---

### D.3 Metrics Collection

**Per Query:**
- Timestamp, query type, latency, segments scanned, bytes scanned, rows scanned, result size

**System (continuous):**
- CPU/memory utilization, query queue depth, segment scan rate

**Storage (daily):**
- Total segments, storage bytes, compression ratio

---

## Appendix E: Alternative Strategies

### E.1 Hash Partitioning

**Configuration:**
```json
{"partitionsSpec": {"type": "hash", "partitionDimensions": ["payment_debited_access_manager_identifier"], "numShards": 28}}
```

**Analysis:**
Even distribution guaranteed, but low data locality. Hash function distributes same Access Manager across multiple partitions. No query pruning benefit.

**Decision:** Rejected for lack of data locality.

---

### E.2 Range on Transaction Type

**Configuration:**
```json
{"partitionsSpec": {"type": "range", "partitionDimensions": ["settlement_settlement_transaction_type"], "targetRowsPerSegment": 5000000}}
```

**Analysis:**
Low cardinality  creates segment size imbalance. Transaction Status Query does not filter by type. Example: PAYMENT (50%) = 14 segments, RECALL (1%) = 0.28 segments.

**Decision:** Rejected due to low cardinality and query pattern mismatch.

---

## Appendix F: Operational Considerations

### F.1 Key Performance Indicators

**Query:** P50/P95/P99 latency by type, success rate, timeout rate, segments scanned
**Ingestion:** Rows/sec, lag, failed tasks, task duration P95
**Storage:** Segment count, storage bytes, average size, compression ratio, compaction lag
**Cluster:** Node availability, coordinator response time, task queue depth

---

### F.2 Alert Thresholds

**Critical:**
- Query P95 latency greater than 2s (sustained 5min)
- Ingestion lag greater than 5min
- Segment count growth exceeding projection by 50%
- Node unavailable

**Warning:**
- Query P95 latency greater than 1s
- Compaction lag greater than 48h
- Segment count growth exceeding projection by 25%
- CPU greater than 80% (sustained 15min)

---

### F.3 Capacity Planning


---

## Appendix G: Change Log Detail

### Version 2.1 (2025-12-10)

**Modified Sections:**
- Initial draft with assumptions 

**Feedback Addressed:**
- Add functional context
- Avoid over engineering we are at the beginning keep simple
- To match details
- identify levers for performance tuning

---

### Version 1.1 (2025-12-17)

**Major Changes:**
- Redesign to range partitioning
- Correction of dimension cardinalities
- Alignment with functional requirements ands data model definition

**Feedback Addressed:**


---

## Appendix H: References

**Druid Documentation:**
- https://druid.apache.org/docs/latest/ingestion/partitioning/
- https://druid.apache.org/docs/latest/design/segments/
- https://druid.apache.org/docs/latest/data-management/compaction/

**USER Requirements document:**
- DESP_UR_SDCA_1000: Transaction Status Query
- DESP_UR_INTF_0690: Settlement and DCA Management Interface

**Related Documents:**
- IDM Settlement Data Dictionary
