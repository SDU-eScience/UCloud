                            [UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Resources](/docs/developer-guide/core/resources.md)
                            
                            # `AclEntity`

                            
[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class AclEntity {
    class ProjectGroup : AclEntity()
    class User : AclEntity()
}
```

