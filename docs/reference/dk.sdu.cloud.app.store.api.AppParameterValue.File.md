[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppParameterValue.File`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a UCloud file_

```kotlin
data class File(
    val path: String,
    val readOnly: Boolean?,
    val type: String /* "file" */,
)
```
- __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The absolute path to the file or directory in the software's environment
- __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory
    
The path of the file must be absolute and refers to either a UCloud directory or file.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The absolute path to the file or directory in UCloud
</summary>





</details>

<details>
<summary>
<code>readOnly</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates if this file or directory should be mounted as read-only
</summary>



A provider must reject the request if it does not support read-only mounts when `readOnly = true`.


</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "file" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


