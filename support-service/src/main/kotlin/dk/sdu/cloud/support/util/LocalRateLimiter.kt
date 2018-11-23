package dk.sdu.cloud.support.util

import dk.sdu.cloud.SecurityPrincipal

private data class Visit(val username: String, val timestamp: Long)

class LocalRateLimiter(
    private val expireAfter: Long,
    private val limit: Int
) {
    private val visitTracker = HashMap<String, MutableList<Visit>>()

    fun checkAndTrack(user: SecurityPrincipal): Boolean {
        synchronized(this) {
            cleanup()

            val thisVisit = Visit(user.username, System.currentTimeMillis())
            val visits = visitTracker[user.username]

            return if (visits == null) {
                visitTracker[user.username] = arrayListOf(thisVisit)
                true
            } else {
                val canAdd = visits.size < limit
                if (canAdd) {
                    visits.add(thisVisit)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun cleanup() {
        synchronized(this) {
            val globalIt = visitTracker.iterator()
            while (globalIt.hasNext()) {
                val (_, visits) = globalIt.next()

                val visitsIt = visits.iterator()
                while (visitsIt.hasNext()) {
                    val visit = visitsIt.next()
                    if (System.currentTimeMillis() > visit.timestamp + expireAfter) {
                        visitsIt.remove()
                    }
                }

                if (visits.isEmpty()) {
                    globalIt.remove()
                }
            }
        }
    }
}
