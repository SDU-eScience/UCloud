# alerting-service

Provides slack alerts due to failures in critical services. This service
monitors data from [auditing](../service-common/wiki/auditing.md), if the error
rate goes above a threshold an error will be emitted. If the alerting service
at any points fail to contact elasticsearch an error will be emitted.
