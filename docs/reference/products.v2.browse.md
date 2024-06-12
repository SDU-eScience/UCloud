[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# `products.v2.browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browse a set of products_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsv2browserequest'>ProductsV2BrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#productv2'>ProductV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint uses the normal pagination and filter mechanisms to return a list of [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md).

__Examples:__

| Example |
|---------|
| [Browse in the full product catalog](/docs/reference/products.v2_browse.md) |
| [Browse for a specific type of product (e.g. compute)](/docs/reference/products.v2_browse-by-type.md) |


