[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Software Licenses](/docs/developer-guide/orchestration/compute/license.md)

# `LicenseState`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class LicenseState {
    PREPARING,
    READY,
    UNAVAILABLE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PREPARING</code> A state indicating that the `License` is currently being prepared and is expected to reach `READY` soon.
</summary>





</details>

<details>
<summary>
<code>READY</code> A state indicating that the `License` is ready for use or already in use.
</summary>





</details>

<details>
<summary>
<code>UNAVAILABLE</code> A state indicating that the `License` is currently unavailable.
</summary>



This state can be used to indicate downtime or service interruptions by the provider.


</details>



</details>


