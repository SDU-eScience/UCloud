# Tool Format

A __tool__ is a resource defined by a YAML document. This document describes
which container should be used by the _application_. The same tool can be
used by multiple different applications.

Examples can be found [here](../yaml/tools).

```yaml
---
tool: v1 # Denotes that this document is describing a tool. Tool spec is v1.

title: tool # User friendly title for the tool (currently not displayed anywhere)
name: tool-name # A unique name for the tool (used in references)
version: "1.0.0" # A version number for the tool, must be a string

container: myusername/foo # Name of the container to run. Any valid docker container string (including remote ones)
backend: UDOCKER # Can also be SINGULARITY

authors:
- A
- List
- Of
- Many
- Authors
- Dan Thrane

defaultMaxTime:
    hours: 1
    minutes: 0
    seconds: 0

description: It is a tool # (Optional) Description of tool
defaultNumberOfNodes: 1 # Optional
defaultTasksPerNode: 1 # Optional
```

## Reference

### `tool`

Denotes the version of this document. Supported values are:

- `"v1"`

### `title`

User friendly title for the tool.

### `name` and `version`

The `name` and `version` is a combination which uniquely identifies this
document.

### `authors`

A list of authors (strings). The list of authors can be anything and doesn't
have to conform to any standard. This is used solely for giving credit to the
authors.

### `description`

A markdown description of this tool.

### `container`

A reference to the container image. How this string is interpreted is
`backend` dependent.

- `UDOCKER`: Conforms to the standard defined by docker. By default this will
  attempt to retrieve the container from DockerHub.

### `backend`

The backend implementation for this tool. Below is a list of current
implementations:

- `UDOCKER` (since `v1`): Implemented via normal docker containers. Name is
likely to be changed to `DOCKER` in the future
(https://github.com/SDU-eScience/SDUCloud/issues/804).

### `defaultMaxTime`

Optional.

The default amount of time to use for the scheduler. This doesn't affect for
how long the tool/application can be scheduled.

The type is a simple duration described below:

```yaml
hours: int (> 0)
minutes: int (0-59)
seconds: int (0-59)
```

### `defaultNumberOfNodes` and `defaultTasksPerNode`

Optional.

Unused properties for MPI.