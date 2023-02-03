[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `UserCriteria`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes some criteria which match a user_

```kotlin
sealed class UserCriteria {
    class Anyone : UserCriteria()
    class EmailDomain : UserCriteria()
    class WayfOrganization : UserCriteria()
}
```
This is used in conjunction with actions that require authorization.


