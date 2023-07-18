package dk.sdu.cloud.app.orchestrator.services

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

// NOTE(Dan): This small class stores a thread-safe sorted integer set. It utilizes a load-balancing scheme along with
// RW-locks to minimize contention. This implementation purposefully does not suspend but instead chooses to block the
// thread due to the very high cost of rescheduling a coroutine. This set should not be used for temporary
// calculations due to the relatively high overhead. This implementation assumes that values are balanced well with
// `value % numberOfShards`. If that is not the case, then don't use this.
class ShardedSortedIntegerSet(private val numberOfShards: Int = 32) {
    private val shards = Array(numberOfShards) { IntList() }
    private val rwLocks = Array(numberOfShards) { ReentrantReadWriteLock() }

    fun add(value: Int) {
        val index = value % numberOfShards

        rwLocks[index].write {
            shards[index].addSortedSet(value)
        }
    }

    fun findValues(output: IntList, minimumValueInclusive: Int, maximumSize: Int = Int.MAX_VALUE) {
        for (i in 0..<numberOfShards) {
            rwLocks[i].readLock().lock()
        }

        try {
            for (shard in shards) {
                for (entry in shard) {
                    if (entry >= minimumValueInclusive) {
                        output.addSortedSet(entry)
                        if (output.size >= maximumSize) return
                    }
                }
            }
        } finally {
            for (i in 0..<numberOfShards) {
                rwLocks[i].readLock().unlock()
            }
        }
    }
}

class ShardedSortedLongSet(private val numberOfShards: Int = 32) {
    private val shards = Array(numberOfShards) { LongList() }
    private val rwLocks = Array(numberOfShards) { ReentrantReadWriteLock() }

    fun add(value: Long) {
        val index = (value % numberOfShards).toInt()

        rwLocks[index].write {
            shards[index].addSortedSet(value)
        }
    }

    fun findValues(output: LongList, minimumValueInclusive: Long, maximumSize: Int = Int.MAX_VALUE) {
        for (i in 0..<numberOfShards) {
            rwLocks[i].readLock().lock()
        }

        try {
            for (shard in shards) {
                for (entry in shard) {
                    if (entry >= minimumValueInclusive) {
                        output.addSortedSet(entry)
                        if (output.size >= maximumSize) return
                    }
                }
            }
        } finally {
            for (i in 0..<numberOfShards) {
                rwLocks[i].readLock().unlock()
            }
        }
    }
}
