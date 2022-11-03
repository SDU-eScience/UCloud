[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md)

# `WalletOwner`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class WalletOwner {
    class Project : WalletOwner()
    class User : WalletOwner()
}
```


