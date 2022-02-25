[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/filecollections.md)

# `MemberFilesFilter`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Filter for member files._

```kotlin
enum class MemberFilesFilter {
    SHOW_ONLY_MINE,
    SHOW_ONLY_MEMBER_FILES,
    DONT_FILTER_COLLECTIONS,
}
```
A member files collection must use the following format to be recognized: "Member Files: $username"

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>SHOW_ONLY_MINE</code> Shows only the requesting user's personal member file along with all other collections
</summary>





</details>

<details>
<summary>
<code>SHOW_ONLY_MEMBER_FILES</code> Shows only the member file collections and hides all others
</summary>





</details>

<details>
<summary>
<code>DONT_FILTER_COLLECTIONS</code> Applies no filter and shows both normal collections and member files
</summary>





</details>



</details>


