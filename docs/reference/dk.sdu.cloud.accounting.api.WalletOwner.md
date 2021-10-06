# `WalletOwner`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class WalletOwner {
    class User : WalletOwner()
    class Project : WalletOwner()
}
```

