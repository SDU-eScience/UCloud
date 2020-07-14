[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectEvent](./index.md)

# ProjectEvent

`sealed class ProjectEvent`

### Types

| Name | Summary |
|---|---|
| [Created](-created/index.md) | `data class Created : `[`ProjectEvent`](./index.md) |
| [Deleted](-deleted/index.md) | `data class Deleted : `[`ProjectEvent`](./index.md) |
| [GroupCreated](-group-created/index.md) | `data class GroupCreated : `[`ProjectEvent`](./index.md) |
| [GroupDeleted](-group-deleted/index.md) | `data class GroupDeleted : `[`ProjectEvent`](./index.md) |
| [GroupRenamed](-group-renamed/index.md) | `data class GroupRenamed : `[`ProjectEvent`](./index.md) |
| [MemberAdded](-member-added/index.md) | `data class MemberAdded : `[`ProjectEvent`](./index.md) |
| [MemberAddedToGroup](-member-added-to-group/index.md) | `data class MemberAddedToGroup : `[`ProjectEvent`](./index.md) |
| [MemberDeleted](-member-deleted/index.md) | `data class MemberDeleted : `[`ProjectEvent`](./index.md) |
| [MemberRemovedFromGroup](-member-removed-from-group/index.md) | Note this event is *not* fired when a member is removed from the project.`data class MemberRemovedFromGroup : `[`ProjectEvent`](./index.md) |
| [MemberRoleUpdated](-member-role-updated/index.md) | `data class MemberRoleUpdated : `[`ProjectEvent`](./index.md) |

### Properties

| Name | Summary |
|---|---|
| [projectId](project-id.md) | `abstract val projectId: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
