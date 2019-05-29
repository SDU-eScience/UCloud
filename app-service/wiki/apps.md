# Application Format

__Applications__ are described by YAML documents. The document describes the
parameters of an application and how these should be used to invoke it. Each
application has an associated tool.

TODO Application environment variables, working directory, ...

## Reference

### `application`

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

### `tags`

A list of tags for this application. Used only for discoverability.

### `applicationType`

Denotes the type of application. Different application types behave in
different ways. Supported values:

- `BATCH`: A batch style application. It is run to completion without any user
  input.
- `WEB`: An interactive application with a web interface. Requires the `web`
  block to be defined.
- `VNC`: An interactive application accessed via VNC. Requires the `vnc` block
   to be defined.

### `invocation`

A list of invocation 'arguments' which denote how the application should be
started. Each item in the list translates to zero or more arguments on the
command line.

#### `string`

If the item in the list is a string (or can be converted to one) then the
invocation parameter will be passed as is.

##### Example

```yaml
invocation:
- foo
- bar
- 42
```

Would invoke the following command: `foo bar 42`.

#### `type: var`

Puts one or more variables into the invocation. This invocation argument has
several additional fields:

- `vars`: A list of variable names (or a single string)
- `prefixGlobal`: A global prefix. This will be added before any of the
  variables. This will be passed as its own argument.
- `suffixGlobal`: A global suffix. This will be added after all of the other
  variables. This will be passed as its own argument.
- `prefixVariable`: An argument to be passed with each variable.
- `suffixVariable`: An argument to be passed with each variable.
- `isPrefixVariablePartOfArg`: A boolean to determine if `prefixVariable`
  should be part of the same argument as the variable. Default: `false`.
- `isSuffixVariablePartOfArg`: A boolean to determine if `prefixVariable` should
  be part of the same argument as the variable. Default: `false`.

##### Examples

__Simple example:__

```yaml
invocation:
- foo
- type: var
  vars: my_variable
```

With `var = "hello"` would cause the following invocation: `foo hello`.

__Multiple variables:__

```yaml
invocation:
- foo
- type: var
  vars:
  - var1
  - var2
```

With `var1 = "v1", var2 = "v2"` would cause the following invocation `foo v1
v2`.

__Simple global prefixes:__

```yaml
invocation:
- foo
- type: var
  prefixGlobal: --flag
  vars:
  - var1
  - var2
```

With `var1 = "v1", var2 = "v2"` would cause the following invocation 
`foo --flag v1 v2`.

__Simple variable prefixes:__

```yaml
invocation:
- foo
- type: var
  prefixVariable: --flag
  vars:
  - var1
  - var2
```

With `var1 = "v1", var2 = "v2"` would cause the following invocation 
`foo --flag v1 --flag v2`.

__isPrefixVariablePartOfArg example:__

```yaml
invocation:
- foo
- type: var
  prefixVariable: --flag
  isPrefixVariablePartOfArg: true
  vars:
  - var1
  - var2
```

With `var1 = "v1", var2 = "v2"` would cause the following invocation
`foo --flagv1 --flagv2`.

__Prefixes and Suffixes:__

```yaml
invocation:
- foo
- type: var
  prefixGlobal: --gloPre
  suffixGlobal: --gloSuf
  prefixVariable: --pre
  suffixVariable: --suf
  vars:
  - var1
  - var2
```

With `var1 = "v1", var2 = "v2"` would cause the following invocation
`foo --gloPre --pre v1 --suf --pre v2 --suf --gloSuf`.

#### `type: flag`

Used to represent boolean flags. This is used in combination with a parameter
of type boolean. This invocation argument has the following additional fields:

- `var`: The variable. This must point to a variable of type boolean.
- `flag`: The value to include if the value of `var` is true

##### Examples

__Simple Example:__

```yaml
invocation:
- foo
- type: flag
  var: var1
  flag: --do-this-thing
- bar
```

With `var1 = true` would cause: `foo --do-this-thing bar`.

With `var1 = false` would cause: `foo bar`.

### `parameters`

Applications parameters are an opportunity for users to pass in their own input.
Each parameter define the format of a single piece of information. The following
fields are shared for all parameters:

- `type`: The type of application parameter (see below).
- `optional`: (Optional) Specifies if this value is optional.
- `defaultValue`: (Optional) The default value for this parameter. Only makes
  sense to use with `optional: true`.
- `title`: (Optional) A user friendly title for the parameter. Defaults to be
  the same as `name`.
- `description`: (Optional) A user friendly description of the parameter.
  Markdown is supported.

Note: A parameter can be `optional` without a `defaultValue`. In this case no
arguments will be passed to the invocation.

#### `type: input_file`

Represents an input file from SDUCloud. When passed to an invocation it will
be converted into the absolute path to the file. The input file will be mounted
at the working directory with the same name as in SDUCloud.

`defaultValue` for this type is not supported.

##### Examples

```yaml
invocation:
- foo
- type: var
  vars: my_parameter

parameters:
  my_parameter:
    type: input_file
```

#### `type: input_directory`

Represents an input directory from SDUCloud. When passed to an invocation it
will be converted into the absolute path to the file. The input file will be
mounted at the working directory with the same name as in SDUCloud.

`defaultValue` for this type is not supported.

##### Examples

```yaml
invocation:
- foo
- type: var
  vars: my_parameter

parameters:
  my_parameter:
    type: input_directory
```

