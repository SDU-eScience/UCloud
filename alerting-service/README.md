# alerting-service

Provides slack alerts due to failures in critical services. This service
monitors data from [auditing](../service-common/wiki/auditing.md), if the error
rate goes above a threshold an error will be emitted. If the alerting service
at any points fail to contact Elasticsearch an error will be emitted.

At the moment this service is sending alerts to the #devsupport/#support
channels of slack. The messages are sent to slack via their webhooks feature.
This service is not specified only to work with slack, but can be hooked up to 
any chat/mail service that supports webhooks.

## Supported Alerts

- **Elasticsearch cluster health**   
  It is important that the Elasticsearch cluster always is at good health. Elasticsearch can be in three different states:
  
  - **Green**   
    All good
  - **Yellow**   
    Missing replicas of one or more indices
  - **Red**   
    One or more primary shards have not been allocated

  If the state changes to yellow and have not changed back to green within 
  5 minutes the service will send an alert. If the state however changes to 
  red, it will be alert after 1 minute. We can live without a replica but if 
  we are missing a primary shard, then we miss data.

- **Elasticsearch storage**   
  Elasticsearch consists of multiple data nodes. Theses nodes are given an 
  specific amount of storage to their disposal. If this storage is reaching
  pre-configured limits it will trigger and send a message.
  SDUCloud uses the following configuration: 

  - Send a `information` message clarifying which node has used 50% of its 
  storage.
  - Send a `warning` message clarifying which node has used 80% of its storage.
  - Send a `alert` message clarifying which node has used 90% of its storage.

  This gives time to either scale up or clean out in the elastic indices 
  before the [watermark limits](../elastic-management) are reach and further actions are required
  and limitation are enforced by elastic it self.

- **5XX status codes**   
  If request returns a 5XX code, then it could be a sign of something is 
  wrong in either our code or our infrastructure. 

  This alert checks the audit
  trail for request responses that has a 5XX status code. 
  If the number of request that returns a 5XX status code exceeds a pre-set 
  percentage of all requests within the last 15 minutes, the alert triggers. 

  The check runs each 5 minutes and will also notify when the number of 5XX 
  enters the acceptable range again.

- **Crash loop and pod failure detection (Kubernetes)**   
  If any pods in the kubernetes cluster fails due to some error or if a pod
  keeps restarting and ends in a CrashLoppBackOff (multiple failing restarts 
  within short time) this alert will trigger sending a message specifying which
  pod have failed.