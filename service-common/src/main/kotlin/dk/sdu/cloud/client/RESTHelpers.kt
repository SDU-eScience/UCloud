package dk.sdu.cloud.client

fun <S : Any, E : Any> RESTCallDescription<Unit, S, E, *>.prepare(): PreparedRESTCall<S, E> = prepare(Unit)

suspend fun <S : Any, E : Any> RESTCallDescription<Unit, S, E, *>.call(cloud: AuthenticatedCloud): RESTResponse<S, E> =
    call(Unit, cloud)
