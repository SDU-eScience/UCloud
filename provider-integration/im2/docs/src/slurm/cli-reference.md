# CLI

The UCloud Integration Module ships with a Command-Line Interface (CLI) which makes some management 
tasks more trivial. This page will serve as the manual.

The CLI is invoked from a terminal on the node where the UCloud Integration Module is installed, and 
where the `ucloud` binary is in your `PATH`. Commands are invoked in the following format:

```terminal
$ ucloud <command> <subcommand> [options]
```

where `<command>` can be any of the options described in the following sections. For example can 
users who have connected to your provider be listed with:

```terminal
$ ucloud users ls
```

## Commands

### `allocations`

### `connect`

### `drives`

### `jobs`

### `projects`

<div class="table-wrapper">
<table>
<thead>
<tr>
<td>
Subcommand
</td>

<td>
Description
</td>
</tr>
</thead>
<tbody>
<tr>
<td>

`add`

</td>
<td>

</td>
</tr>
<tr>
<td>

`delete`, `del`, `rm`, `remove`

</td>
<td>

</td>
</tr>

<tr>
<td>

`ls`, `list`

</td>
<td>

</td>
</tr>
<tr>
<td>

`replace`

</td>
<td>

</td>
</tr>
</tbody>
</table>
</div>

#### Examples

```terminal
$ ucloud projects ls
```

### `scripts`

### `slurm-accounts`

### `tasks`

### `users`

<div class="table-wrapper">
<table>
<thead>
<tr>
<td>
Subcommand
</td>

<td>
Description
</td>
</tr>
</thead>
<tbody>
<tr>
<td>

```
add <ucloud-name> <local-name>
```

</td>
<td>

Creates a new mapping between UCloud user with user name `user-name` and the local user 
`local-name`.

**Example**

```terminal
$ ucloud users add AliceAndersen#1234 aandersen01
```



</td>

</tr>

<tr>
<td>

`delete`, `del`, `rm`, `remove`

</td>
<td>

</td>
</tr>

<tr>
<td>

`ls`, `list`

</td>
<td>

</td>
</tr>

</tbody>
</table>
</div>

#### Examples

```terminal
$ ucloud users ls
```

