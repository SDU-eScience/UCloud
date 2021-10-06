# `ComputeSupport`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ComputeSupport(
    val product: ProductReference,
    val docker: ComputeSupport.Docker?,
    val virtualMachine: ComputeSupport.VirtualMachine?,
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



</details>

