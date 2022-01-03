[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `accounting.charge`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Records usage in the system_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Internal UCloud services invoke this endpoint to record usage from a workspace. Providers report data 
indirectly to this API through the outgoing `Control` API. This endpoint causes changes in the balances 
of the targeted allocation and ancestors. UCloud will change the `balance` and `localBalance` property 
of the targeted allocation. Ancestors of the targeted allocation will only update their `balance`.

UCloud returns a boolean, for every request, indicating if the charge was successful. A charge is 
successful if no affected allocation went into a negative balance.

---

__📝 NOTE:__ Unsuccessful charges are still deducted in their balances.

---

The semantics of `charge` depends on the Product's payment model.

__Absolute:__

- UCloud calculates the change in balances by multiplying: the Product's pricePerUnit, the number of 
  units, the number of periods
- UCloud subtracts this change from the balances

__Differential:__

- UCloud calculates the change in balances by comparing the units with the current `localBalance`
- UCloud subtracts this change from the balances
- Note: This change can cause the balance to go up, if the usage is lower than last period

#### Selecting Allocations

The charge operation targets a wallet (by combining the ProductCategoryId and WalletOwner). This means 
that the charge operation have multiple allocations to consider. We explain the approach for absolute 
payment models. The approach is similar for differential products.

UCloud first finds a set of leaf allocations which, when combined, can carry the full change. UCloud 
first finds a set of candidates. We do this by sorting allocations by the Wallet's `chargePolicy`. By 
default, this means that UCloud prioritizes allocations that expire soon. UCloud only considers 
allocations which are active and have a positive balance.

---

__📝 NOTE:__ UCloud does not consider ancestors at this point in the process.

---

UCloud now creates the list of allocations which it will use. We do this by performing a rolling sum of 
the balances. UCloud adds an allocation to the set if the rolling sum has not yet reached the total 
amount.

UCloud will use the full balance of each selected allocation. The only exception is the last element, 
which might use less. If the change in balance is never reached, then UCloud will further charge the 
first selected allocation. In this case, the priority allocation will have to pay the difference.

Finally, the system updates the balances of each selected leaf, and all of their ancestors.

__Examples:__

| Example |
|---------|
| [Charging a root allocation (Absolute)](/docs/reference/accounting_charge-absolute-single.md) |
| [Charging a leaf allocation (Absolute)](/docs/reference/accounting_charge-absolute-multi.md) |
| [Charging a leaf allocation with missing credits (Absolute)](/docs/reference/accounting_charge-absolute-multi-missing.md) |
| [Charging a root allocation (Differential)](/docs/reference/accounting_charge-differential-single.md) |
| [Charging a leaf allocation (Differential)](/docs/reference/accounting_charge-differential-multi.md) |
| [Charging a leaf allocation with missing credits (Differential)](/docs/reference/accounting_charge-differential-multi-missing.md) |


