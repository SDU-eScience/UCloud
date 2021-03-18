package dk.sdu.cloud.service.db.async

/**
 * Utility function for returning the next value of a sequence
 */
suspend fun AsyncDBConnection.allocateId(sequence: String = "hibernate_sequence"): Long {
    return sendPreparedStatement("select nextval(?)", listOf(sequence)).rows.single().getLong(0)!!
}
