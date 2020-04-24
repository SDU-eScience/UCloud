package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "downtimes")
data class DowntimeEntity(
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "start_time")
    var start: Date,
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "end_time")
    var end: Date,

    @Column(columnDefinition = "TEXT")
    var text: String,

    @Id
    @GeneratedValue
    var id: Long? = 0L
) {
    companion object : HibernateEntity<DowntimeEntity>, WithId<Long>
}

interface DowntimeDAO<Session> {
    fun add(
        session: Session,
        downtime: DowntimeWithoutId
    )

    fun remove(
        session: Session,
        id: Long
    )

    fun listAll(
        session: Session,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    fun listPending(
        session: Session,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    fun removeExpired(
        session: Session,
        user: SecurityPrincipal
    )

    fun getById(
        session: Session,
        id: Long
    ): Downtime
}