package dk.sdu.cloud.bare.services

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "sample_entities")
class SampleEntity(
    var contents: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<SampleEntity>, WithId<Long>
}
