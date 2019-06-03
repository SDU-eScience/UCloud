package dk.sdu.cloud.redis.cleaner

import dk.sdu.cloud.events.RedisStreamService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.service.CommonServer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start(): Unit = runBlocking {
        val redis = micro.eventStreamService as RedisStreamService
        val connManager = redis.connManager
        val dryRun = micro.commandLineArguments.contains("--dry")

        val syncConn = connManager.getSync()
        val allKeys = syncConn.keys("*")

        allKeys.forEach {
            cleanStream(syncConn, it, dryRun)
        }

        exitProcess(0)
    }

    private fun cleanStream(
        redis: RedisCommands<String, String>,
        keyName: String,
        dryRun: Boolean
    ) {
        val minTimestamp = System.currentTimeMillis() - MAX_AGE

        log.info("Inspecting stream $keyName")
        val xlen = try {
            redis.xlen(keyName)
        } catch (ex: RedisCommandExecutionException) {
            if (ex.message?.startsWith("WRONGTYPE") == true) return
            throw ex
        }

        if (xlen == 0L) return

        log.info("Cleaning stream $keyName")

        var count = 0L
        var range: Range<String> = Range.unbounded<String>()

        fun doTrim() {
            if (count == 0L) {
                log.info("$keyName did not find any entries which were too old.")
                return
            }

            val currentLength = redis.xlen(keyName)
            log.info("$keyName found $count entries which were too old. Trimming to size ${currentLength - count}.")

            if (currentLength - count < 0) {
                log.warn("$keyName currentLength - count <= 0")
                return
            }

            if (!dryRun) {
                redis.xtrim(keyName, true, currentLength - count)
            }
        }

        while (true) {
            val items = redis.xrange(keyName, range, Limit.from(RANGE_LIMIT))
            if (items.size == 0) {
                doTrim()
                break
            }

            val firstItemToKeep = items.indexOfFirst { parseTimestamp(it.id) > minTimestamp }
            if (firstItemToKeep != -1) {
                count += max(0, firstItemToKeep - 1)
                doTrim()
                break
            }

            count += items.size

            val (ts, id) = parseId(items.last().id)
            range = Range.create("$ts-${id.toLong() + 1}", "+")
            log.debug("$keyName $range")
        }
    }

    private fun parseTimestamp(id: String): Long = id.split("-").first().toLong()
    private fun parseId(id: String): Pair<String, String> = id.split("-").let { Pair(it[0], it[1]) }

    companion object {
        const val MAX_AGE = (1000L * 60 * 60 * 24 * 60)
        const val RANGE_LIMIT = 1000L
    }
}
