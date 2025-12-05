# Questions for Definitive Partitioning Strategy Design
## Transactions Datasource

This document lists all questions that must be answered to create the definitive partitioning strategy design document. Questions are organized by section to align with the design document structure.

---

## Data Volume and Characteristics

### Transaction Volume

1. **What is the expected number of transactions per day?**
   - Average daily volume: [ ]
   - Peak daily volume: [ ]
   - Seasonal variations: [ ]

2. **What is the expected number of transactions per hour?**
   - Average hourly volume: [ ]
   - Peak hourly volume: [ ]
   - Peak hours of the day: [ ]

3. **What is the expected growth rate?**
   - Year-over-year growth: [ ]%
   - Expected growth over next 12 months: [ ]%
   - Expected growth over next 24 months: [ ]%

### Data Size

4. **What is the average size of a transaction record?**
   - Average row size in bytes: [ ]
   - Minimum row size: [ ]
   - Maximum row size: [ ]

5. **What is the expected daily data volume?**
   - Raw data size per day: [ ] GB/TB
   - Compressed data size per day: [ ] GB/TB

### Data Retention

6. **What is the data retention requirement?**
   - Retention period: [ ] days/months/years
   - Compliance requirements: [ ]
   - Archive strategy: [ ]

---

## Query Patterns and Requirements

### Query Types and Frequency

7. **What are the primary query types?**
   - Real-time monitoring queries: [ ] (frequency: High/Medium/Low)
   - Daily reporting queries: [ ] (frequency: High/Medium/Low)
   - Historical analysis queries: [ ] (frequency: High/Medium/Low)
   - Ad-hoc exploration queries: [ ] (frequency: High/Medium/Low)

8. **What are the typical time windows for queries?**
   - Real-time queries: Last [ ] hours/days
   - Daily reporting: Last [ ] days/weeks
   - Historical analysis: Last [ ] months/years
   - Ad-hoc queries: Variable ranges from [ ] to [ ]

### Query Performance Requirements

9. **What are the query performance SLAs?**
   - P50 latency target: [ ] ms
   - P95 latency target: [ ] ms
   - P99 latency target: [ ] ms
   - Maximum acceptable latency: [ ] ms

10. **What is the expected query load?**
    - Queries per hour (average): [ ]
    - Queries per hour (peak): [ ]
    - Concurrent queries: [ ]

### Query Dimensions

11. **Which dimensions are most frequently used in WHERE clauses?**
    - Dimension 1: [ ] (used in [ ]% of queries)
    - Dimension 2: [ ] (used in [ ]% of queries)
    - Dimension 3: [ ] (used in [ ]% of queries)
    - Other frequently filtered dimensions: [ ]

12. **What are the typical filter patterns?**
    - Exact match filters: [ ] (dimensions: [ ])
    - Range filters: [ ] (dimensions: [ ])
    - IN clause filters: [ ] (dimensions: [ ])
    - Pattern matching: [ ] (dimensions: [ ])

---

## Dimensions and Schema

### Dimension List

13. **What are all the dimensions in the transactions datasource?**
    - Complete list: [ ]
    - Dimension types (STRING, LONG, FLOAT, etc.): [ ]

14. **What is the cardinality of each dimension?**
    - account_id: [ ] unique values
    - transaction_type: [ ] unique values
    - status: [ ] unique values
    - merchant_id: [ ] unique values
    - region: [ ] unique values
    - [Other dimensions]: [ ] unique values

### Primary Filter Dimensions

15. **Which dimensions are used as primary filters (most frequent in WHERE clauses)?**
    - Rank 1: [ ] (appears in [ ]% of queries)
    - Rank 2: [ ] (appears in [ ]% of queries)
    - Rank 3: [ ] (appears in [ ]% of queries)
    - Rank 4: [ ] (appears in [ ]% of queries)
    - Rank 5: [ ] (appears in [ ]% of queries)

16. **What are the typical filter values for primary filter dimensions?**
    - account_id: [ ] (single values, ranges, patterns)
    - transaction_type: [ ] (common values: [ ])
    - status: [ ] (common values: [ ])
    - [Other primary filters]: [ ]

### Dimension Characteristics

17. **Are there any high-cardinality dimensions?**
    - Dimension: [ ] (cardinality: [ ])
    - Impact on partitioning: [ ]

18. **Are there any low-cardinality dimensions used frequently?**
    - Dimension: [ ] (cardinality: [ ])
    - Usage pattern: [ ]

---

## Time Granularity Requirements

### Segment Granularity

19. **What is the required time precision for data segmentation?**
    - Minimum granularity needed: [ ] (MINUTE, HOUR, DAY, etc.)
    - Business justification: [ ]

20. **What are the constraints on segment size?**
    - Minimum acceptable segment size: [ ] MB
    - Maximum acceptable segment size: [ ] MB
    - Target segment size: [ ] MB

21. **What is the ingestion frequency?**
    - Real-time ingestion: [ ] Yes/No
    - Batch ingestion frequency: [ ] (every X minutes/hours)
    - Ingestion latency requirement: [ ]

### Query Granularity

22. **What is the required time precision for queries?**
    - Minimum granularity needed: [ ] (SECOND, MINUTE, HOUR, DAY, etc.)
    - Business justification: [ ]
    - Typical aggregation levels: [ ]

23. **Are there specific time-based analysis requirements?**
    - Fraud detection time windows: [ ]
    - Real-time monitoring precision: [ ]
    - Historical analysis precision: [ ]

---

