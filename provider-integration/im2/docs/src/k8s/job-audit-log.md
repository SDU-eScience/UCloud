# Job Audit Log

Job audit log is a feature that allows application developers to log specific job executions.


To enable it, upload an application with the following configuration:
```yaml
application: v2

name: "my-custom-app"
version: "7"

title: "My Custom Application"
documentation: "https://docs.cloud.sdu.dk/"
description: "Auditing jobs"

features:
  jobAuditLog: true
...
```

When this feature is enabled, the job will spawn a sidecar container called 
audit-log, the application will then be able to make a POST 
request to:

```bash
curl -X POST http://127.0.0.1:48291/append \
  -H "Content-Type: application/json" \
  -d '{
    "event": "DATA_EXPORT",
    "message": "Exported results to CSV",
    "meta": {
      "path": "/work/output/results.csv",
      "rows": 12500
    }
  }'
```

A log line will be appended as shown
```json
{"ts":"2026-01-28T09:12:44.381Z","jobId":"5003592","workspaceId":"651b3f72-b49d-4ad5-8bbe-4ff2d4889002","event":"DATA_EXPORT","message":"Exported results to CSV","meta":{"path":"/work/output/results.csv","rows":12500}}
```
The log data will be stored in a file called
```
/audit/audit-${UCLOUD_RANK}_timestamp.jsonl
```

## Cleanup interval

To configure how often the audit log should be cleaned up, 
set the `retentionPeriodInDays` property in `/etc/cloud/config.yaml`
```yaml
provider:
  id: k8s

  auditLog:
    retentionPeriodInDays: 180
...

```
