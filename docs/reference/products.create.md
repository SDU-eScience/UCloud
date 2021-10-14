[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# `products.create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: SERVICE, ADMIN, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=SERVICE,+ADMIN,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  in UCloud_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#product'>Product</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only providers and UCloud administrators can create a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md). When this endpoint is
invoked by a provider, then the provider field of the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  must match the invoking user.

The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  will become ready and visible in UCloud immediately after invoking this call.
If no [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  has been created in this category before, then this category will be created.

---

__📝 NOTE:__ Most properties of a [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md)  are immutable and must not be changed.
As a result, you cannot create a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  later with different category properties.

---

If the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  already exists, then a new `version` of it is created. Version numbers are
always sequential and the incoming version number is always ignored by UCloud.


