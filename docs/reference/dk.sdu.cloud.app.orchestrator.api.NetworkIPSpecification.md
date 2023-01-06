[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public IPs (NetworkIP)](/docs/developer-guide/orchestration/compute/ips.md)

# `NetworkIPSpecification`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPSpecification(
    val product: ProductReference,
    val firewall: NetworkIPSpecification.Firewall?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> The product used for the `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>firewall</code>: <code><code><a href='#networkipspecification.firewall'>NetworkIPSpecification.Firewall</a>?</code></code>
</summary>





</details>



</details>


