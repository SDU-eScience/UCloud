:orphan:

# Alerting Service

Provides slack alerts due to failures in critical services. This service
monitors data from [auditing](backend/service-common/wiki/auditing.html), if the error
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
  before the [watermark limits](backend/elastic-management-service/README.html) are reach and further actions are required
  and limitation are enforced by elastic it self.

- **Number of Shards**  
  Since Elasticsearch 7.0, a hard limit on number of shards per nodes have been introduced. 
  If this limit is reached then no more shards, and therefore indices, cannot be allocated to 
  the node. 
  
  This alert gives a heads up when running close to the cluster limit. This makes it possible to 
  take actions before allocations are put on hold. If no configuration is given to the alert, then
  a default of 500 available shards are set as the threshold before an alert triggers.
  
  To handle this situation either increase the number of shards allowed per node or reduce the number 
  of shards/indices in the cluster.
  To help with the latter the [elastic-management service](backend/elastic-management-service/README.html) can be used.
  
  Since the number of shards do not change much during a day, this alert is only 
  checking every 12 hours. Should an alert have fired, it checks every 30 min to be able to notify 
  once the number of available shards are above the limit again.

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
  
- **4XX detection in Ambassador**    
  If an IP has many 4XX requests going through Ambassador there is a risk
  that there might be someone malicious person trying to gain access to SDUCloud by 
  sweeping for endpoints.
  
  This alert checks the log trail from ambassador for any logs containing 4xx 
  in the last half hour. These logs also contains the IP of origin for the request.
  Depending on the number of 4XX responses from a IP a alert will trigger, sending
  a alert to slack where the suspect IP(s) are listed. If a limit is not given in the
  configuration, then a default limit of 500 4xx responses per IP is used.
  
  The alert can also whitelist certain IPs by adding them to the configuration. 
  By doing this the alert will skip a log if the origin IP is contain in the list of 
  whitelisted IPs.
  
  This alert is run every 15 minutes.
