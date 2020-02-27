# Release Notes

This page tracks a high-level overview of changes to the version of UCloud
hosted at ucloud.dk (cloud.sdu.dk). This page is intended for internal
use.

Note that each service may have more than one deployment associated with it.
For details look in the `k8/` folders of each microservice.

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

