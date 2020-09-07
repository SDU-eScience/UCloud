[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [acceptInvite](./accept-invite.md)

# acceptInvite

`val acceptInvite: CallDescription<`[`AcceptInviteRequest`](../-accept-invite-request/index.md)`, `[`AcceptInviteResponse`](../-accept-invite-response.md)`, CommonErrorMessage>`

Accepts the [invite](invite.md) to a project.

Only the recipient of the invite can call this endpoint. This call will fail if an invite has already been sent
to the user.

