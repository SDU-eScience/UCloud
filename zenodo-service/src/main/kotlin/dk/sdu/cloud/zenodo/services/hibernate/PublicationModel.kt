package dk.sdu.cloud.zenodo.services.hibernate

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.zenodo.api.ZenodoPublicationStatus
import dk.sdu.cloud.zenodo.api.ZenodoPublicationWithFiles
import dk.sdu.cloud.zenodo.api.ZenodoUpload
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "publication_data_objects")
internal class PublicationDataObjectEntity(
    @EmbeddedId
    var id: Key,

    var uploaded: Boolean = false,

    override var createdAt: Date = Date(),
    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<PublicationDataObjectEntity>,
        WithId<Long>

    @Embeddable
    data class Key(
        var dataObjectPath: String,

        @ManyToOne
        @JoinColumn(name = "publication_id")
        var publication: PublicationEntity
    ) : Serializable
}


@Entity
@Table(name = "publications")
internal class PublicationEntity(
    var name: String,

    var owner: String,

    @Enumerated(EnumType.STRING)
    var status: ZenodoPublicationStatus,

    var zenodoId: String? = null,

    @OneToMany(mappedBy = "id.publication")
    var dataObjects: MutableList<PublicationDataObjectEntity> = arrayListOf(),

    @Id
    @GeneratedValue
    var id: Long = 0,

    override var createdAt: Date = Date(),
    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<PublicationEntity>,
        WithId<Long>
}

internal fun PublicationEntity.toModel(): ZenodoPublicationWithFiles = ZenodoPublicationWithFiles(
    id,
    name,
    status,
    zenodoId?.let { "https://sandbox.zenodo.org/deposit/$it" }, // TODO Hardcoded string
    createdAt.time,
    modifiedAt.time,
    dataObjects.map { it.toModel() }
)

internal fun PublicationDataObjectEntity.toModel(): ZenodoUpload = ZenodoUpload(id.dataObjectPath, uploaded, modifiedAt.time)
