package dk.sdu.cloud.filesearch.api

data class TimestampQuery(val after: Long?, val before: Long?) {
    init {
        if (after == null && before == null) throw IllegalArgumentException("Both after and before cannot be null")
    }
}
