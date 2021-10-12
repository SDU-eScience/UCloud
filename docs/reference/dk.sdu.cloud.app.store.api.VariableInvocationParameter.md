[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `VariableInvocationParameter`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An InvocationParameter which produces value(s) from parameters._

```kotlin
data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String?,
    val suffixGlobal: String?,
    val prefixVariable: String?,
    val suffixVariable: String?,
    val isPrefixVariablePartOfArg: Boolean?,
    val isSuffixVariablePartOfArg: Boolean?,
    val type: String /* "var" */,
)
```
The parameter receives a list of `variableNames`. Each must reference an [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md). It is 
valid to reference both optional and mandatory parameters. This invocation will produce zero values if all the 
parameters have no value. This is regardless of the prefixes and suffixes.

The invocation accepts prefixes and suffixes. These will alter the values produced. The global affixes always 
produce one value each, if supplied. The variable specific affixes produce their own value if 
`isXVariablePartOfArg`.

__Example:__ Simple variable

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"]
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "text", "value": "Hello, World!" }
}
```

_Expands to:_

```bash
"Hello, World!"
```

__Example:__ Global prefix (command line flags)

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"],
    "prefixGlobal": "--count"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 }
}
```

_Expands to:_

```bash
"--count" "42"
```

__Example:__ Multiple variables

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable", "mySecondVariable"],
    "prefixGlobal": "--count"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
    "mySecondVariable": { "type": "integer", "value": 120 },
}
```

_Expands to:_

```bash
"--count" "42" "120"
```

__Example:__ Variable prefixes and suffixes

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"],
    "prefixGlobal": "--entries",
    "prefixVariable": "--entry",
    "suffixVariable": "--next",
    "isPrefixVariablePartOfArg": true,
    "isSuffixVariablePartOfArg": false
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
}
```

_Expands to:_

```bash
"--entries" "--entry42" "--next"
```

__Example:__ Complete example

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable", "mySecondVariable"],
    "prefixGlobal": "--entries",
    "prefixVariable": "--entry",
    "suffixVariable": "--next",
    "suffixGlobal": "--endOfEntries",
    "isPrefixVariablePartOfArg": false,
    "isSuffixVariablePartOfArg": true
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
    "mySecondVariable": { "type": "text", "value": "hello" },
}
```

_Expands to:_

```bash
"--entries" "--entry" "42--next" "--entry" "hello--next" "--endOfEntries"
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variableNames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isPrefixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isSuffixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "var" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


