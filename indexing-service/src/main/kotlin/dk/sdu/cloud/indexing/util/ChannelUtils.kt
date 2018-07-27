package dk.sdu.cloud.indexing.util

import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.ticker
import kotlinx.coroutines.experimental.selects.select
import java.util.concurrent.TimeUnit

fun <E> ReceiveChannel<E>.windowed(delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): ReceiveChannel<List<E>> {
    // TODO This is probably very bad
    val dataChannel = this
    val ticker = ticker(delay, unit)

    return produce {
        val buffered = ArrayList<E>()

        try {
            while (!dataChannel.isClosedForReceive) {
                select<Unit> {
                    dataChannel.onReceive {
                        buffered.add(it)
                    }

                    ticker.onReceive {
                        if (buffered.isNotEmpty()) {
                            send(buffered.toList())
                            buffered.clear()
                        }
                    }
                }
            }
        } finally {
            ticker.cancel()
        }
    }
}
