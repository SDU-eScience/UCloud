# Learn Jinja2 for UCloud

The `invocation` field is a Jinja2 template. UCloud renders it when a job starts and uses the result as a shell script. This guide is a quick reference for writing application invocations.

For the complete language reference, see the [Jinja2 template documentation](https://jinja.palletsprojects.com/en/stable/templates/). The syntax and names in this guide represent the subset supported by UCloud.

## The short version

```jinja2
# Text outside Jinja tags is copied to the generated script.
# String parameter values are automatically shell-quoted by UCloud.
my-program {{ inputFile }}

{% if verbose %}
echo "Verbose mode enabled"
{% endif %}

# Add an option and shell-quoted value.
my-program {{ outputDirectory | option("--output") }}

# Use a fallback when a value is not defined.
my-program --model {{ model | default("default-model") }}

# Use UCloud runtime information.
echo "Running on {{ ucloud.machine.name }}"
```

If `inputFile` is `/data/my file.txt`, the generated command contains `'/data/my file.txt'`. Direct string values are automatically quoted, so a simple input parameter usually needs only `{{ inputFile }}`. Numbers and booleans are rendered in their normal form. The `if` block emits a separate command when `verbose` is true. `option` adds an option and its quoted value, `default` supplies a fallback, and `ucloud.machine.name` reads the allocated machine name.

- `{{ expression }}` evaluates an expression and writes its value.
- `{% statement %}` runs control logic such as `if`, `for`, or `set`.
- `{# comment #}` is removed from the generated script.
- `{{-` and `-}}` trim whitespace next to an expression. The same form works with statement tags.
- Use `~` to concatenate values: `{{ "--name=" ~ name }}`.
- Invocation templates can contain multiple lines and commands.

## Values and parameters

Every submitted application parameter is available by its parameter name. A parameter with a default value uses that default when the user does not provide a value. An optional parameter without a value or default is undefined.

```jinja2
{{ textParameter }}
{{ numberParameter + 1 }}

{% if optionalParameter is defined %}
  echo {{ optionalParameter }}
{% endif %}
```

Use `is defined` before reading an optional value. Use `is none` when a value is explicitly `None`.

```jinja2
{% if optionalParameter is not defined or optionalParameter is none %}
  echo "No value was supplied"
{% endif %}
```

The available parameter values depend on the parameter type. Files and directories are rendered as paths, peers as host names, and license, IP, ingress, and similar resources as their identifiers. String values written with `{{ ... }}` are automatically shell-quoted. The `option` filter is useful when the option name and value should be emitted together.

```jinja2
my-program {{ value | option("--value") }}
```

## Conditions

```jinja2
{% if model == "large" %}
  echo "Using the large model"
{% elif model == "small" %}
  echo "Using the small model"
{% else %}
  echo "Using the default model"
{% endif %}
```

Supported operators include `==`, `!=`, `<`, `<=`, `>`, `>=`, `and`, `or`, `not`, `in`, and `is`. Parentheses can be used to group expressions.

## Loops

```jinja2
{% for file in inputFiles %}
  process {{ file }}
{% endfor %}
```

The `loop` object provides `index`, `index0`, `revindex`, `revindex0`, `first`, `last`, and `length`. It also provides `loop.cycle(...)` and `loop.changed(value)`.

```jinja2
{% for item in items %}
  echo "{{ loop.index }}: {{ item }}"
{% endfor %}
```

Dictionaries can be iterated as key/value pairs:

```jinja2
{% for key, value in settings %}
  echo "{{ key }}={{ value }}"
{% endfor %}
```

## Variables and reusable blocks

Use `set` to assign a value. `namespace` is useful when a value must be updated from inside a loop.

```jinja2
{% set command = "program" %}
{{ command }} {{ inputFile }}

{% set ns = namespace(found=false) %}
{% for item in items %}
  {% if item == wanted %}{% set ns.found = true %}{% endif %}
{% endfor %}
```

Macros provide local reusable blocks:

```jinja2
{% macro input(value) %}{{ value | option("--input") }}{% endmacro %}
program {{ input(inputFile) }}
```

UCloud does not load templates from files. `include`, `import`, `from`, `extends`, and `block` are not available in application invocations.

## UCloud variables

Application parameters are top-level variables. Their names are the keys from the `parameters` section of the application YAML.

The following values are available in the `ucloud` object:

| Variable | Value |
| --- | --- |
| `ucloud.jobId` | The stable UCloud job ID as a string. |
| `ucloud.machine.name` | The allocated machine slice name. |
| `ucloud.machine.category` | The machine category name. |
| `ucloud.machine.cpu` | Number of allocated CPUs. |
| `ucloud.machine.cpuModel` | CPU model, when configured. |
| `ucloud.machine.memoryInGigs` | Allocated memory in gigabytes. |
| `ucloud.machine.memoryModel` | Memory model, when configured. |
| `ucloud.machine.gpu` | Number of allocated GPUs. |
| `ucloud.machine.gpuModel` | GPU model, when configured. |
| `ucloud.nodes` | Number of allocated nodes. |
| `ucloud.rank` | Zero-based rank of the current process. |
| `ucloud.application.name` | Name of the application being submitted. |
| `ucloud.application.version` | Version of the application being submitted. |

For example:

```jinja2
echo "Running {{ ucloud.application.name }} {{ ucloud.application.version }}"
echo "Using {{ ucloud.machine.cpu }} CPUs on {{ ucloud.machine.name }}"
```

## Functions

| Function | Description |
| --- | --- |
| `ternary(condition, ifTrue, ifFalse)` | Returns `ifTrue` or `ifFalse` based on `condition`. |
| `dynamicInterface(rank, type, target, port)` | Registers a dynamic `WEB` or `VNC` interface for a process rank. |
| `setInterfaceName(name)` | Sets the name of a dynamic interface. |
| `dict(**values)` | Creates a dictionary from keyword arguments. |
| `namespace(**values)` | Creates a mutable namespace for values shared across scopes. |
| `range([start,] stop[, step])` | Produces integer values from `start` up to, but not including, `stop`. |
| `cycler(values...)` | Cycles through values. Use `.next()` and `.reset()`. |
| `joiner(sep=",")` | Returns an empty string on the first call and `sep` on later calls. |
| `lipsum(n=5, html=true, min=20, max=100)` | Generates lorem ipsum text. Primarily useful for testing. |

Functions that register interface metadata return an empty string and are normally used as standalone statements:

```jinja2
{{ ternary(useGpu, "--device gpu", "--device cpu") }}
{% for rank in range(ucloud.nodes) %}
  {{ dynamicInterface(rank, "WEB", "worker-" ~ rank, 8080) }}
{% endfor %}
```

## Filters

Filters are applied with `|` and can be chained:

```jinja2
{{ name | trim | lower }}
{{ values | select("defined") | join(", ") }}
```

| Filter | Description |
| --- | --- |
| `flag(onFlag[, offFlag])` | Returns `onFlag` for a true value and `offFlag` otherwise. The default `offFlag` is empty. |
| `option(optionFlag[, addSpace])` | Adds a shell-escaped option and value when the input is not `None`. Spacing is inferred from whether `optionFlag` ends in `=` unless `addSpace` is supplied. |
| `abs` | Absolute value. |
| `attr` | Read an attribute without item lookup. |
| `batch` | Group values into fixed-size lists. |
| `capitalize` | Uppercase the first character and lowercase the rest. |
| `center` | Center text to a given width. |
| `default`, `d` | Use a fallback for an undefined value. |
| `dictsort` | Sort dictionary items. |
| `escape`, `e` | Escape HTML characters. |
| `filesizeformat` | Format a number as a human-readable file size. |
| `first` | Return the first item. |
| `float` | Convert to a floating-point number. |
| `forceescape` | Force HTML escaping. |
| `format` | Apply printf-style formatting. |
| `groupby` | Group values by an attribute. |
| `indent` | Indent each line of text. |
| `int` | Convert to an integer. |
| `join` | Join a sequence into a string. |
| `last` | Return the last item. |
| `length` | Return the number of items. |
| `list` | Convert a value to a list. |
| `lower` | Convert to lowercase. |
| `map` | Map an attribute or filter over a sequence. |
| `max` | Return the largest item. |
| `min` | Return the smallest item. |
| `pprint` | Pretty-print a value. |
| `random` | Return a random item. |
| `rejectattr` | Reject values whose attribute passes a test. |
| `reject` | Reject values that pass a test. |
| `replace` | Replace occurrences of a substring. |
| `reverse` | Reverse a value or iterator. |
| `round` | Round a number. |
| `safe` | Mark a value as safe from automatic escaping. |
| `selectattr` | Select values whose attribute passes a test. |
| `select` | Select values that pass a test. |
| `slice` | Split an iterator into lists. |
| `sort` | Sort an iterable. |
| `string` | Convert a value to a string. |
| `striptags` | Remove HTML/XML tags. |
| `sum` | Sum values, optionally by an attribute. |
| `title` | Convert text to title case. |
| `tojson` | Serialize a value as JSON. |
| `trim` | Remove leading and trailing whitespace. |
| `truncate` | Limit text to a given length. |
| `unique` | Remove duplicate values. |
| `upper` | Convert to uppercase. |
| `urlencode` | Encode a value for a URL. |
| `urlize` | Convert URLs in text to links. |
| `wordcount` | Count words in text. |
| `wordwrap` | Wrap text to a given width. |
| `xmlattr` | Create XML/HTML attributes from a dictionary. |

For example, `flag` produces a switch and `option` produces a switch plus its quoted value:

```jinja2
my-program {{ enabled | flag("--enabled", "--disabled") }}
my-program {{ value | option("--value") }}
my-program {{ value | option("--value=") }}
```

## Tests

Tests are used with `is`, especially in conditions and `select`/`reject` filters.

```jinja2
{% if value is defined and value is not none %}
  echo {{ value }}
{% endif %}

{{ values | select("even") | list }}
```

| Tests | Meaning |
| --- | --- |
| `defined`, `undefined` | Whether a value exists. |
| `none` | Whether a value is `None`. |
| `callable`, `iterable`, `mapping`, `sequence`, `string`, `number` | Checks the value type or capability. |
| `even`, `odd`, `divisibleby` | Integer checks. |
| `eq`, `equalto`, `==`, `ne`, `!=` | Equality and inequality. |
| `gt`, `greaterthan`, `>`, `ge`, `>=` | Greater-than comparisons. |
| `lt`, `lessthan`, `<`, `le`, `<=` | Less-than comparisons. |
| `in` | Checks whether a value occurs in a sequence or mapping. |
| `lower`, `upper` | Checks the case of a string. |
| `sameas` | Checks object identity. |
| `match(pattern[, ignorecase=false][, multiline=false])` | Matches a regular expression against the complete string. |
| `search(pattern[, ignorecase=false][, multiline=false])` | Searches for a regular expression in a string. |

## Methods

The following methods are registered for Python-like values:

| Value | Methods |
| --- | --- |
| Strings | `capitalize`, `capwords`, `casefold`, `center`, `count`, `encode`, `endswith`, `expandtabs`, `find`, `format`, `format_map`, `isalnum`, `isalpha`, `isascii`, `isdecimal`, `isdigit`, `islower`, `isnumeric`, `isprintable`, `isspace`, `istitle`, `isupper`, `join`, `ljust`, `lower`, `lstrip`, `partition`, `removeprefix`, `removesuffix`, `replace`, `rfind`, `rjust`, `rpartition`, `rsplit`, `rstrip`, `split`, `splitlines`, `startswith`, `strip`, `swapcase`, `title`, `upper`, `zfill`. |
| Lists | `append`, `copy`, `reverse`. |
| Dictionaries | `keys`. |
| Integers | `is_integer`, `bit_length`, `bit_count`, `as_integer_ratio`. |
| Floats | `is_integer`, `as_integer_ratio`, `hex`. |
| Booleans | `string`, `int`, `bit_length`, `bit_count`, `as_integer_ratio`. |

```jinja2
{{ name.strip().lower() }}
{{ ",".join(values) }}
{{ settings.keys() | join(", ") }}
```
