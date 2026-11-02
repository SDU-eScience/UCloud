# Audit

The Integration Module writes an audit record for each logical inference call. The records show who used which model,
when, and with what result. The record does not contain the message content.

The records use the same audit pipeline as the RPC calls of the platform. See [Auditing](../../procedures/auditing.md)
for the general mechanism.

## Record names

| Request name | When the platform writes a record |
|--------------|-----------------------------------|
| `inference.chat` | A Chat Completions call, or a chat in the playground. |
| `inference.responses` | A create call to the Responses API, including a streaming or background call. |
| `inference.responses.delete` | A deleted response. |
| `inference.conversations` | A conversation call that the platform rejects. |
| `inference.conversations.delete` | A deleted conversation. |

The responses adapter suppresses the inner chat record, so one create call to the Responses API produces one
`inference.responses` record. A response delete produces one `inference.responses.delete` record. See
[Chat Middleware](./middleware.md).

Some read-only calls do not create a detailed audit record. The platform still covers them with infrastructure
monitoring and logging, such as access logs and metrics. These endpoints are often part of the public API surface.
`/v1/models` is an example, including the single-model route `/v1/models/{name}`. A `GET` request for one conversation
is also read-only and does not create a detailed audit record. A background response writes a record when the platform
accepts it.

## Record fields

The Integration Module writes the record to `audit_logs.logs` in its database. These records are in the Integration
Module database, not in the UCloud/Core database. The table has these fields:

| Field | Type | Meaning |
|-------|------|---------|
| `request_name` | `text` | The record name from the table above. |
| `received_at` | `timestamptz` | When the Integration Module received the call. |
| `request_size` | `bigint` | The size of the request body. |
| `response_code` | `smallint` | The HTTP status of the call. |
| `response_time_nanos` | `bigint` | The duration of the call in nanoseconds. |
| `request_body` | `jsonb` | The audited request. See [Request body](#request-body). |
| `username` | `text` | The user that created the token, or the owner of the playground session. |
| `user_agent` | `text` | The user agent of the client. |
| `token_reference` | `text` | The API token ID. Empty for a playground call. |
| `remote_origin` | `text` | The client IP address. Empty for a playground call. |
| `project_id` | `text` | The project that owns the call. Empty for a personal call. |

The platform does not update a record after it writes it.

## Request body

The `request_body` field holds the audited request. It does not hold the message text.

A chat record has these fields:

| Field | Meaning |
|-------|---------|
| `model` | The model name. |
| `stream` | `true` when the client requested a stream. |
| `source` | `playground` for a playground call. Absent for a public API call. |
| `reason` | The outcome, or the rejection reason. |
| `aborted` | `true` when the client cancelled the request. |
| `messageCount` | The number of messages in the request. |
| `usage` | The token usage. |
| `temperature` | The temperature, when the client set it. |
| `top_p` | The top-p value, when the client set it. |
| `max_completion_tokens` | The completion limit, when the client set it. |
| `reasoning_effort` | The reasoning effort, when the client set it. |
| `toolCount` | The number of tools in the request. |

A Responses record uses these fields when they apply: `model`, `background`, `stream`, `source`, `reason`, `itemCount`,
`conversation`, `previous_response_id`, and `usage`.

A conversation delete record has one field:

```json
{
    "conversationId": "conv_..."
}
```

A response delete record has one field:

```json
{
    "responseId": "resp_..."
}
```

The `reason` field is the outcome for a normal call. Examples are `success`, `success_missing_usage`,
`background_submitted`, `client_cancelled`, `client_error`, `admission_rejected`, `payment_required`,
`upstream_error`, `stream_timeout`, `incomplete`, and `error`. For a rejected call, `reason` is the rejection reason.

### Example

```json
{
    "model": "deepseek-v4.1-flash-1017",
    "stream": true,
    "source": "playground",
    "reason": "success",
    "messageCount": 4,
    "usage": {
        "prompt_tokens": 1024,
        "completion_tokens": 256,
        "total_tokens": 1280,
        "prompt_tokens_details": {
            "cached_tokens": 0
        }
    },
    "temperature": 0.8,
    "top_p": 0.1,
    "toolCount": 2
}
```

## Token correlation

The `token_reference` field holds the ID of the API token. Use it to connect an inference record to the token that the
client used. See [Provider API Tokens](../../overview/provider-api-tokens.md).

The `username` field is the user that created the token, or the owner of the playground session. For a project token, the
record names the creator of the token, not the person who used the secret. Treat the token as the accountable principal
and use `token_reference` to identify it.

A rejected authentication or authorization request has no token identity. Its `username` and `token_reference` are
empty, and `request_body` holds only the rejection reason.

A playground call has no API token. Its record has a `username` and no `token_reference`. A job token has its own token
ID, so a record from a job has a `token_reference`.

## Retention

The audit store partitions `audit_logs.logs` into seven-day partitions and keeps them for 25 weeks. The Integration
Module configuration does not expose a setting for this window.

See [Auditing scenario](../../procedures/auditing-scenario.md) for how to query the general audit records.

## Query examples

Run these queries on the Integration Module database. Add a `received_at` condition to use the partitions.

The following query returns the inference records of the last seven days:

```sql
select
    received_at,
    request_name,
    username,
    project_id,
    token_reference,
    response_code,
    request_body->>'model' as model,
    request_body->>'reason' as outcome,
    request_body->'usage' as usage
from
    audit_logs.logs
where
    request_name like 'inference.%'
    and received_at >= now() - interval '7 days'
order by received_at desc
limit 100;
```

The following query returns the records of one API token in the last seven days:

```sql
select
    received_at,
    request_name,
    username,
    project_id,
    response_code,
    request_body->>'model' as model,
    request_body->>'reason' as outcome
from
    audit_logs.logs
where
    token_reference = '<token-id>'
    and request_name like 'inference.%'
    and received_at >= now() - interval '7 days'
order by received_at desc
limit 100;
```

The following query counts the outcomes of the chat requests of one day:

```sql
select
    request_body->>'reason' as outcome,
    count(*) as requests
from
    audit_logs.logs
where
    request_name = 'inference.chat'
    and received_at >= now() - interval '1 day'
group by 1
order by 2 desc;
```

## Metrics

The Integration Module also exports Prometheus metrics for usage and latency. See [Metrics](./observability.md).
