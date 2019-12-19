#Grafana

To display information from our systems we use [Grafana](https://grafana.com/) to construct dashboards with user 
defined queries. 

##Datasources
To be able to create dashboards, Grafana needs to have access to different datasources.
- Elasticsearch  
  Keeps logs from the system and also the audit trail of the users allowing us to generate metrics on request time, 
  request types, number of 4XX requests, user activity etc.
- Prometheus  
  [Prometheus](https://prometheus.io/) has a node exporter installed on all the nodes in the Kubernetes cluster. 
  These exporters ships metrics from these nodes. These are used for showing RAM, Disk and CPU usage of individual 
  pods along side the Kubernetes plugin.
- Kubernetes Plugin  
  Gathers information of the Kubernetes cluster available through [kubectl](https://kubernetes.io/docs/reference/kubectl/overview/) 
  along side Prometheus.
- Postgres  
  Grants access to our database and gives us the possibility to query it for things like: number of applications,
  number of users etc..

##Dashboards
We have 9 dashboards that can be divided into the following categories: 
- Requests:  
  Here we have 2 different dashboards. One for a general overview of the requests being sent on the cloud 
system (request time/averages, status codes, number of requests per user) and another to have a more detailed view on 
a specific request type.
- Kubernetes dashboards:  
  Here we have 4 different dashboards. These dashboards are automatically created by the 
  [Kubernetes Plugin](https://grafana.com/grafana/plugins/grafana-kubernetes-app). They show cluster, node, deployment 
  and container specific metrics.
- Memory, disk and CPU usage:  
  A single dashboard that shows the RAM, CPU, Swap and disk usage of all pods.
- Postgres Stats:
  A single dashboard showing stats from the PostgreSQL database (Querys Per Sec., Row stats, number of connections etc.)
- UCloud dashboard:  
  A single dashboard that shows key numbers of the cloud platform: Number of request, user activity, data stored, jobs
  information, etc..
