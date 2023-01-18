package dk.sdu.cloud.integration.utils

import java.util.concurrent.atomic.AtomicInteger

private val uniqueValue = System.currentTimeMillis().toString(32)
private val uniqueValueAcc = AtomicInteger(0)
fun generateId(prefix: String, suffix: String = ""): String {
    return prefix + uniqueValue + uniqueValueAcc.getAndIncrement() + suffix
}
