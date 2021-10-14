[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `PaginationRequestV2Consistency`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class PaginationRequestV2Consistency {
    PREFER,
    REQUIRE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PREFER</code> Consistency is preferred but not required. An inconsistent snapshot might be returned.
</summary>





</details>

<details>
<summary>
<code>REQUIRE</code> Consistency is required. A request will fail if consistency is no longer guaranteed.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>


