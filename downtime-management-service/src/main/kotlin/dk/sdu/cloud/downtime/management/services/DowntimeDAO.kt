package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import javax.persistence.*

@Entity
@Table(name = "downtimes")
data class DowntimeEntity(
    var start: Long,
    var end: Long,

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
        user: SecurityPrincipal,
        downtime: DowntimeWithoutId
    )

    fun remove(
        session: Session,
        user: SecurityPrincipal,
        id: Long
    )

    fun listAll(
        session: Session,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    fun listUpcoming(
        session: Session,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    fun removeExpired(
        session: Session,
        user: SecurityPrincipal
    )

    fun getById(
        session: Session,
        user: SecurityPrincipal,
        id: Long
    ): Downtime
}