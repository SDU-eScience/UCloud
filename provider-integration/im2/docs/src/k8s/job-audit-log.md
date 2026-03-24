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
audit-log service; terminal and ssh access are disabled.

The application will then be able to make http POST 
request to the audit-log service
eg. using `curl`
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
meta can hold any arbitrary data.

A log line will be appended as a json object
```json
{"ts":"2026-01-28T09:12:44.381Z","jobId":"5003592","workspaceId":"651b3f72-b49d-4ad5-8bbe-4ff2d4889002","event":"DATA_EXPORT","message":"Exported results to CSV","meta":{"path":"/work/output/results.csv","rows":12500}}
```
The log file is stored in the workspace directory `/audit`
```
/audit/audit-${UCLOUD_RANK}_timestamp.jsonl
```

## Job audit log retention period

To configure how often the audit log should be cleaned up, 
set the `retentionPeriodInDays` property in `/etc/cloud/config.yaml`
```yaml
provider:
  id: k8s

  auditLog:
    retentionPeriodInDays: 180
...

```
