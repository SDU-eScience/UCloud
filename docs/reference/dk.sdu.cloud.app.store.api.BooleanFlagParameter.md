[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `BooleanFlagParameter`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Produces a toggleable command-line flag_

```kotlin
data class BooleanFlagParameter(
    val variableName: String,
    val flag: String,
    val type: String /* "bool_flag" */,
)
```
The parameter referenced by `variableName` must be of type [`ApplicationParameter.Bool`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.Bool.md), and the value
must be [`AppParamValue.Bool`](/docs/reference/dk.sdu.cloud.app.store.api.AppParamValue.Bool.md). This invocation parameter will produce the `flag` if the variable's value is
`true`. Otherwise, it will produce no values.

__Example:__ Example (with true value)

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--example"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": true }
}
```

_Expands to:_

```bash
"--example"
```

__Example:__ Example (with false value)

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--example"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": false }
}
```

_Expands to (nothing):_

```bash

```

__Example:__ With spaces

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--hello world"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": true }
}
```

_Expands to:_

```bash
"--hello world"
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variableName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>flag</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "bool_flag" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


