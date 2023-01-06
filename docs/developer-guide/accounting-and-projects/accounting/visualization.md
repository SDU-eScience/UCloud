<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/allocations.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/grants.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / Visualization of Usage
# Visualization of Usage

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Visualization gives the user the possibility to get an easy overview of their usage_

## Usage chart

The usage chart shows the usage over time for each product category.

![](/backend/accounting-service/wiki/UsageChartFull.png)

__Figure 1:__ Usage shown fully for product type `COMPUTE` for the period of week. 

![](/backend/accounting-service/wiki/UsageChartInfo.png)

__Figure 2:__ Usage specifics for each category in the product type.

## Breakdown chart

The breakdown chart shows the usage for the entire period divided into the different products used.

![](/backend/accounting-service/wiki/BreakdownChart.png)

__Figure 3:__ Breakdown of different products usage in product type `COMPUTE`

## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#retrievebreakdown'><code>retrieveBreakdown</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveusage'><code>retrieveUsage</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#breakdownchart'><code>BreakdownChart</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#linechart'><code>LineChart</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#linechart.line'><code>LineChart.Line</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#linechart.point'><code>LineChart.Point</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#piechart'><code>PieChart</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#piechart.point'><code>PieChart.Point</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usagechart'><code>UsageChart</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#visualizationretrievebreakdownrequest'><code>VisualizationRetrieveBreakdownRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#visualizationretrieveusagerequest'><code>VisualizationRetrieveUsageRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#visualizationretrievebreakdownresponse'><code>VisualizationRetrieveBreakdownResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#visualizationretrieveusageresponse'><code>VisualizationRetrieveUsageResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieveBreakdown`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#visualizationretrievebreakdownrequest'>VisualizationRetrieveBreakdownRequest</a></code>|<code><a href='#visualizationretrievebreakdownresponse'>VisualizationRetrieveBreakdownResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveUsage`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#visualizationretrieveusagerequest'>VisualizationRetrieveUsageRequest</a></code>|<code><a href='#visualizationretrieveusageresponse'>VisualizationRetrieveUsageResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `BreakdownChart`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

