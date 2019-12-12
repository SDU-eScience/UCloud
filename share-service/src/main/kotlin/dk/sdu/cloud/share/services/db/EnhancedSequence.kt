package dk.sdu.cloud.share.services.db

suspend fun AsyncDBConnection.allocateId(sequence: String = "hibernate_sequence"): Long {
    return sendPreparedStatement("select nextval(?)", listOf(sequence)).rows.single().getLong(0)!!
}
