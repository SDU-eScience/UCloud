#Kubernetes Monitor

A service that allows to contact kubernetes and automate actions

##Rancher Fluentd Log Monitoring
Goes through all pods associated with the Fluentd logging forwarding to Elasticsearch of Rancher. 
By checking the logs of each pod, the service will restart the pod if it seems to have
problems sending logs to Elasticsearch. 

Once per day it sends a report to the alert slack channel of the development team with 
the number of restarts it has performed. If none has been restarted it will not notify.
