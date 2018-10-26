package dk.sdu.cloud.app.abacus.services

interface JobDao<Session> {
    fun insertMapping(session: Session, systemId: String, slurmId: Long)
    fun resolveSystemId(session: Session, slurmId: Long): String?
    fun resolveSlurmId(session: Session, systemId: String): Long?
}

