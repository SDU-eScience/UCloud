# CLI

The Kubernetes integration includes a small operations CLI for inspecting and controlling active jobs and inference model catalog entries.

Commands are run on the host where the Integration Module is installed:

```terminal
$ ucloud <command> <subcommand> [options]
```

The job commands are available under `ucloud jobs ...`. Inference model catalog commands are available under `ucloud inference models ...`.

## Notes

- Commands must be run as `root`.
- Query flags use regular expressions unless otherwise noted.
- The CLI shows active jobs tracked by the Integration Module (non-final jobs).
- Most commands support `--json` for machine-readable output.

## `jobs` command

### `ucloud jobs ls`

Lists active jobs.

Optional filters:

- `--state <regex>`: Match UCloud job state.
- `--job-id <regex>`: Match UCloud job ID.
- `--user <regex>`: Match submitting UCloud username.
- `--project <regex>`: Match UCloud project ID.
- `--application <regex>`: Match application name.
- `--category <regex>`: Match machine category.
- `--queue <regex>`: Match scheduler queue name.
- `--json`: Print JSON output.

Aliases: `list`.

### `ucloud jobs get <jobId>`

Shows detailed information for one job.

Options:

- `--json`: Print JSON output.

Aliases: `retrieve`, `stat`, `view`.

### `ucloud jobs queue [queue]`

Shows queue insight for all queues (or a selected queue), including:

- queued job count
- running job count
- queue/product availability statuses
- queued jobs per queue (when enabled)

Options:

- `--queue <regex>`: Filter queue name.
- `--jobs=<true|false>`: Include/exclude queued job listings (default: `true`).
- `--json`: Print JSON output.

If a positional `queue` argument is given, it is treated as an exact queue-name match.

Alias: `queues`.

### `ucloud jobs queue debug [queue]`

Shows internal scheduler state intended for debugging and operations.

The output includes, per scheduler queue:

- queue tick/time
- all known node entries (including remaining/capacity/limits and unschedulable flag)
- individual queue entries (priority and factor values)
- individual replica entries (job, rank, node, dimensions)

Options:

- `--queue <regex>`: Filter queue name.
- `--json`: Print JSON output.

Alias: `ucloud jobs queue state [queue]`.

### `ucloud jobs kill <jobId...>`

Stops one or more jobs.

Behavior by backend:

- Virtual machine jobs are **suspended**.
- Container jobs are **terminated**.

You can pass multiple IDs in one command.

Aliases:

- `stop`
- `rm`, `remove`, `delete`, `del`

### `ucloud jobs suspend <jobId...>`

Suspends one or more virtual machine jobs.

If a listed job is not a virtual machine job, the command reports an error for that job.

### `ucloud jobs unsuspend <jobId...>`

Unsuspends one or more virtual machine jobs.

If a listed job is not a virtual machine job, the command reports an error for that job.

Alias: `resume`.

## `inference models` command

The inference model commands manage the model catalog stored by the Integration Module. They do not query the backing inference platform directly.

### `ucloud inference models ls`

Lists inference model catalog entries, including name, title, capabilities, pricing multipliers, endpoint information, and availability.

Options:

- `--json`: Print JSON output.

Alias: `list`.

### `ucloud inference models update <name>`

Updates an existing inference model catalog entry.

Options:

- `--name <name>`: Rename the model. Renaming is only allowed while the model is not public.
- `--title <title>`: Set the display title.
- `--capabilities <list>`: Set a comma-separated capability list. Allowed values are `TextGeneration`, `TextToImage`, and `SpeechToText`.
- `--price-cached <n>`: Set cached input token price multiplier in fixed-point thousandths.
- `--price-input <n>`: Set input token price multiplier in fixed-point thousandths.
- `--price-output <n>`: Set output token price multiplier in fixed-point thousandths.
- `--public <bool>`: Set whether the model is publicly available.
- `--available-to <list>`: Set a comma-separated list of project IDs allowed to use the model when it is not public.
- `--base-path <url>`: Set the OpenAI-compatible endpoint base path for the backing model.
- `--backend-model-name <name>`: Set the model name sent to the backing inference provider.

Price multipliers must be non-negative integers. A value of `1000` means `1x`; `500` means `0.5x`.

### `ucloud inference models rm <name>`

Removes an inference model catalog entry. Removal only affects the Integration Module catalog and does not depend on whether the backing inference platform still lists the model.

Aliases: `remove`, `delete`, `del`.

## Examples

```terminal
$ ucloud jobs ls
$ ucloud jobs ls --state IN_QUEUE --category gpu-a10
$ ucloud jobs get 1234
$ ucloud jobs queue
$ ucloud jobs queue cpu-standard/general
$ ucloud jobs queue --queue 'gpu.*/.*' --jobs=false
$ ucloud jobs queue debug
$ ucloud jobs queue state gpu-a10/a10
$ ucloud jobs stop 1234 5678
$ ucloud jobs suspend 1234
$ ucloud jobs resume 1234
$ ucloud inference models ls
$ ucloud inference models update qwen --title "Qwen" --capabilities TextGeneration --price-input 1000 --price-output 1000
$ ucloud inference models update qwen --name qwen-public --public true
$ ucloud inference models rm qwen-public
```
