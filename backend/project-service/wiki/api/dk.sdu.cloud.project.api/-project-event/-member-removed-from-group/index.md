[api](../../../index.md) / [dk.sdu.cloud.project.api](../../index.md) / [ProjectEvent](../index.md) / [MemberRemovedFromGroup](./index.md)

# MemberRemovedFromGroup

`data class MemberRemovedFromGroup : `[`ProjectEvent`](../index.md)

Note this event is *not* fired when a member is removed from the project.

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | Note this event is *not* fired when a member is removed from the project.`MemberRemovedFromGroup(projectId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, memberUsername: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, group: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`)` |

### Properties

| Name | Summary |
|---|---|
| [group](group.md) | `val group: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [memberUsername](member-username.md) | `val memberUsername: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [projectId](project-id.md) | `val projectId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
