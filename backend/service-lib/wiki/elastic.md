# ElasticSearch

To keep our auditing logging information we use [Elasticsearch](https://www.elastic.co/products/elasticsearch). 

## Configuration and General Setup

The Elasticsearch cluster consists of 3 nodes. To be able to have an efficient cluster of this size each
node have multiple roles in the cluster. Each node is able to handle ingestion of data, data storage, 
node distribution, and client requests. 

Elasticsearch tries to distribute data evenly between its data nodes, however it 
supports that if a node crosses certain thresholds called [watermarks](https://www.elastic.co/guide/en/elasticsearch/reference/current/disk-allocator.html) it
will take action to try to redistribute, if available, or to stop nodes from 
creating more shards. In worst case it makes the node read-only until the problem 
has been resolved. These limitation are divided into 3 different levels and
are shared by both clusters:

- Low: No more shards for replicas can be allocated to this node.
- High: Start to automatically relocate shards away from node to generate more space.
- Flood stage: Each index that has shards on this node will now be put into read_only_allow_delete mode. This mode can
  only
  be removed manually by the cluster admin after fixing the storage problem.

## Logging/Audit

All auditing resides in a single Elasticsearch cluster that only contains logs. All auditing
is collected by the auditing service and then formattet and inserted into the ElasticSearch cluster as shown below.

![Logging overview](/Users/schulz/Desktop/UCloud/backend/service-lib/wiki/newLogFlow.png)

### Cloud Service Auditing Ingestion

The audit logs are created by the users preforming actions on the cloud service and thereby creating events. These
events are stored in Redis. The Audit Ingestion Service then collects these events and places in their respective index.

### Data Retention and Automated Curation

By using our own [Elasticsearch Management Tool](../elastic-management) are we able to keep our shard and indices
consumption at a minimum. A daily scan is run to reduce the number of shards in the audit logs for the previous day
to 1 primary shard and 1 replica shard per index. This would usually mean that indexing and search speed would decrease,
however since this is the logs for the previous day then the need of indexing is no more and search does not have to
fast. On top of this shrinking process we also do a reindex to a monthly index of week old indices. By doing this we
reduce the former weeks daily indices into a single monthly index continually.

During the daily scan we also check old logs to see if any of them have expired according the predetermined retention
period. If the entire index only contains expired logs, then the entire index is delete, otherwise it would only be the
specific logs that are deleted. This gives us the ability to have a fine granularity if some logs needs to be kept
longer than other logs.

Last but not least we do an aliasing of the 7 last days of logs. All these indices will now be included in search when
we search the "Grafana" index. This makes search and response time of Kibana much faster since it does not look in the
last 180 days of logs but instead only the last 7 days.

## Upgrade Procedures

The person responsible for Elasticsearch should be on the Elasticsearch e-mailing list. If a new version is released,
it would be necessary to upgrade the deployed clusters. This is performed by first a rolling upgrade, updating one node 
at a time. This procedure is done manually. The nodes should be removed and added one
at a time and be allowed to rejoin the cluster before proceeding. When an update is applied, the Elasticsearch API used
by the services should also be upgraded and in case of deprecation: Rewrite/update of codebase.

## Security

The Elasticsearch cluster is protected by username and password. Different roles are given to the users so that they
do not have access to more actions than necessary.

## Kibana

[Kibana](https://www.elastic.co/products/kibana) is our dashboard for searching and navigating the large amount
of auditlogs that are generated. This is also username/password protected and the user also have different roles/privileges
according to their needs. By using Kibana we can search logs for a given period in the http_logs. 


