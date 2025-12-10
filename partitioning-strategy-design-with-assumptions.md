# Druid Partitioning Strategy Design Document
## Transactions Datasource

**Document Version:** 1.0  
**Date:** December 10, 2025  
**Author:**  
**Status:** Draft - Pending Validation

---

## Quick Reference

```
DECISION SUMMARY
─────────────────────────────────────────────────────
Segment Granularity    : DAY
Query Granularity      : MINUTE
Partitioning Strategy  : HASH on account_id (assumed)
Target Segment Size    : 500 MB (~1M rows)
Daily Segments (avg)   : ~138 partitions
Daily Segments (peak)  : ~1,382 partitions
Storage (10-year)      : 170 TB compressed, 258 TB raw
Compaction Strategy    : Tiered (6h/daily/weekly/monthly)

KEY ASSUMPTIONS (REQUIRE VALIDATION)
• Transaction volume: 1,600 tx/sec avg, 16,000 tx/sec peak
• Partition dimension: account_id (high cardinality expected)
• Query pattern: 80% filter by account, 60% by type
• Compression ratio: 3:1 (standard for transaction data)
```

---

## Table of Contents

1. [Context and Objectives](#context-and-objectives)
2. [Data Characteristics](#data-characteristics)
3. [Partitioning Strategy](#partitioning-strategy)
4. [Dimension Design](#dimension-design)
5. [Capacity Planning](#capacity-planning)
6. [Compaction Strategy](#compaction-strategy)
7. [Performance Considerations](#performance-considerations)
8. [Risk Analysis](#risk-analysis)
9. [Validation Plan](#validation-plan)
10. [Approval](#approval)

---

## Context and Objectives

### Business Context

The transactions datasource supports a high-volume settlement data transaction system:

- Daily batch collection with 10-year retention for compliance
- Real-time (U2A) and A2A query processing, 24/7/365 availability
- No negative impact on DESP performance during query processing

### Objectives

We need to balance four competing priorities:

1. **Performance**: Sub-second query latency for recent data (P95 < 500ms)
2. **Scalability**: Handle 20% annual growth without redesign
3. **Efficiency**: Keep storage costs reasonable over 10 years
4. **Reliability**: Minimize operational overhead (99%+ compaction success)

### Scope

**In Scope:** Time granularity, secondary partitioning, sizing, compaction, dimension ordering

**Out of Scope:** Data source implementation, ingestion pipeline, infrastructure provisioning

---

## Data Characteristics

### Volume Projections

Starting with the settlement system capacity requirements:

| Metric | Average | Peak |
|--------|---------|------|
| **Transactions/day** | 138,240,000 | 1,382,400,000 |
| **Daily data volume** | 70.8 GB | 707.8 GB |
| **10-year total** | 258 TB raw | 2.58 PB peak |

At 512 bytes per row (400B dimensions + 100B metrics + 12B overhead), we're looking at roughly 70-700 GB per day depending on load.

### Expected Query Patterns

Based on similar settlement systems I've worked with, we can expect:

- **Real-time monitoring** (high frequency): Last 1-24 hours, need < 500ms response
- **Daily reporting** (medium frequency): Last 7-30 days, can tolerate 1-2 seconds
- **Historical analysis** (low frequency): Months to years, up to 6 seconds acceptable
- **Ad-hoc queries** (variable): Unpredictable, but usually recent data

Assumed primary filters: account_id (~80%), transaction_type (~60%), status (~50%), region (~30%). These percentages need validation from actual query logs.

**Note:** Weekend volume typically drops to ~30% of weekday average in financial systems.

---

## Partitioning Strategy

### Time Granularity Decision

#### Why DAY for Segment Granularity

I initially considered HOUR granularity since we have high transaction rates, but the math doesn't support it:

| Granularity | Segments (10y) | Avg Size | Why Not? |
|-------------|----------------|----------|----------|
| HOUR | 87,600 | 2.95 GB | Too many segments to manage, metadata overhead kills us |
| **DAY** | **3,650** | **70.8 GB** | **Sweet spot** |
| MONTH | 120 | 2.1 TB | Segments too large, queries would scan unnecessary data |

With DAY granularity:
- We get ~3,650 segments over 10 years (manageable)
- Each daily segment can be split into ~138 partitions at 1M rows each
- Aligns perfectly with the daily data collection requirement
- Most queries target daily/weekly/monthly ranges anyway

The 70 GB average segment size might seem large, but remember it's split into partitions internally. At peak (707 GB), we'll have ~1,382 partitions per day, which is still reasonable for the cluster.

#### Query Granularity: MINUTE

Going with MINUTE for query granularity because:
- Real-time monitoring needs fine-grained buckets
- Fraud detection typically looks at minute-level patterns
- We can always aggregate up to hour/day for reports
- Storage impact is acceptable: ~470 MB/min at peak after compression

SECOND granularity would be overkill (14 GB/sec at peak), and HOUR is too coarse for real-time use cases.

### Secondary Partitioning Strategy

#### Hash Partitioning on account_id

**Decision:** HASH partitioning on `account_id`

I'm proposing account_id as the partition dimension based on these assumptions:
- Settlement systems typically have >1M accounts (need to confirm)
- ~80% of queries filter by account (based on similar projects)
- Even hash distribution should prevent hot-spotting

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

**Why this works:**
- Hash on high-cardinality dimension = even distribution
- Queries filtering by account only scan 1-5 partitions instead of all 138
- That's a 60-80% reduction in I/O for most queries

**Validation needed:**
We MUST verify account_id cardinality from actual data. If it's < 100K accounts, we need to switch to transaction_id or a composite key. I've seen this assumption fail before on smaller systems.

**Fallback options if account_id doesn't work:**
- Plan B: transaction_id (guaranteed high cardinality but less query-friendly)
- Plan C: Composite key (account_id + timestamp_hour)
- Plan D: RANGE partitioning on timestamp with HASH on transaction_id

**Partition sizing targets:**
- Target: 1M rows (~500 MB) - proven sweet spot in Druid
- Max: 2M rows (~1 GB) to handle peak bursts
- Daily: ~138 partitions average, ~1,382 at peak

---

## Dimension Design

### Primary Filter Dimensions

Based on transaction system patterns, proposing this ordering:

| Priority | Dimension | Expected Cardinality | Filter Frequency | Status |
|----------|-----------|---------------------|------------------|---------|
| 1 | `account_id` | High (>1M assumed) | ~80% | **Must validate** |
| 2 | `transaction_type` | Low (15-20) | ~60% | **Must validate** |
| 3 | `status` | Very low (3-5) | ~50% | **Must validate** |
| 4 | `region` | Low (3) | ~30% | Confirmed |
| 5 | `timestamp` | N/A | 100% (implicit) | Confirmed |

**Ordering logic:** Put highest-cardinality, most-filtered dimensions first. This is standard practice but needs confirmation from actual query logs.

**Clustering strategy:**
- Primary cluster by account_id to co-locate account transactions
- Secondary cluster by transaction_type for common account+type queries

**Important:** These filter frequencies are educated guesses. We need 30 days of query logs to validate before finalizing dimension order.

---

## Capacity Planning

### Segment Sizing Analysis

Target: 500 MB per partition (1M rows at 512 bytes/row)

**Daily breakdown:**

| Scenario | Rows | Partitions | Storage (raw) | Storage (compressed 2x replication) |
|----------|------|------------|---------------|-------------------------------------|
| Average day | 138M | ~138 | 70.8 GB | 47 GB |
| Peak day | 1,382M | ~1,382 | 707.8 GB | 472 GB |

### Growth Projections

Assuming 20% annual growth (conservative for fintech):

| Timeframe | Daily Rows | Daily Storage (compressed) | Annual Storage |
|-----------|------------|----------------------------|----------------|
| **Today** | 138M | 47 GB | 17 TB |
| **12 months** | 198M | 68 GB | 25 TB |
| **24 months** | 238M | 81 GB | 30 TB |
| **10 years** | — | — | 170-300 TB |

The 170-300 TB range for 10 years accounts for compounding growth. Budget should plan for 300 TB to be safe.

### Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Query latency (P95, recent) | < 500ms | Account-filtered, last 24h |
| Query latency (P95, historical) | < 2,000ms | Multi-day range queries |
| Ingestion throughput | 16,000 events/sec | Sustained peak load |
| Segment scan efficiency | > 80% | Partitions actually read vs total |
| Compaction success rate | > 99% | Daily measurement |

These targets are based on Druid best practices and similar high-volume deployments.

---

## Compaction Strategy

### Configuration Approach

Compaction is critical here because we're ingesting at high rates and will accumulate many small segments without it.

**Core config:**

```json
{
  "dataSource": "transactions",
  "taskPriority": 25,
  "maxRowsPerSegment": 1024000,
  "skipOffsetFromLatest": "PT6H",
  "tuningConfig": {
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

**Key parameters:**
- `skipOffsetFromLatest: PT6H` - Don't compact last 6 hours to avoid conflicts with active ingestion
- `taskPriority: 25` - Lower than ingestion (50) so it doesn't interfere
- `maxRowsInMemory: 500K` - Conservative to avoid OOM issues

### Tiered Schedule

Rather than one-size-fits-all compaction, we need tiered approach:

**Short-term (6-24h old data):**
- Run every 6 hours continuously
- Quick consolidation of newly closed segments
- Low priority, opportunistic

**Near-term (1-7d old data):**
- Run daily at 02:00-06:00 (assumed low-traffic window)
- Consolidate into optimal daily partitions
- Higher priority, should complete within 4 hours

**Medium-term (7-30d old data):**
- Run weekly on weekends
- Further reduce segment count
- Can take longer, less time-sensitive

**Long-term (30d+ old data):**
- Run monthly during maintenance windows
- Final optimization for cold data
- Lowest priority, can be deferred if needed

### Expected Outcomes

From experience with similar setups:
- Segment count reduction: 20-40% for historical data
- Query performance: 15-35% faster for multi-day queries
- Storage: 5-15% savings from better compression
- Compaction time: Should complete in < 1 hour per day under normal load

### Operational Notes

- Keep original segments for 48h before deletion (allows rollback if compaction issues)
- Alert if compaction queue grows beyond 2x expected duration
- Consider dedicated compaction workers if regular workers get overloaded
- Monitor compaction task failures closely - they're usually early warning signs

---

## Performance Considerations

### How Partition Pruning Works

With DAY segments + HASH partitioning on account_id:

**Example query: "Show transactions for account X in last hour"**
1. Time filter → Scan only today's segment (1 day segment)
2. account_id filter → Hash lookup → Scan only 1-5 partitions out of ~138
3. Result: Reading ~1-4% of data instead of 100%

**Query performance expectations:**

| Query Pattern | Partitions Scanned | Expected Latency |
|---------------|-------------------|------------------|
| Single account, last hour | 1-5 of 138 (~1-4%) | < 300ms |
| Account + type, last 7 days | 7-35 total | < 1,500ms |
| Region query, 30 days | All partitions in region | < 6,000ms |
| Full scan, historical | All segments | Minutes (rare case) |

### Trade-offs We're Making

**Pros of this approach:**
- Manageable segment count over 10 years
- Good query performance for common patterns
- Straightforward to maintain and troubleshoot
- Scales with volume growth

**Cons:**
- Large daily segments (70 GB avg, 707 GB peak)
- Queries on very recent data still read larger segment files (mitigated by partition pruning)
- Need careful monitoring to catch partition skew

**Mitigation strategies:**
- Segment cache for last 7 days (hot data)
- Result caching for common queries
- Parallel execution leverages multiple partitions
- Compaction keeps segments optimized

### Monitoring Essentials

**Critical metrics to watch:**

| Metric | Target | Alert If |
|--------|--------|----------|
| Query latency P95 | < 500ms | > 1,000ms |
| Segment scan efficiency | > 80% | < 60% |
| Compaction success | > 99% | < 95% |
| Compaction duration | < 1h/day | > 4h/day |
| Ingestion lag | < 5 min | > 15 min |

**What to tune if performance degrades:**
1. Check partition distribution - might have hot shards
2. Verify compaction is keeping up
3. Look at query patterns - might need dimension reordering
4. Consider increasing segment cache size
5. Scale workers if resource-bound

---

## Risk Analysis

### Major Risks and Mitigation

**Risk 1: Volume is 10x higher than expected**
- Likelihood: Low
- Impact: High (cluster can't keep up)
- Mitigation:
    - Fall back to HOUR segmentation (already sized it out)
    - Increase maxPartitionSize to 5M rows
    - Add worker nodes (already have capacity plan for 2x growth)
    - Implement aggressive compaction

**Risk 2: account_id cardinality too low (<100K)**
- Likelihood: Medium
- Impact: High (poor partition distribution, hot shards)
- Mitigation:
    - Switch to transaction_id (guaranteed unique)
    - Use composite key (account_id + region)
    - Retest partition distribution before go-live
    - Keep 72h rollback window

**Risk 3: Query patterns completely different from assumptions**
- Likelihood: Medium
- Impact: Medium (suboptimal performance)
- Mitigation:
    - Get actual query logs ASAP for validation
    - Reorder dimensions based on real usage
    - Consider multi-dimensional partitioning
    - Document actual patterns in runbook

**Risk 4: Compaction can't keep up at peak**
- Likelihood: Low
- Impact: Medium (segment sprawl, slower queries)
- Mitigation:
    - Increase compaction worker pool
    - Adjust compaction windows to off-peak times
    - Implement batch compaction for catch-up
    - Alert on compaction backlog

**Risk 5: Storage costs exceed budget**
- Likelihood: Low
- Impact: Medium (budget overrun)
- Mitigation:
    - Move >90d data to cold storage tier
    - Review retention policy (maybe 10 years is overkill?)
    - Improve compression settings
    - Archive least-accessed historical data

### Fallback Scenarios

**If performance targets not met after 2 weeks:**
1. Enable aggressive result caching
2. Increase segment replication for hot data
3. Revisit dimension ordering with actual query patterns
4. Consider switching to HOUR segmentation for recent data only

**If partition distribution is skewed:**
1. Switch partitioning dimension within 1 week
2. Test composite keys
3. Adjust hash function if needed
4. Keep original segments for 72h rollback

**If ingestion can't sustain peak load:**
1. Immediate: Scale Kafka consumer tasks
2. Short-term: Add ingestion workers
3. Long-term: Implement backpressure handling
4. Fallback: Temporarily reduce parallelism if cluster unstable

---

## Validation Plan

### Pre-Implementation Tasks

Before we go live, we need to validate these assumptions:

| Task | Owner | Duration | Deadline | Method |
|------|-------|----------|----------|--------|
| **Query log analysis** | Lead Data Engineer | 3 days | -2 weeks | Druid console, export last 30d logs |
| **Cardinality check** | DBA | 1 day | -2 weeks | Sample 1M rows from source DB |
| **Hash distribution test** | Platform Engineer | 2 days | -10 days | Python script on sample data |
| **Canary compaction** | Data Engineer | 3 days | -1 week | Test cluster with 2 days real data |
| **Performance benchmark** | Platform Engineer | 2 days | -1 week | Run 50 representative queries |
| **Capacity check** | Infrastructure | 1 day | -1 week | Verify cluster can handle load |
| **Monitoring setup** | DevOps | 2 days | -3 days | Grafana dashboards + alerts |
| **Rollback test** | Data Engineer | 1 day | -3 days | Practice restore procedure |

**Total effort:** About 8 person-days spread over 2-3 weeks

### Success Criteria

**Must pass before production:**
- account_id cardinality confirmed > 100K
- Hash distribution variance < 20%
- P95 query latency < 500ms on test data
- Compaction completes in < 1h for 1-day window
- No segments > 1 GB after compaction
- All monitoring dashboards working

**Yellow flags (proceed with caution):**
- account_id cardinality 50K-100K (watch for hot shards)
- Hash variance 20-30% (monitor closely)
- Compaction takes 1-2h (may need more workers)

**Red flags (stop and revisit):**
- account_id cardinality < 50K
- Hash variance > 30%
- P95 latency > 1,000ms
- Compaction takes > 4h

### Post-Launch Monitoring

**Week 1:**
- Daily standup review of metrics
- Watch for partition hot spots
- Verify compaction completion rates
- Be ready to rollback if needed

**Weeks 2-4:**
- Weekly performance reports
- Fine-tune based on actual load
- Validate growth projections
- Document any surprises

**Day 30 review:**
- Formal performance assessment
- Update document with real data
- Plan any needed optimizations
- Close out validation phase

---

## Approval

### Review Workflow

| Stage | Reviewer | Role | Status | Comments |
|-------|----------|------|--------|----------|
| **Technical Review** | [Name] | Lead Data Engineer | Pending | Need feedback on partitioning approach |
| **Architecture Review** | [Name] | Data Architect | Pending | Validate against standards |
| **Performance Review** | [Name] | Platform Engineer | Pending | Confirm capacity assumptions |
| **Business Review** | [Name] | Product Owner | Pending | Verify query requirements |
| **Final Sign-off** | [Name] | Technical Director | Pending | Approve for production |

### Outstanding Items

Before final approval, need to complete:
1. Confirm account_id cardinality from production sample
2. Get 30 days query logs to validate filter frequencies
3. Finalize complete dimension list with actual schema
4. Complete Kafka ingestion spec with real topic names

**Estimated time:** 2-3 weeks

### Sign-Off

**Data Architecture Approval:**

- **Approved by:** ________________
- **Title:** ________________
- **Date:** ________________
- **Signature:** ________________

---

## Appendices

### Appendix A: Ingestion Configuration

**Kafka Supervisor (adjust topic/broker as needed):**

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
        "region"
      ]
    },
    "metricsSpec": [
      {"name": "amount", "type": "doubleSum", "fieldName": "amount"}
    ],
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
      "bootstrap.servers": "kafka-broker:9092"
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
    "intermediatePersistPeriod": "PT5M"
  }
}
```

**Note:** Additional dimensions and metrics TBD based on final schema

### Appendix B: Compaction Configuration

```json
{
  "type": "compact",
  "dataSource": "transactions",
  "interval": "2025-12-01/2025-12-02",
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
    },
    "indexSpec": {
      "bitmap": {"type": "roaring"},
      "dimensionCompression": "lz4",
      "metricCompression": "lz4"
    }
  }
}
```

Submit via: `POST /druid/indexer/v1/task`

### Appendix C: Compaction Schedule Visual

```
Compaction Timeline (by data age)
───────────────────────────────────────────────────────────
timeline
    title Compaction Timeline
    0h       : Hot data
    6h       : Short compaction window (every 6h)
    24h      : Near-line compaction (daily 02:00-06:00)
    7d       : Medium window (weekly Sat 02:00)
    30d      : Long window (monthly 1st Sun 02:00)
    1y       : Archive start
    10y      : Archive end


Execution:
  Short  : Every 6h, continuous
  Near   : Daily 02:00-06:00
  Medium : Weekly Sat 02:00
  Long   : Monthly 1st Sun 02:00

Skip last 6h to avoid ingestion conflicts
```

### Appendix D: Quick Reference Links

- [Druid Segment Optimization](https://druid.apache.org/docs/latest/operations/segment-optimization.html)
- [Druid Partitioning Guide](https://druid.apache.org/docs/latest/ingestion/partitioning.html)
- [Compaction Documentation](https://druid.apache.org/docs/latest/data-management/compaction.html)
- [Performance Tuning FAQ](https://druid.apache.org/docs/latest/operations/performance-faq.html)

### Appendix E: Change History

| Version | Date              | Author | Changes |
|---------|-------------------|--------|---------|
| 1.0 | December 09, 2025 |  | Initial design with validation plan |

---

## Document Status

**Current state:** ~85% complete - core strategy defined, awaiting data validation

**Next actions:**
1. Execute validation plan (1 weeks)
2. Update assumptions with real data
3. Schedule architecture review
4. Get final approvals


---

**End of Document**