#### `type: text`

A simple text value. Will be passed as a single invocation parameter
(multiple words are supported).

##### Examples

__Simple:__

```yaml
parameters:
  my_parameter:
    type: text
```

__With default value:__

```yaml
parameters:
  my_parameter:
    type: text
    optional: true
    defaultValue: "myDefaultValue"
```

#### `type: integer`

An integer value. This will be passed as a single invocation parameter.
Internally big integers are used for storing. The following additional fields
can be used:

- `min`: (Optional) The minimum value this parameter can have
- `max`: (Optional) The maximum value this parameter can have
- `step`: (Optional) Integer value determining how big the 'steps' should be
  in the slider UI component. Only useful when `min` and `max` is specified.
- `unitName`: (Optional) An optional unit name to be displayed in the UI.

##### Examples

__Simple:__

```yaml
parameters:
  my_parameter:
    type: integer
```

__With default value:__

```yaml
parameters:
  my_parameter:
    type: integer
    optional: true
    defaultValue: 42
```

__Percentages:__

```yaml
parameters:
  my_parameter:
    type: integer
    min: 0
    max: 100
    unitName: "%"
```

__Distances:__

```yaml
parameters:
  my_parameter:
    type: integer
    min: 0
    max: 1000
    step: 5 # Any value can still be used between 0-1000
    unitName: "meters
```

#### `type: floating_point`

A floating point value. Internally a big decimal type will be used. Uses the
exact same interface as `type: integer`.

#### `type: boolean`

A boolean value with configurable values for true and false. Will be passed
as a single argument. Additional fields are possible:

- `trueValue`: (Optional) A different value to be passed is the input is
  true. Default: `"true"`.
- `falseValue`: (Optional) A different value to be passed is the input is
  false. Default: `"false"`.

##### Examples

__Simple:__

```yaml
parameters:
  my_parameter:
    type: boolean
```

__With default value:__

```yaml
parameters:
  my_parameter:
    type: boolean
    optional: true
    defaultValue: true
```

__Different values:__

```yaml
parameters:
  my_parameter:
    type: boolean
    trueValue: --yes-do-this-thing
    falseValue: --no-do-this-other-thing
```

__Different values (2):__

```yaml
invocation:
- foo
- --my-flag
- type: var
   vars: my_parameter

parameters:
  my_parameter:
    type: boolean
    trueValue: "1"
    falseValue: "0"
```

With `my_parameter = true` will cause invocation `foo --my-flag 1`.

### `outputFileGlobs`

A list of [globs](https://en.wikipedia.org/wiki/Glob_%28programming%29) to
capture relevant output files. All globs will be executed in the working
directory and evaluated against the file name of each file in the working
directory. If a folder is captured it will be copied as is.

#### Examples

```yaml
outputFileGlobs:

- *.txt
- file.png
- folder
```

In directory:

```
.
├── a.png
├── b.png
├── c.txt
├── d.txt
├── file.png
└── folder
```

Would transfer the following files:

```
├── c.txt
├── d.txt
├── file.png
└── folder
```

### `container`

Optional.

Specifies some additional configuration for how the container should work.
This can be passed for any application type. The following fields are defined:

- `changeWorkingDirectory`: Should the working directory be changed to
  `/work/`. Default value is `true`.
- `runAsRoot`: Should this container be forcefully be changed to run as root?
  By default we will respect the containers configuration. Default value is
  `false`.
- `runAsRealUser`: Should this container be forcefully be changed to run as the
  UID matches the SDUCloud UID? By default we will respect the containers 
  configuration. Default value is `false`.

#### Examples

__Always run this container as root:__

```yaml
container:
  runAsRoot: true
```

### `environment`

Optional.

An object containing environment variable keys and invocation arguments as
their values. These environment variables will be available inside of the
container.

In addition to the arguments listed in the `invocation` section we can use
the following invocation argument:

#### `type: env`

Used for aliasing of environment variables.

- `var`: The variable to alias

#### Examples

__Simple example:__

```yaml
environment:
  var1: my_value
```

__Variable aliasing:__

```yaml
environment:
  var1: my_value
  var2:
    type: env
    var: var1
```

__Environment variables from user input:__

```yaml
parameters:
  my_parameter:
    type: text

environment:
  envVar1:
    type: var
    vars: my_parameter
```

### `vnc`

Used with `applicationType: VNC`.

This block configures how to access the VNC server running inside of the
container. The VNC server running inside of the container is required to
support VNC via websockets. Additionally the server should run with no
password or a pre-configured password which should be passed in this block.

Note: All authentication is done by SDUCloud, hence we do not need to rely on
the security of VNC.

The following fields are supported in this block:

- `password`: (Optional) The password for this VNC server. Default value is
  none.
- `port`: (Optional) The port inside of the container that the server will
  respond to VNC websocket traffic. Default is 5900.

#### Examples

__Simple:__

```yaml
vnc:
  password: hardcodedPassword
  port: 5902
```

### `web`

Used with `applicationType: WEB`.

This block configures how to access the web server running inside of the
container. Just like with `applicationType: VNC` authentication should
generally be disabled for the web application. Authentication will
automatically be done by SDUCloud.

SDUCloud will proxy traffic as HTTP to the webserver running inside of the
container.

The following fields are supported in this block:

- `port`: (Optional) The port to access the web server inside of the
  container. Default value is 80.
