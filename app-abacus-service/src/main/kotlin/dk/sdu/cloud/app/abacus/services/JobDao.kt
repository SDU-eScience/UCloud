package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.service.db.HibernateSession

interface JobDao<Session> {
    fun insertMapping(session: Session, systemId: String, slurmId: Long)
    fun resolveSystemId(session: Session, slurmId: Long): String?
    fun resolveSlurmId(session: Session, systemId: String): Long?
}

class JobInMemoryDao : JobDao<HibernateSession> {
    private val slurmToSystem = HashMap<Long, String>()
    private val systemToSlurm = HashMap<String, Long>()

    override fun insertMapping(session: HibernateSession, systemId: String, slurmId: Long) {
        slurmToSystem[slurmId] = systemId
        systemToSlurm[systemId] = slurmId
    }

    override fun resolveSystemId(session: HibernateSession, slurmId: Long): String? {
        return slurmToSystem[slurmId]
    }

    override fun resolveSlurmId(session: HibernateSession, systemId: String): Long? {
        return systemToSlurm[systemId]
    }
}
