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
   read/delete only state. At this point manual interventions is needed. 
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
   [reindexed](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html)
   them into week indices for each audit type.
- *"--backup"*  
   Intended to be a cronjob creating a incremental 
   [snapshot](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-snapshots.html).
