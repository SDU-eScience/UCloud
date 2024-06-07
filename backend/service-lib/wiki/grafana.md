To display information from our systems we use [Grafana](https://grafana.com/) to construct dashboards with user
defined queries.

More information about our use of Grafana can be found in our infrastructure documentation.

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
- __UCloud metrics (via Prometheus):__ UCloud exports various metrics via a Prometheus format. These are scraped and
  made available in Grafana.

## Dashboards

We have various dashboards, some examples include:

- __Requests:__ Here we have 2 different dashboards. One for a general overview of the requests being sent on the cloud
  system (request time/averages, status codes, number of requests per user) and another to have a more detailed view on
  a specific request type.
- __Node statistics:__ A single dashboard that shows the RAM, CPU, Swap and disk usage of all nodes.
- __Postgres Stats:__ A single dashboard showing stats from the PostgreSQL database (Queries Per Sec., Row stats, number
  of connections etc.)
- __UCloud dashboard:__ A single dashboard that shows key numbers of the cloud platform: Number of request, user
  activity, data stored, jobs information, etc.
- __KPI dashboard:__ A single dashboard showing Key Performance indicators such as new subscribed users and total amount
  of users and their respective organization distribution. Jobs activity and which applications are most used.

## Logging with Loki

As part of the Grafana stack we use 
[Loki](https://grafana.com/oss/loki/?pg=logs&plcmt=options&src=ggl-s&mdm=cpc&cnt=124221004773&camp=nb-loki-exact) 
to search and read our system and container logs.
We collect logs from containers used to run UCloud software as well as all 
system logs on each of our managed nodes. 

Loki makes it easy for us search efficiently for potential errors reported
by users. Being able to search for logs from specific nodes or containers combined with timestamps 
makes it easy to find the logs that are relevant.