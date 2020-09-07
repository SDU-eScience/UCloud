[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [rejectInvite](./reject-invite.md)

# rejectInvite

`val rejectInvite: CallDescription<`[`RejectInviteRequest`](../-reject-invite-request/index.md)`, `[`RejectInviteResponse`](../-reject-invite-response.md)`, CommonErrorMessage>`

Rejects the invite to a project.

The recipient of the invite *and* a project administrator of the project can call this endpoint.
Calling this will invalidate the invite and a new invite must be sent to the user if they wish to join.

