package dk.sdu.cloud.service

/**
 * A result produced from a [ScrollRequest].
 *
 * Scrolls work similarly to [Page]s in that they both produce a view of a resource that can paginate through
 * all results. Scrolls differ by not requiring the backend to know how many results will be produced. It also gives
 * freedom to the backend to choose any type of offset, not just integer based offsets. This could allow a backend
 * to produce more stable results than traditional pagination.
 */
data class ScrollResult<Item, OffsetType : Any>(
    val items: List<Item>,
    val nextOffset: OffsetType,
    val endOfScroll: Boolean
)

/**
 * A request for a [ScrollResult].
 *
 * Should only be used for user-facing requests. Must be [normalize]d before use.
 */
data class ScrollRequest<OffsetType : Any>(
    val offset: OffsetType? = null,
    val scrollSize: Int? = null
) {
    fun normalize(): NormalizedScrollRequest<OffsetType> = NormalizedScrollRequest(offset, scrollSize)
}

class NormalizedScrollRequest<OffsetType : Any>(
    val offset: OffsetType? = null,
    scrollSize: Int? = null
) {
    val scrollSize = when (scrollSize) {
        10, 25, 50, 100, 250 -> scrollSize
        else -> 50
    }
}
