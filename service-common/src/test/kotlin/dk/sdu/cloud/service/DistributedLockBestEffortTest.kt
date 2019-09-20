package dk.sdu.cloud.service

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.Ignore

@Ignore("Requires real Redis instance")
class DistributedLockBestEffortTest {
    private val description = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    private val micro = Micro().also { ctx ->
        ctx.initWithDefaultFeatures(
            description,
            arrayOf("--dev", "--config-dir", "${System.getProperty("user.home")}/sducloud")
        )
    }

    private val lockName = "lock"
    private val lockFactory = DistributedLockBestEffortFactory(micro)

    @Test
    fun `test basic usage`(): Unit = runBlocking {
        var lockedResource: String? = null

        val threads = (0 until 10).map { id ->
            GlobalScope.launch {
                val lock = lockFactory.create(lockName)
                val clientString = "client-$id"
                repeat(10) {
                    lock.withLock {
                        println("$clientString has acquired the lock")
                        assert(lockedResource == null)
                        lockedResource = clientString

                        repeat(1000) {
                            assert(lockedResource == clientString)
                        }

                        lockedResource = null
                        println("$clientString is releasing the lock!")
                    }

                    delay(Random.nextLong(150))
                }
            }
        }

        threads.joinAll()
    }

    @Test
    fun `test liveness property`(): Unit = runBlocking {
        val lock1 = lockFactory.create(lockName, duration = 1_000)
        val lock2 = lockFactory.create(lockName, duration = 1_000)

        val job = GlobalScope.launch {
            lock1.withLock {
                while (isActive) {
                    delay(100)
                    println("Hi!")
                }
            }
        }

        delay(2_000)

        lock2.withLock {
            println("Got lock!")
            job.cancel()
        }

        job.join()
    }
}