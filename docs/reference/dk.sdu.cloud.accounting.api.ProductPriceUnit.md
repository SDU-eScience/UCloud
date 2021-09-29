# `ProductPriceUnit`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
enum class ProductPriceUnit {
    PER_UNIT,
    CREDITS_PER_MINUTE,
    CREDITS_PER_HOUR,
    CREDITS_PER_DAY,
    UNITS_PER_MINUTE,
    UNITS_PER_HOUR,
    UNITS_PER_DAY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PER_UNIT</code> Used for resources which either: are charged once for the entire life-time (`ChargeType.ABSOLUTE`) or
</summary>



used to enforce a quota (`ChargeType.DIFFERENTIAL_QUOTA`).

When used in combination with `ChargeType.ABSOLUTE` then this is typically used for resources such as:
licenses, public IPs and links.

When used in combination with `ChargeType.DIFFERENTIAL_QUOTA` it is used to enforce a quota. At any point in
time, a user should never be allowed to use more units than specified in their current `balance`. For example,
if `balance = 100` and `ProductType = ProductType.COMPUTE` then no more than 100 jobs should be allowed to run
at any given point in time. Similarly, this can be used to enforce a storage quota.


</details>

<details>
<summary>
<code>CREDITS_PER_MINUTE</code> Used for resources which are charged periodically in a pre-defined currency.
</summary>



This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.

The pre-defined currency is decided between the UCloud/Core and the provider out-of-band. UCloud only supports
a single currency for the entire system.

The accounting system only stores balances as an integer, for precision reasons. As a result, a charge using
`CREDITS_PER_X` will always refer to one-millionth of the currency (for example: 1 Credit = 0.000001 DKK).

This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
operations this often, it only serves to define the true meaning of `units` in the `charge` operation.

The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
not be charged differently because of summer/winter time.


</details>

<details>
<summary>
<code>CREDITS_PER_HOUR</code> See `CREDITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>CREDITS_PER_DAY</code> See `CREDITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_MINUTE</code> Used for resources which are charged periodically.
</summary>



This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.

All allocations granted to a product of `UNITS_PER_X` specify the amount of units of the recipient can use.
Some examples include:

  - Core hours
  - Public IP hours
  
This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
operations this often, it only serves to define the true meaning of `units` in the `charge` operation.

The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
not be charged differently because of summer/winter time.


</details>

<details>
<summary>
<code>UNITS_PER_HOUR</code> See `UNITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_DAY</code> See `UNITS_PER_MINUTE`
</summary>





</details>



</details>

