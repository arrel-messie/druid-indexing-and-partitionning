As a data engineer,
I want a time and dimension partitioning strategy for transactions datasource
so that queries remain performant at the expected transaction volumes.

**In scope:** Partitioning and time granularity
**Out of scope:** Implementation of data source

**acceptance criteria:**
- `segmentGranularity` and `queryGranularity` are chosen and justified based on expected data volume and time windows of queries
- A secondary partitioning/clustering strategy is documented (e.g., hash/range on 1-2 key dimensions such as account/participant)
- A short sizing note estimates rows per segment and target segment size, with implementations for compaction
- The list of "primary filter dimensions" (those we expect in WHERE clauses most often) is identified and appears early in dimension list
- Design is reviewed and explicitly approved by data architecture