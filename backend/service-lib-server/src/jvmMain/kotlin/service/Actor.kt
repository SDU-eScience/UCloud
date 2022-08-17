package dk.sdu.cloud.service

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityPrincipalOrNull
import dk.sdu.cloud.calls.server.signedIntent
import dk.sdu.cloud.toActorOrGuest

val CallHandler<*, *, *>.actorAndProject: ActorAndProject
    get() = ActorAndProject(ctx.securityPrincipalOrNull.toActorOrGuest(), ctx.project, ctx.signedIntent)
