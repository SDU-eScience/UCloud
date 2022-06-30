package dk.sdu.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext

object ProcessingScope : CoroutineScope {
    private val job = SupervisorJob()
    @OptIn(DelicateCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = job + newFixedThreadPoolContext(30, "Processing")
}
