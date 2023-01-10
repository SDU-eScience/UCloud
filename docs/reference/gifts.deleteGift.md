[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Gifts](/docs/developer-guide/accounting-and-projects/grants/gifts.md)

# `gifts.deleteGift`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes a Gift by its DeleteGiftRequest.giftId_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deletegiftrequest'>DeleteGiftRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only project administrators of `Gift.resourcesOwnedBy` can delete the [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md).


