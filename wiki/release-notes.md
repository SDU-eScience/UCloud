# Release Notes

This page tracks a high-level overview of changes to the version of UCloud
hosted at ucloud.dk (cloud.sdu.dk). This page is intended for internal
use.

Note that each service may have more than one deployment associated with it.
For details look in the `k8.kts` folders of each microservice.

## 01-04-20

Another bug fix for app-kubernetes.

| Deployment | Image |
| ---------- | ----- |
| app-kubernetes         |  registry.cloud.sdu.dk/sdu-cloud/app-kubernetes-service:0.17.2 |

## 31-03-20

Minor bug fix for indexing

| Deployment | Image |
| ---------- | ----- |
| indexing         |  registry.cloud.sdu.dk/sdu-cloud/indexing-service:1.16.1 |

## 23-03-20

Kata container code disabled for app-kubernetes.

| Deployment | Image |
| ---------- | ----- |
| app-kubernetes         |  registry.cloud.sdu.dk/sdu-cloud/app-kubernetes-service:0.17.1-kata-disabled |

## 23-03-20

New deployment for most services.


| Deployment | Image |
| ---------- | ----- |
| accounting-compute     |  registry.cloud.sdu.dk/sdu-cloud/accounting-compute-service:1.2.12 |
| accounting-storage     |  registry.cloud.sdu.dk/sdu-cloud/accounting-storage-service:1.3.0 |
| activity               |  registry.cloud.sdu.dk/sdu-cloud/activity-service:1.4.14 |
| alerting               |  registry.cloud.sdu.dk/sdu-cloud/alerting-service:1.1.22 |
| app-kubernetes         |  registry.cloud.sdu.dk/sdu-cloud/app-kubernetes-service:0.17.0 |
| app-kubernetes-watcher |  registry.cloud.sdu.dk/sdu-cloud/app-kubernetes-watcher-service:0.1.6 |
| app-license            |  registry.cloud.sdu.dk/sdu-cloud/app-license-service:0.1.8 |
| app-orchestrator       |  registry.cloud.sdu.dk/sdu-cloud/app-orchestrator-service:2.0.1 |
| app-store              |  registry.cloud.sdu.dk/sdu-cloud/app-store-service:0.13.0 |
| audit-ingestion        |  registry.cloud.sdu.dk/sdu-cloud/audit-ingestion-service:0.1.14 |
| auth                   |  registry.cloud.sdu.dk/sdu-cloud/auth-service:1.27.4 |
| avatar                 |  registry.cloud.sdu.dk/sdu-cloud/avatar-service:1.3.6 |
| contact-book           |  registry.cloud.sdu.dk/sdu-cloud/contact-book-service:0.1.16 |
| downtime-management    |  registry.cloud.sdu.dk/sdu-cloud/downtime-management-service:0.1.6 |
| file-favorite          |  registry.cloud.sdu.dk/sdu-cloud/file-favorite-service:1.5.0 |
| file-gateway           |  registry.cloud.sdu.dk/sdu-cloud/file-gateway-service:1.4.0 |
| file-stats             |  registry.cloud.sdu.dk/sdu-cloud/file-stats-service:2.0.0 |
| file-trash             |  registry.cloud.sdu.dk/sdu-cloud/file-trash-service:1.3.10 |
| filesearch             |  registry.cloud.sdu.dk/sdu-cloud/filesearch-service:1.1.11 |
| indexing               |  registry.cloud.sdu.dk/sdu-cloud/indexing-service:1.16.0 |
| kubernetes-monitor     |  registry.cloud.sdu.dk/sdu-cloud/kubernetes-monitor-service:0.1.3 |
| mail                   |  registry.cloud.sdu.dk/sdu-cloud/mail-service:0.1.0-mail-test.10 |
| notification           |  registry.cloud.sdu.dk/sdu-cloud/notification-service:1.2.12 |
| share                  |  registry.cloud.sdu.dk/sdu-cloud/share-service:1.7.0 |
| storage                |  registry.cloud.sdu.dk/sdu-cloud/storage-service:4.0.0 |
| support                |  registry.cloud.sdu.dk/sdu-cloud/support-service:1.3.4 |
| task                   |  registry.cloud.sdu.dk/sdu-cloud/task-service:0.2.5 |
| webclient              |  registry.cloud.sdu.dk/sdu-cloud/webclient:0.36.1 |
| webdav                 |  registry.cloud.sdu.dk/sdu-cloud/webdav-service:0.1.12 |

## 02-03-20

Bug fix in elastic-management. During shrinking process source and target index 
should now have same amount of doc before attempt to delete source index is made.

| Deployment | Image |
| ---------- | ----- |
| elastic-management | elastic-management-service:1.0.24 |

## 21-02-20

Bug fix via new storage version. Added more names to GPU whitelist.

| Deployment | Image |
| ---------- | ----- |
| storage | storage-service:3.2.10 |
| app-orchestrator | app-orchestrator-service:0.8.24-gpu.3 |

## 13-02-20

We back-ported GPU support for the old versions.

| Deployment | Image |
| ---------- | ----- |
| app-kubernetes | app-kubernetes-service:0.12.7-gpu.0 |
| app-orchestrator | app-orchestrator-service:0.8.24-gpu.0 |
| app-store | app-store-service:0.10.4-gpu.0 |
| webclient-deployment | webclient:0.31.2-gpu.0 |
| downtime-management | downtime-management-service:0.1.5 |

## 22-01-20

Initial entry. The table below represents the active deployments.

| Deployment | Image |
| ---------- | ----- |
| accounting-compute | accounting-compute-service:1.2.8 |
| accounting-storage-service-deployment | accounting-storage-service:1.2.8 |
| activity-service-deployment | activity-service:1.4.9 |
| alerting | alerting-service:1.1.17 |
| app-fs | app-fs-service:0.2.6 |
| app-fs-kubernetes | app-fs-kubernetes-service:0.1.5 |
| app-kubernetes | app-kubernetes-service:0.12.7 |
| app-kubernetes-watcher | app-kubernetes-watcher-service:0.1.3 |
| app-license | app-license-service:0.1.2 |
| app-orchestrator | app-orchestrator-service:0.8.24 |
| app-store | app-store-service:0.10.4 |
| audit-ingestion | audit-ingestion-service:0.1.10 |
| auth-service-deployment | auth-service:1.26.5 |
| avatar | avatar-service:1.3.3 |
| downtime-management | downtime-management-service:0.1.0 |
| file-favorite | file-favorite-service:1.4.3 |
| file-gateway | file-gateway-service:1.3.6 |
| file-stats-service-deployment | file-stats-service:1.2.7 |
| file-trash-service-deployment | file-trash-service:1.3.6 |
| filesearch-service-deployment | filesearch-service:1.1.8 |
| indexing-service-deployment | indexing-service:1.15.6-DEVONLY |
| notification-service-deployment | notification-service:1.2.9 |
| share-service-deployment | share-service:1.6.3 |
| storage-service-deployment | storage-service:3.0.9 |
| support-service-deployment | support-service:1.3.1 |
| task | task-service:0.2.2 |
| webclient-deployment | webclient:0.31.2 |
| webdav | webdav-service:0.1.9 |