## Partitioning Strategy

### Secondary Partitioning

24. **Which dimensions should be used for secondary partitioning?**
    - Primary partitioning dimension: [ ]
    - Secondary partitioning dimension (if any): [ ]
    - Justification: [ ]

25. **What type of partitioning is preferred?**
    - Hash partitioning: [ ] Yes/No
    - Range partitioning: [ ] Yes/No
    - Single dimension partitioning: [ ] Yes/No
    - Justification: [ ]

26. **What are the partitioning size targets?**
    - Target partition size (rows): [ ]
    - Maximum partition size (rows): [ ]
    - Minimum partition size (rows): [ ]

### Clustering Strategy

27. **Should dimensions be clustered within segments?**
    - Clustering required: [ ] Yes/No
    - Clustering dimensions: [ ]
    - Clustering order: [ ]

---

## Sizing and Capacity

### Segment Sizing

28. **What is the target segment size?**
    - Target size: [ ] MB (recommended: 300-700 MB)
    - Justification: [ ]

29. **What is the expected number of rows per segment?**
    - Rows per segment (average): [ ]
    - Rows per segment (peak): [ ]
    - Calculation method: [ ]

### Capacity Planning

30. **What is the storage capacity requirement?**
    - Current storage: [ ] TB
    - 6-month projection: [ ] TB
    - 12-month projection: [ ] TB
    - 24-month projection: [ ] TB

31. **What is the replication factor?**
    - Replication factor: [ ] (typically 2-3)
    - High availability requirements: [ ]

32. **What is the expected compression ratio?**
    - Expected compression ratio: [ ]:1
    - Compression algorithm: [ ]

---

## Compaction Strategy

### Compaction Requirements

33. **What is the compaction schedule requirement?**
    - Compaction frequency: [ ] (hourly, daily, weekly)
    - Compaction time window: [ ]
    - Compaction priority: [ ]

34. **What are the compaction objectives?**
    - Segment size optimization: [ ] Yes/No
    - Query performance improvement: [ ] Yes/No
    - Storage efficiency: [ ] Yes/No
    - Other objectives: [ ]

35. **What is the acceptable compaction lag?**
    - Maximum lag before compaction: [ ] hours
    - Data freshness requirement: [ ]

---

## Performance and Operations

### Performance Targets

36. **What are the specific performance targets?**
    - Query latency P50: [ ] ms
    - Query latency P95: [ ] ms
    - Query latency P99: [ ] ms
    - Ingestion throughput: [ ] events/second

37. **What is the expected segment scan efficiency?**
    - Target efficiency: [ ]% (segments scanned vs. total)
    - Current efficiency (if applicable): [ ]%

### Operational Considerations

38. **What are the operational constraints?**
    - Maintenance windows: [ ]
    - Resource constraints: [ ]
    - Cost constraints: [ ]

39. **What monitoring and alerting is required?**
    - Key metrics to monitor: [ ]
    - Alert thresholds: [ ]
    - SLA requirements: [ ]

---

## Business Context

### Business Requirements

40. **What are the business drivers for this partitioning strategy?**
    - Primary business need: [ ]
    - Success criteria: [ ]
    - Business impact: [ ]

41. **Are there any compliance or regulatory requirements?**
    - Compliance requirements: [ ]
    - Data governance requirements: [ ]
    - Audit requirements: [ ]

### Stakeholders

42. **Who are the key stakeholders?**
    - Data architecture team contact: [ ]
    - Platform engineering team contact: [ ]
    - Business stakeholders: [ ]
    - Approval authority: [ ]

---

## Technical Environment

### Infrastructure

43. **What is the Druid cluster configuration?**
    - Cluster size: [ ] nodes
    - Node specifications: [ ]
    - Available resources: [ ]

44. **What is the ingestion method?**
    - Real-time ingestion: [ ] Yes/No
    - Batch ingestion: [ ] Yes/No
    - Ingestion tool: [ ] (Kafka, Kinesis, etc.)

### Integration

45. **How will this datasource integrate with other systems?**
    - Downstream systems: [ ]
    - Integration requirements: [ ]
    - Data sharing requirements: [ ]

---

## Validation and Approval

### Review Process

46. **What is the review and approval process?**
    - Reviewers: [ ]
    - Review timeline: [ ]
    - Approval criteria: [ ]

47. **What documentation is required for approval?**
    - Required documents: [ ]
    - Approval format: [ ]
    - Sign-off process: [ ]

---

## Additional Considerations

48. **Are there any specific use cases or edge cases to consider?**
    - Special use cases: [ ]
    - Edge cases: [ ]
    - Known issues: [ ]

49. **Are there any lessons learned from similar implementations?**
    - Previous implementations: [ ]
    - Lessons learned: [ ]
    - Best practices: [ ]

50. **Are there any future requirements to consider?**
    - Planned features: [ ]
    - Expected changes: [ ]
    - Scalability requirements: [ ]

---

## Summary

Once all questions are answered, the definitive design document can be created with:
- Validated data volume and characteristics
- Confirmed query patterns and requirements
- Accurate dimension analysis and ordering
- Justified time granularity decisions
- Optimized partitioning strategy
- Realistic sizing and capacity planning
- Appropriate compaction strategy
- Clear performance targets and monitoring

**Next Steps:**
1. Answer all questions above
2. Validate answers with stakeholders
3. Create definitive design document
4. Review and approve design
5. Implement partitioning strategy

---

**Document Version:** 1.0  
**Last Updated:** [Date]

