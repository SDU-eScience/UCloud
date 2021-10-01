# Allocations

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


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
<td><a href='#charge'><code>charge</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#check'><code>check</code></a></td>
<td>Checks if one or more wallets are able to carry a charge</td>
</tr>
<tr>
<td><a href='#deposit'><code>deposit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rootdeposit'><code>rootDeposit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transfer'><code>transfer</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateallocation'><code>updateAllocation</code></a></td>
<td>Update an existing allocation</td>
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
<td><a href='#chargewalletrequestitem'><code>ChargeWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deposittowalletrequestitem'><code>DepositToWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rootdepositrequestitem'><code>RootDepositRequestItem</code></a></td>
<td>See `DepositToWalletRequestItem`</td>
</tr>
<tr>
<td><a href='#transfertowalletrequestitem'><code>TransferToWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateallocationrequestitem'><code>UpdateAllocationRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `charge`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `check`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)


_Checks if one or more wallets are able to carry a charge_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Checks if one or more charges would succeed without lacking credits. This will not generate a
transaction message, and as a result, the description will never be used.


### `deposit`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#deposittowalletrequestitem'>DepositToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rootDeposit`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#rootdepositrequestitem'>RootDepositRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transfer`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#transfertowalletrequestitem'>TransferToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAllocation`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Update an existing allocation_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#updateallocationrequestitem'>UpdateAllocationRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Updates one or more existing allocations. This endpoint will use all the provided values. That is,
you must provide all values, even if they do not change. This will generate a transaction indicating
the change. This will set the initial balance of the allocation, as if it was initially created with
this value.

The constraints that are in place during a standard creation are still in place when updating the
values. This means that the new start and end dates _must_ overlap with the values of all ancestors.



## Data Models

### `ChargeWalletRequestItem`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
data class ChargeWalletRequestItem(
    val payer: WalletOwner,
    val units: Long,
    val numberOfProducts: Long,
    val product: ProductReference,
    val performedBy: String,
    val description: String,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>payer</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The payer of this charge
</summary>





</details>

<details>
<summary>
<code>units</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of units that this charge is about
</summary>



The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the
number of minutes/hours/days.


</details>

<details>
<summary>
<code>numberOfProducts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of products involved in this charge, for example the number of nodes
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which the service is charging for
</summary>





</details>

<details>
<summary>
<code>performedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The username of the user who generated this request
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the charge this is used purely for presentation purposes
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `DepositToWalletRequestItem`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
data class DepositToWalletRequestItem(
    val recipient: WalletOwner,
    val sourceAllocation: String,
    val amount: Long,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>recipient</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The recipient of this deposit
</summary>





</details>

<details>
<summary>
<code>sourceAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the source allocation which the deposit will draw from
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The amount of credits to deposit into the recipient's wallet
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of this change. This is used purely for presentation purposes.
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become valid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.


</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become invalid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation will never
expire.


</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `RootDepositRequestItem`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


_See `DepositToWalletRequestItem`_

```kotlin
data class RootDepositRequestItem(
    val categoryId: ProductCategoryId,
    val recipient: WalletOwner,
    val amount: Long,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>recipient</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `TransferToWalletRequestItem`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
data class TransferToWalletRequestItem(
    val categoryId: ProductCategoryId,
    val target: WalletOwner,
    val source: WalletOwner,
    val amount: Long,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code> The category to transfer from
</summary>





</details>

<details>
<summary>
<code>target</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The target wallet to insert the credits into
</summary>





</details>

<details>
<summary>
<code>source</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The source wallet from where the credits is transferred from
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The amount of credits to transfer
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become valid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.


</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become invalid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation will never
expire.


</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `UpdateAllocationRequestItem`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    val startDate: Long,
    val endDate: Long?,
    val reason: String,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>reason</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

