To display information from our systems we use [Grafana](https://grafana.com/) to construct dashboards with user
defined queries.

## Data sources

To be able to create dashboards, Grafana needs to have access to different datasources.

- __Elasticsearch:__ Keeps logs from the system and also the audit trail of the users allowing us to generate metrics on
  request time, request types, number of 4XX requests, user activity etc.
- __Prometheus:__ [Prometheus](https://prometheus.io/) has a node exporter installed on all the nodes in the Kubernetes
  cluster. These exporters ships metrics from these nodes. These are used for showing RAM, Disk and CPU usage of
  individual pods alongside the Kubernetes plugin.
- __Kubernetes Plugin:__ Gathers information of the Kubernetes cluster available
  through [kubectl](https://kubernetes.io/docs/reference/kubectl/overview/) alongside Prometheus.
- __Postgres:__ Grants access to our database and gives us the possibility to query it for things like: number of
  applications, number of users etc.

## Dashboards

We have 9 dashboards that can be divided into the following categories:

- __Requests:__ Here we have 2 different dashboards. One for a general overview of the requests being sent on the cloud
  system (request time/averages, status codes, number of requests per user) and another to have a more detailed view on
  a specific request type.
- __Kubernetes dashboards:__ Here we have 2 different dashboards. These dashboards are automatically created by the
  [Kubernetes Plugin](https://grafana.com/grafana/plugins/grafana-kubernetes-app). They show cluster, node, deployment
  and container specific metrics.
- __Memory, disk and CPU usage:__ A single dashboard that shows the RAM, CPU, Swap and disk usage of all pods.
- __Postgres Stats:__ A single dashboard showing stats from the PostgreSQL database (Queries Per Sec., Row stats, number
  of connections etc.)
- __UCloud dashboard:__ A single dashboard that shows key numbers of the cloud platform: Number of request, user
  activity, data stored, jobs information, etc.
- __KPI dashboard:__ A single dashboard showing Key Performance indicators such as new subscribed users and total amount
  of users and their respective organization distribution. Jobs activity and which applications are most used.

