# Audit and Metrics

The Integration Module writes one audit record for each logical call, with the middleware names `inference.chat`,
`inference.responses`, and `inference.conversations`. The responses adapter suppresses the inner chat record, so one
call produces one record.

A record holds the caller, the model, the outcome, and the token usage. The record never contains the message
content. A rejected call records the rejection reason, and a read-only call can complete without a record.

## Metrics

Prometheus metrics are exposed under the `ucloud_im_inference_` prefix.

| Metric (`ucloud_im_inference_`) | Type | Labels | Description |
|---------------------------------|------|--------|-------------|
| `requests_total` | Counter | `model` | Requests with reported or estimated usage. |
| `request_results_total` | Counter | `model`, `outcome` | Requests by result. |
| `request_duration_seconds` | Histogram | `model` | Duration of a request. |
| `requests_in_flight` | Gauge | none | Requests that are admitted and processed now. |
| `requests_rejected_total` | Counter | `reason` | Requests that admission control rejected. |
| `input_tokens_total` | Counter | `model` | Non-cached input tokens. |
| `cached_input_tokens_total` | Counter | `model` | Cached input tokens. |
| `output_tokens_total` | Counter | `model` | Output tokens. |
| `input_tokens_per_request` | Histogram | `model` | Input tokens of one request. |
| `output_tokens_per_request` | Histogram | `model` | Output tokens of one request. |
| `cached_input_ratio` | Histogram | `model` | Cached input as a fraction of all input, per request. |
| `time_to_first_token_seconds` | Histogram | `model` | Stream start to the first output. |
| `time_to_last_token_seconds` | Histogram | `model` | Stream start to the last output delta. |
| `output_tokens_per_second` | Histogram | `model` | Output rate, from the first delta to the end of the stream. |
| `output_delta_interval_seconds` | Histogram | `model` | Time between two output deltas. |
| `users_total` | Counter | none | Users that have used the feature successfully. |
| `users_today_total` | Counter | none | Users that used the feature successfully today. |

The `outcome` label is `success`, `success_missing_usage`, `background_submitted`, `client_cancelled`, `client_error`,
`admission_rejected`, `payment_required`, `upstream_error`, `stream_timeout`, `incomplete`, or `error`. The `reason`
label is `global`, `owner`, or `cancelled`.
