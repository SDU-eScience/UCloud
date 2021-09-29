# Visualization of Usage

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



## Remote Procedure Calls

### `retrieveUsage`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#visualizationretrieveusagerequest'>VisualizationRetrieveUsageRequest</a></code>|<code><a href='#visualizationretrieveusageresponse'>VisualizationRetrieveUsageResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveBreakdown`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#visualizationretrievebreakdownrequest'>VisualizationRetrieveBreakdownRequest</a></code>|<code><a href='#visualizationretrievebreakdownresponse'>VisualizationRetrieveBreakdownResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `BreakdownChart`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class BreakdownChart(
    val type: ProductType,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: PieChart,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md'>ChargeType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unit</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md'>ProductPriceUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chart</code>: <code><code><a href='#piechart'>PieChart</a></code></code>
</summary>





</details>



</details>



---

### `LineChart`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class LineChart(
    val lines: List<LineChart.Line>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>lines</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#linechart.line'>LineChart.Line</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `LineChart.Line`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Line(
    val name: String,
    val points: List<LineChart.Point>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>points</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#linechart.point'>LineChart.Point</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `LineChart.Point`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Point(
    val timestamp: Long,
    val value: Double,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>



</details>



---

### `PieChart`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class PieChart(
    val points: List<PieChart.Point>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>points</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#piechart.point'>PieChart.Point</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `PieChart.Point`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Point(
    val name: String,
    val value: Double,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>



</details>



---

### `UsageChart`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class UsageChart(
    val type: ProductType,
    val periodUsage: Long,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: LineChart,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>periodUsage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md'>ChargeType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unit</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md'>ProductPriceUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chart</code>: <code><code><a href='#linechart'>LineChart</a></code></code>
</summary>





</details>



</details>



---

### `VisualizationRetrieveBreakdownRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VisualizationRetrieveBreakdownRequest(
    val filterStartDate: Long?,
    val filterEndDate: Long?,
    val filterType: ProductType?,
    val filterProvider: String?,
    val filterProductCategory: String?,
    val filterAllocation: String?,
    val filterWorkspace: String?,
    val filterWorkspaceProject: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterStartDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterEndDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterWorkspace</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterWorkspaceProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `VisualizationRetrieveUsageRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VisualizationRetrieveUsageRequest(
    val filterStartDate: Long?,
    val filterEndDate: Long?,
    val filterType: ProductType?,
    val filterProvider: String?,
    val filterProductCategory: String?,
    val filterAllocation: String?,
    val filterWorkspace: String?,
    val filterWorkspaceProject: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterStartDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterEndDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterWorkspace</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterWorkspaceProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `VisualizationRetrieveBreakdownResponse`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VisualizationRetrieveBreakdownResponse(
    val charts: List<BreakdownChart>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>charts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#breakdownchart'>BreakdownChart</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `VisualizationRetrieveUsageResponse`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VisualizationRetrieveUsageResponse(
    val charts: List<UsageChart>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>charts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#usagechart'>UsageChart</a>&gt;</code></code>
</summary>





</details>



</details>



---

