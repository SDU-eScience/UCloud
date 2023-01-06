[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Gifts](/docs/developer-guide/accounting-and-projects/grants/gifts.md)

# `gifts.claimGift`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Claims a `Gift` to the calling user's personal project_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#claimgiftrequest'>ClaimGiftRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

User errors:
 - Users who are not eligible for claiming this `Gift` will receive an appropriate error code.
 - If the gifting project has run out of resources then this endpoint will fail. The gift will not be 
   marked as claimed.


