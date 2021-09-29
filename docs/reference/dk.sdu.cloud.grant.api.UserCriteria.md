# `UserCriteria`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class UserCriteria {
    class Anyone : UserCriteria()
    class EmailDomain : UserCriteria()
    class WayfOrganization : UserCriteria()
}
```

