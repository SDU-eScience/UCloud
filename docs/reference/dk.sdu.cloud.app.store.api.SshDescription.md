[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `SshDescription`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Information to the provider about the SSH capabilities of this application_

```kotlin
data class SshDescription(
    val mode: SshDescription.Mode?,
)
```
Providers must use this information, if SSH is supported, to correctly configure applications with the appropriate
keys. See /docs/reference/jobs.control.browseSshKeys.md  for more information.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>mode</code>: <code><code><a href='#sshdescription.mode'>SshDescription.Mode</a>?</code></code>
</summary>





</details>



</details>


