[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `Application`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Applications specify the input parameters and invocation of a software package._

```kotlin
data class Application(
    val metadata: ApplicationMetadata,
    val invocation: ApplicationInvocationDescription,
)
```
For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#applicationmetadata'>ApplicationMetadata</a></code></code>
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='#applicationinvocationdescription'>ApplicationInvocationDescription</a></code></code>
</summary>





</details>



</details>


