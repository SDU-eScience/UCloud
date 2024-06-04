<p align='center'>
<a href='/docs/developer-guide/core/monitoring/grafana.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/scripts/redis.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring, Alerting and Procedures](/docs/developer-guide/core/monitoring/README.md) / Alerting
# Alerting

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Provides slack alerts due to failures in critical services._

## Rationale

This service monitors data from [auditing](./auditing.md), if the error
rate goes above a threshold an error will be emitted. If the alerting service
at any points fail to contact Elasticsearch an error will be emitted.

At the moment this service is sending alerts to the `#devalerts`/`#alerts`
    channels of slack. The messages are sent to slack via their webhooks feature.
This service is not specified only to work with slack, but can be hooked up to
any chat/mail service that supports webhooks.

## Supported Alerts

**Elasticsearch cluster health**

It is important that the Elasticsearch cluster always is at good health. Elasticsearch can be in three different states:

  - **Green:** All good
  - **Yellow:** Missing replicas of one or more indices
  - **Red:** One or more primary shards have not been allocated

If the state changes to yellow and have not changed back to green within 5 minutes the service will send an 
alert. If the state however changes to red, it will alert after 1 minute. We can live without a replica but if
we are missing a primary shard, then we have unavailable data.

**Elasticsearch storage**

Elasticsearch consists of multiple data nodes. Theses nodes are given an specific amount of storage to their 
disposal. If this storage is reaching pre-configured limits it will trigger and send a message.
UCloud uses the following configuration:

- Send a `information` message clarifying which node has used 85% of its
storage.
- Send a `warning` message clarifying which node has used 88% of its storage.
- Send a `alert` message clarifying which node has used 90% of its storage.

This gives time to either scale up or clean out in the elastic indices before the watermark limits are reached
and further actions are required and limitation are enforced by elastic it self.

**Number of Shards**

Since Elasticsearch 7.0, a hard limit on number of shards per nodes have been introduced.
If this limit is reached then no more shards, and therefore indices, cannot be allocated to
the node.

This alert gives a heads up when running close to the cluster limit. This makes it possible to
take actions before allocations are put on hold. If no configuration is given to the alert, then
a default of 500 available shards are set as the threshold before an alert triggers.

To handle this situation either increase the number of shards allowed per node or reduce the number
of shards/indices in the cluster. To help with the latter the elastic-management service can be used.

Since the number of shards do not change much during a day, this alert is only
checking every 12 hours. Should an alert have fired, it checks every 30 min to be able to notify
once the number of available shards are above the limit again.

**Number of Documents**

If a shard is containing to many documents (entries in Elasticsearch) then it might
become to heavy to perform optimal. We have an alert that triggers if a shard 
exceeds 1.800.000.000 documents and again at 2.000.000.000. Should this trigger
a reindex would be needed to split the original index into multiple indices or
delete some data that might have been missed by the timed scan of logs. 


**5XX status codes**

If request returns a 5XX code, then it could be a sign of something is wrong in either our code or our
infrastructure.

This alert checks the audit trail for request responses that has a 5XX status code.
If the number of request that returns a 5XX status code exceeds a pre-set
percentage of all requests within the last 15 minutes, the alert triggers.

The check runs each 5 minutes and will also notify when the number of 5XX enters the acceptable range again.

**Crash loop and pod failure detection (Kubernetes)**

If any pods in the kubernetes cluster fails due to some error or if a pod keeps restarting and ends in a 
CrashLoppBackOff (multiple failing restarts within short time) this alert will trigger sending a message
specifying which pod have failed.

**Node monitoring (Kubernetes)**

If a node changes status to a know error state, this alert will send a message declaring the node, state and
problem. The follow state are monitored:

- **Ready state:** If state is unknown or not ready it will trigger alert
- **MemoryPressure:** Node is low on memory
- **PIDPressure:** To many processors are active on the node at the same time
- **DiskPressure:** Node is low on disk space.
- **NetworkUnavailable:** The nodes network is not configured correctly

