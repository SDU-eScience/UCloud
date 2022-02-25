[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `BulkRequest`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A base type for requesting a bulk operation._

```kotlin
data class BulkRequest<T>(
    val items: List<T>,
)
```
---

__⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
that either the entire request succeeds, or the entire request fails.

There are two exceptions to this rule:

1. Certain calls may choose to only guarantee this at the provider level. That is if a single call contain request
for multiple providers, then in rare occasions (i.e. crash) changes might not be rolled back immediately on all
providers. A service _MUST_ attempt to rollback already committed changes at other providers.

2. The underlying system does not provide such guarantees. In this case the service/provider _MUST_ support the
verification API to cleanup these resources later.

---

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code>
</summary>





</details>



</details>


