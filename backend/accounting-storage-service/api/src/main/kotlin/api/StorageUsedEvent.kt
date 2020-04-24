package dk.sdu.cloud.accounting.storage.api

import dk.sdu.cloud.accounting.api.AccountingEvent

data class StorageUsedEvent(
    override val timestamp: Long,
    val bytesUsed: Long,
    val id: Long,
    val user: String
) : AccountingEvent {
    override val title: String = "Storage used"
    override val description: String? = humanReadableByteCount(bytesUsed)
}

// https://stackoverflow.com/a/3758880
private fun humanReadableByteCount(bytes: Long, si: Boolean = true): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}
