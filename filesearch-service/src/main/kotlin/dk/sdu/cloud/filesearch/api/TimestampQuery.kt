package dk.sdu.cloud.filesearch.api

/**
 * A query for timestamps. Can filter for timestamps before and after a certain timestamp.
 *
 * If one [after] is not specified no upper limit is imposed. If [before] is not specified no lower limit is imposed.
 * If neither [after] or [before] is specified an exception is thrown.
 */
data class TimestampQuery(val after: Long?, val before: Long?) {
    init {
        if (after == null && before == null) throw IllegalArgumentException("Both after and before cannot be null")
    }
}
