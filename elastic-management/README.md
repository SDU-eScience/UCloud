# elastic-management

Elastic-management uses different arguments to handle different jobs:

- *"--setup"*  
   Automates setup of [watermarks](https://www.elastic.co/guide/en/elasticsearch/reference/6.4/disk-allocator.html) 
   and [index templates](https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-templates.html) 
   for a Elasticsearch cluster.
   Watermarks is a way of gracefully limiting usage of data-nodes. The default limits are:
   * **Low:** Prevents Elasticsearch in allocation new shards to a node that has `less than 50GB` 
   of available space left.
   * **High:** Elasticsearch tries to reallocate shards to other nodes if the node with low space has 
   `less than 25GB` of available space left.
   * **Flood:** When a node has `less than 10GB` Elasticsearch changes all indices that have a shard on the flooded node into a 
   read/delete only state. At this point manual interventions is needed(see --removeFlod). 
   This requires the owner of the cluster to clean up the node and manually remove the flood limitation.
- *"--cleanup"*  
   Meant to run as a daily cron job. Goes through all indices to find expired [auditing](../service-common/wiki/auditing.md) 
   information given by the `expiry` field in the entries. Also takes all audit entries from the day before and 
   [shrinks](https://www.elastic.co/guide/en/elasticsearch/reference/master/indices-shrink-index.html)
   the indices to only contain 1 shard. We are lowering the number of shards to 1
   since we will no longer write to this index and only rarely read from it. By doing this we reduce 
   overhead and also the storage used by the index.
- *"--reindex"*  
   Meant to run as a weekly cron job. Takes the audit entries from the week before and 
   [reindex](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html)
   them into weekly indices for each audit type.
- *"--backup"*  
   Intended to be a cronjob creating a incremental 
   [snapshot](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-snapshots.html).
- *"--removeFlood"*  
  Can be used to quickly remove the read/delete only state enforced by the Watermark when reaching flood
  level once the cluster have been cleaned up or given more storage. 
- *"--monthlyReduce"*  
  Meant to run as a monthly cron job. Takes all audit logs for the past month and reindex them 
  into a single index for the month using the template: http_logs_AUDITNAME-monthly-01.mm.yyyy-LastDayOfMonth.mm.yyyy 
- *"--reduceLastQuarter"*  
  Intended to be a cron job run each 3rd month. This has a dependency on the --monthlyReduce, since 
  it requires the indices it reduces to contain the "monthly" keyword. 
- *"--deleteEmptyIndices"*  
  Used for deleting all empty indices in the cluster.
  
## Shrinking
When running *"--cleanup"* the shrinking process takes place. The reason that we shrink each day are multiple:
- There are a small overhead in storage usage in having multiple shards per index
- Shards are usually used for faster write and search. Since we make daily indices we no longer have a need for larger 
write capacity and we rarely have a need for searching.
- The cluster has a hard limit of shards per node. If this limit is exceeded the cluster will not write new shards even 
if there is plenty of storage left. The limit is optimal when between 2-5000 per node. By reducing the number of shards '
in unused indices we can easier keep each node beneath the limit.

The procedure of shrinking requires multiple steps. 
1. Preparing source index: 
  The source index needs to be on the same node before a shrink can happen. The index also needs to be blocked from further 
  writes.
  The process of moving all data of the index to a single node can be time consuming and there for we need to wait for the
  cluster to finish moving before we can continue the process.
2. Shrinking:
  When shrinking a new index is created. This uses the same name, but postfixes it with \"\_small\" and only has a single 
  primary shard and a single replica. This greatly reduces the number of shards overall since we create 100+ indices of 
  logs each day.  
  Once the new index has been created a merge operation can be made which reduces the number of segmentes the index uses.
  This is not a requirement, but it reduces the storage consumption of the index.
3. Deletion:
  Once the shrink has completed we are left with duplicate data. The original index and the new smaller (shardswise) index.
  To finish the shrink we delete the old index from the cluster.
  
### Potential errors and how they are handled
When doing the daily reduction of shards multiple things can happen:
- Allocation to single node not done: Even if we allocate all the shards to one node in a seperate step, there could have 
  been a short instance where the number of reallocating shards was 0, but in fact not done yet. If this is the case,
  Elasticsearch will throw a internal error. In this case we attempt to wait once more for reallocation before we retry 
  the shrink.
- Index already exists: In the case that the cleanup job crashes between a shrink has started and the old index is not 
  yet deleted, the old index would be added to the list of indices to shrink when the cron job restarts.
  When trying to shrink this index Elasticsearch would complain that the index is already there and fail the job.
  If this error is thrown we catch it and makes a check to see if the number of documents in both indices match. 
  If they do match we delete the old index, otherwise we delete the new index and do retry the shrink.
- Timeout when merging: Since the elastic-management code does not use the async API a timeout can happen if the merge 
  operation takes longer than 60s. In this case we catch the exception and writes the event to log and waits for a few seconds.
  There is no need to try to redo the operation since the operation has started, and will continue eventhough a timeout happen.
