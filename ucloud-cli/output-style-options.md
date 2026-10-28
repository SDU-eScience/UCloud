# UCloud CLI output style guide

This guide defines how the UCloud CLI presents human-readable and machine-readable output. It applies to `ucloud-cli` and the shared terminal-rendering utilities in `provider-integration/shared/pkg/termio`.

Use the same information in terminal and redirected output, but adapt its presentation to the stream. Detect whether each stream is attached to a terminal separately. In particular, stdout can be redirected while stderr is still a terminal.

## Human-readable output

### Long-running operations

Use a compact heading, short milestone lines, a progress section when needed, and one final outcome. Don't repeat the same URL or result in a final frame.

For Kubernetes cluster creation, show the dashboard link early and keep it visible while nodes are starting. On a terminal, keep `Dashboard connected` directly below the link, with a blank line before node progress:

```text
Creating cluster 'dev'
  ✓ Resources created
  ↗ Dashboard: https://example.com/app/stacks/dev
  ✓ Dashboard connected

  ✓ ━━━━━━━━━━━━━━━━━━━━━━━━  control-plane-1:  Cluster bootstrapped

✓ Cluster 'dev' is ready
```

Don't put a blank line after the heading. Keep related status and detail lines together. Use one blank line between the dashboard group and node progress, and before the final outcome. Append-only output follows the ordering rules below when the connection status arrives later.

### Short operations

Use one concise status sentence for a simple action. For a create or update that returns useful resource details, follow the sentence with a field-value frame. Add separate lines for useful warnings:

```text
✓ Job 'analysis' created
┌────────┬──────────┐
│ Id     │ 123      │
│ Status │ Queued   │
└────────┴──────────┘
```

For a single deletion, use a status sentence that accurately describes the outcome:

```text
✓ Cluster 'dev' deletion requested
  State drive: deletion requested
```

For an action on multiple resources, use a table that identifies each resource and its outcome. Distinguish failures from successes without relying on color.

### Lists and details

Use a single boxed table for a list of comparable records and a field-value frame for details of one record. Use separate tables for related collections. Use these forms in both terminal and redirected human-readable output.

A list can look like this. The pagination flags in this example describe the intended interface and might not yet be available in every command.

```text
┌──────┬──────────┐
│ Name │ Status   │
├──────┼──────────┤
│ dev  │ Ready    │
│ prod │ Creating │
└──────┴──────────┘

Showing 2 results.
Next: ucloud job list --limit 2 --next 'opaque-cursor'
```

A single-resource detail view can look like this:

```text
┌────────┬───────┐
│ Name   │ dev   │
│ Status │ Ready │
└────────┴───────┘
```

For an empty list, keep the boxed table and column headers, and put a resource-specific message inside it:

```text
┌──────┬──────────┐
│ Name │ Status   │
├──────┴──────────┤
│ No jobs found   │
└─────────────────┘

Showing 0 results.
```

Don't silently make important values inaccessible on a narrow terminal. Wrap values, indicate truncation and provide a way to inspect the full value, or use an appropriate details view. Preserve complete values in JSON when it is available. Human-readable tables are not a substitute for JSON in scripts.

### Pagination

List commands return a bounded batch by default. Use `defaults.itemsPerPage` from the CLI configuration when available; otherwise, use 100. These conventions describe the intended interface; existing commands might not yet support every option.

- `--limit` overrides the default number of results.
- `--next` accepts the opaque continuation cursor for the next batch.
- `--all` fetches all available results rather than applying the default bound.

After every human-readable list table, show the number of results displayed. When more results are available, also show a command for the next batch. Retain the relevant workspace, filters, ordering, and limit in that command, and quote its arguments safely. Don't invent a total count if the backend doesn't provide one.

Continuation must not skip available matches at a page boundary, including when filtering happens on the client. If fetching additional pages fails, report the failure rather than presenting an incomplete result as complete.

Print tables directly to stdout. Don't launch a pager or prompt for navigation automatically; users can scroll or pipe the output to their own pager. Apply the same result limit and continuation behavior when output is redirected.

### Names, symbols, and color

- Put names in single quotes in sentences, such as `Cluster 'dev' is ready`. Escape embedded quotes and backslashes consistently. Render control characters visibly rather than interpreting them in the terminal. This is display text, not shell quoting.
- Don't quote URLs merely because they are URLs.
- Use `✓` for success, `!` for a warning, `✗` for a failure, and `↗` for a link. Status must remain understandable without color.
- By default, enable color according to the stream receiving the output. Honor the CLI's existing color controls and `NO_COLOR`. A forced color setting can enable color in redirected human output, but it must not enable cursor movement or animation there.
- Explain failures with useful context, without exposing credentials. Keep warnings and failures visible and preserve nonzero exit codes.

## Progress

### Terminal output

Render active progress in place, using animation or redraws as appropriate. Keep meaningful final node rows visible and show the final outcome. Never mark an incomplete stage successful merely because another readiness signal is true.

Show available node progress while the dashboard connection is pending. When the connection is reported, place its status directly below the dashboard link and keep the separator before the node rows. Warnings and failures must remain visible.

### Redirected output

Print append-only updates when a stage or outcome changes, and suppress duplicate updates. Always report warnings and failures. Don't use spinners, cursor movement, or animated redraws on a redirected stream.

Print available node updates immediately. If the dashboard connects after node updates have been printed, append `Dashboard connected` at that point. Don't buffer node progress or repeat the dashboard link to rearrange the log:

```text
Creating cluster 'dev'
  ✓ Resources created
  ↗ Dashboard: https://example.com/app/stacks/dev

  … control-plane-1: Allocating resources
  ✓ Dashboard connected
  ✓ control-plane-1: Cluster bootstrapped

✓ Cluster 'dev' is ready
```

This chronological ordering is intentional: append-only output can't insert the connection status above already printed node updates.

## Streams and JSON

For human-readable output, write progress, early links, and results to stdout. Write errors to stderr. Raw data requested by the user, such as kubeconfig content, goes to stdout without status prefixes.

In JSON mode:

- Write the successful result as one valid JSON value on stdout, with two-space indentation and a final newline, whether or not stdout is a terminal.
- For paginated lists, use the page object described below. Preserve the field names, omission rules, and value types of the items. For other results, preserve each command's existing JSON shape and value types.
- Keep stdout free of progress text and ANSI styling. An error may leave stdout empty; preserve existing failure contracts.
- Write human progress, warnings, and errors to stderr. Return a nonzero exit code on failure.
- Apply terminal or redirected progress behavior based on stderr itself, independently of stdout.

For example, a JSON result can be:

```json
{
  "name": "dev",
  "dashboardUrl": "https://example.com/app/stacks/dev",
  "ready": true
}
```

Paginated list results use an object with an `items` array and a `next` cursor. Always include both fields. Use an empty array when there are no items and `null` when there are no more results. An `--all` result has `next: null`.

```json
{
  "items": [
    {
      "name": "dev",
      "status": "ready"
    }
  ],
  "next": "opaque-cursor"
}
```

An empty final page looks like this:

```json
{
  "items": [],
  "next": null
}
```

The page object replaces the array shape used by some existing list commands. Treat changing an existing JSON result, pagination behavior, or command flags as a contract change that requires review and approval. Don't turn an existing JSON result into JSON Lines without a separate, explicitly approved output contract. Keep confirmation prompts readable and JSON stdout clean; don't change confirmation behavior as a styling-only fix.
