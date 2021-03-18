package dk.sdu.cloud.file.api

import java.text.DecimalFormat

private val bytesToStringFormat: DecimalFormat by lazy { DecimalFormat("#0.00") }

/**
 * Converts an amount of [bytes] to a human readable string.
 *
 * The format is: "$bytes $unit". Example: 10 MB.
 *
 * For any unit, except bytes, the value is converted to floating point with a precision of two decimal points.
 */
fun bytesToString(bytes: Long): String {
    @Suppress("DuplicatedCode")
    return when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "${bytesToStringFormat.format(bytes / 1_000.0)} KB"
        bytes < 1_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000.0)} MB"
        bytes < 1_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000.0)} GB"
        bytes < 1_000_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000.0)} TB"
        bytes < 1_000_000_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000_000.0)} PB"
        else -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000_000_000.0)} EB"
    }
}

/**
 * Converts an amount of [bytes] to a human readable string.
 *
 * The format is: "$bytes $unit". Example: 10 MB.
 *
 * For any unit, except bytes, the value is converted to floating point with a precision of two decimal points.
 */
fun bytesToString(bytes: Double): String {
    @Suppress("DuplicatedCode")
    return when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "${bytesToStringFormat.format(bytes / 1_000.0)} KB"
        bytes < 1_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000.0)} MB"
        bytes < 1_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000.0)} GB"
        bytes < 1_000_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000.0)} TB"
        bytes < 1_000_000_000_000_000_000 -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000_000.0)} PB"
        else -> "${bytesToStringFormat.format(bytes / 1_000_000_000_000_000_000.0)} EB"
    }
}
