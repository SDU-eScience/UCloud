package dk.sdu.cloud.calls.server

class WithNewContext<T : IngoingCall> @PublishedApi internal constructor(val ctx: T)

inline fun <reified T : IngoingCall> CallHandler<*, *, *>.withContext(handler: WithNewContext<T>.() -> Unit) {
    val ctx = ctx as? T
    if (ctx != null) {
        WithNewContext(ctx).handler()
    }
}
