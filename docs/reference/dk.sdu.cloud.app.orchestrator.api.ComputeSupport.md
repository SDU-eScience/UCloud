[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `ComputeSupport`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ComputeSupport(
    val product: ProductReference,
    val docker: ComputeSupport.Docker?,
    val virtualMachine: ComputeSupport.VirtualMachine?,
    val native: ComputeSupport.Native?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>docker</code>: <code><code><a href='#computesupport.docker'>ComputeSupport.Docker</a>?</code></code> Support for `Tool`s using the `DOCKER` backend
</summary>





</details>

<details>
<summary>
<code>virtualMachine</code>: <code><code><a href='#computesupport.virtualmachine'>ComputeSupport.VirtualMachine</a>?</code></code> Support for `Tool`s using the `VIRTUAL_MACHINE` backend
</summary>





</details>

<details>
<summary>
<code>native</code>: <code><code><a href='#computesupport.native'>ComputeSupport.Native</a>?</code></code> Support for `Tool`s using the `NATIVE` backend
</summary>





</details>



</details>


