# CLI

The UCloud Integration Module ships with a Command-Line Interface (CLI) which makes some management 
tasks more trivial. This page will serve as the manual.

The CLI is invoked from a terminal on the node where the UCloud Integration Module is installed, and 
where the `ucloud` binary is in your `PATH`. Commands are invoked in the following format:

```terminal
$ ucloud <command> <subcommand> [options]
```

where `<command>` and `<subcommand>` can be any of the options described in the following sections. 
For example you can list the UCloud projects on your system and their corresponding local ID with

```terminal
$ ucloud projects ls
```

Additional parameters or options can be given to some subcommands. For example, if you want the list 
only to contain projects with the name *test* in the name, you can use the `--ucloud-name` option:

```terminal
$ ucloud projects ls --ucloud-name test
```

Since `--ucloud-name` is a query parameter that supports regex, it acts as a fuzzy search that 
returns all projects that contains the word `test` in its name.


## Commands

### `allocations`

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

`ls`, `list`

</td>
<td>
List allocations on the system.

#### Optional parameters

`--ucloud-project-id <string>`

Query by UCloud project ID. Supports regex.


`--local-user <string>`

Query by local user name. Supports regex.


`--local-group <string>`

Query by the local group name. Supports regex.


`--local-uid <string>`

Query by local user ID. Supports regex.


`--local-gid <string>`

Query by local ID (GID). Supports regex.


`--category <string>`

Query by product category (Product). Supports regex.
	
</td>
</tr>
<tr>
<td>

`get`, `retrieve`, `stat`, `view`

</td>
<td>
View details about allocations on the system.

#### Optional parameters

`--ucloud-project-id <string>`

Query by UCloud project ID. Supports regex.


`--local-user <string>`

Query by local user name. Supports regex.


`--local-group <string>`

Query by the local group name. Supports regex.


`--local-uid <string>`

Query by local user ID. Supports regex.


`--local-gid <string>`

Query by local ID (GID). Supports regex.


`--category <string>`

Query by product category (Product). Supports regex.
	
</td>
</tr>

</tbody>
</table>
</div>


### `connect`

TODO


### `drives`

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

`ls`, `list`

</td>
<td>
List all drives.

`--ucloud-id <string>`

Query by the UCloud ID of the drive. Supports regex.

`--ucloud-project-id <string>`

Query by the UCloud project ID of the owner of the drive. Supports regex.

`--local-user <string>`

Query by the local user name that owns the drive. Supports regex.

`--local-group <string>`

Query by the local group (GID) that owns the drive. Supports regex.

`--local-uid <string>`

Query by the local UID of the user that owns the drive. Supports regex.

`--local-gid <string>`

Query by the local GID of the user that own the drive. Supports regex.

`--ucloud-path <path>`

Query by the path to the drive on UCloud. Does not support regex.

`--local-path <path>`

Query by the local path on your system. Does not support regex.
</td>
</tr>
<tr>
<td>

`get`, `retrieve`, `stat`, `view`

</td>
<td>
Get detailed information about drives.

`--ucloud-id <string>`

Query by the UCloud ID of the drive. Supports regex.

`--ucloud-project-id <string>`

Query by the UCloud project ID of the owner of the drive. Supports regex.

`--local-user <string>`

Query by the local user name that owns the drive. Supports regex.

`--local-group <string>`

Query by the local group (GID) that owns the drive. Supports regex.

`--local-uid <string>`

Query by the local UID of the user that owns the drive. Supports regex.

`--local-gid <string>`

Query by the local GID of the user that own the drive. Supports regex.

`--ucloud-path <path>`

Query by the path to the drive on UCloud. Does not support regex.

`--local-path <path>`

Query by the local path on your system. Does not support regex.

</td>
</tr>

</tbody>
</table>
</div>



### `jobs`

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

`get`, `retrieve`, `stat`, `view`

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

`ls`, `list`

</td>
<td>
List UCloud projects with a mapping to your system.

#### Optional parameters

`--ucloud-name <string>`

Query by UCloud name. Supports regex.


`--ucloud-id <string>`


Query by UCloud ID. Supports regex.


`--local-name <string>`

Query by local name. Supports regex.

`--local-id <string>`

Query by local ID. Supports regex.

</td>
</tr>
<tr>
<td>

`replace`

</td>
<td>

Replaces the local ID (GID) of the project with the current local ID `old-id`, with the new local ID 
`new-id`. This changes the mapping of the UCloud project to a new local project. It is also possible 
to replace the mapping of one project mapping with another through this command.

#### Parameters

`--old-id <number>`

The old GID of the project to replace.

`--new-id <number>`

The new GID of the project to replace.

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

**TODO:** The interface for scripts differ slightly from the other subcommands. We should probably 
change something.

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

`ls`, `list`

</td>
<td>
List log of previously run tasks

#### Optional parameters

`--query=<string>`

Search log entries where `<string>` exists in stdout, stderr, request or script path.

`--failures`

Show only failed scripts.

`--script=<path>`

Show only entries from the script located at the path `<path>`.
	
`--before-relative=<string>`

Show entries older than `<string>`, e.g. `--before-relative="1 hour"` shows entries older than 
1 hour.

`--after-relative=<time>`

Show entries newer than `<time>`, e.g. `--after-relative="1 hour"` shows entries from the past hour.
</td>
</tr>
<tr>
<td>

`get`, `retrieve`, `stat`, `view`

</td>
<td>
Get detailed information about script with a specific ID.

#### Example

To get detailed information about the script log with ID 1234.

```terminal
$ ucloud scripts get 1234
```

</td>
</tr>
<tr>
<td>

`delete`, `del`, `rm`, `remove`

</td>
<td>
Delete script log entry with a specific ID.

#### Example

To delete the script log with ID 1234.

```terminal
$ ucloud scripts rm 1234
```

</td>
</tr>
<tr>
<td>

`clear`

</td>
<td>
Deletes all script log entries.
</td>
</tr>
<tr>
<td>

`help`

</td>
<td>
Prints information about parameters for the `script` command.
</td>
</tr>
</tbody>
</table>
</div>



### `slurm-accounts`

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

</tbody>
</table>
</div>



### `tasks`

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

`kill-all`

</td>
<td>
Kills all long running tasks on the system, such as a file copy, empty trash or file transfer.

**TODO:** Needs documentation.
</td>
</tr>
</tbody>
</table>
</div>



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

#### Parameters

`--ucloud-name <string>`

`--local-name <string>`

#### Example

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

