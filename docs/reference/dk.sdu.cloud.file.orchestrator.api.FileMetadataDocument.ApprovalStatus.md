# `FileMetadataDocument.ApprovalStatus`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The approval status of a metadata document_

```kotlin
sealed class ApprovalStatus {
    class Approved : ApprovalStatus()
    class Pending : ApprovalStatus()
    class Rejected : ApprovalStatus()
    class NotRequired : ApprovalStatus()
}
```

