package dk.sdu.cloud.zenodo.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.jpa.sduclouddb.ZenodoPublicationDataobjectRel
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.zenodo.api.*
import io.ktor.http.HttpStatusCode
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

typealias JPAZenodoPublication = dk.sdu.cloud.jpa.sduclouddb.ZenodoPublication
typealias JPAZenodoUpload = dk.sdu.cloud.jpa.sduclouddb.ZenodoPublicationDataobjectRel

fun JPAZenodoPublication.toModel(): ZenodoPublication =
    ZenodoPublication(
        id,
        name,
        ZenodoPublicationStatus.valueOf(status),
        zenodoId?.let { "https://sandbox.zenodo.org/deposit/$it" }, // TODO Hardcoded string
        createdTs.time,
        modifiedAt.time
    )

fun JPAZenodoUpload.toModel(): ZenodoUpload = ZenodoUpload(
    dataobjectRefId,
    hasBeenTransmitted,
    modifiedTs.time
)

sealed class PublicationException : RuntimeException() {
    abstract val connected: Boolean
    abstract val recommendedStatusCode: HttpStatusCode
    abstract override val message: String

    class NotConnected : PublicationException() {
        override val connected = false
        override val recommendedStatusCode = HttpStatusCode.Unauthorized
        override val message = "Not connected"
    }

    class NotFound : PublicationException() {
        override val connected = true
        override val recommendedStatusCode = HttpStatusCode.NotFound
        override val message = "Not found"
    }
}

class PublicationService(
    private val cloudEmf: EntityManagerFactory,
    private val zenodo: ZenodoRPCService
) {

    private fun createEntityManager() = cloudEmf.createEntityManager()

    fun findById(jwt: DecodedJWT, id: Int): ZenodoPublicationWithFiles {
        if (!isConnected(jwt)) throw PublicationException.NotConnected()
        val cloudEntityManager = createEntityManager()

        val result =
            cloudEntityManager.createNamedQuery("ZenodoPublication.findById", JPAZenodoPublication::class.java).apply {
                setParameter("id", id)
            }.resultList.singleOrNull()

        result ?: throw PublicationException.NotFound()

        // TODO Caching in JPA is weird.
        // I don't understand why I have to do this to avoid getting an empty list for the uploads
        cloudEntityManager.refresh(result)

        val publication = result.toModel()
        val uploads = result.zenodoPublicationDataobjectRelCollection.map { it.toModel() }
        return ZenodoPublicationWithFiles(publication, uploads)
    }

    fun findForUser(
        jwt: DecodedJWT,
        pagination: NormalizedPaginationRequest
    ): ZenodoPublicationList {
        if (!isConnected(jwt)) throw PublicationException.NotConnected()
        val cloudEntityManager = createEntityManager()

        val publications = cloudEntityManager.createQuery(
            "SELECT z FROM ZenodoPublication z WHERE z.personRefId = :personRefId ORDER BY z.modifiedAt DESC",
            JPAZenodoPublication::class.java
        ).apply {
            setParameter("personRefId", jwt.subject)
            firstResult = pagination.page * pagination.itemsPerPage
            maxResults = pagination.itemsPerPage
        }.resultList

        val count = cloudEntityManager.createQuery(
            "SELECT COUNT(z) FROM ZenodoPublication z WHERE z.personRefId = :personRefId", Int::class.java).apply {
            setParameter("personRefId", jwt.subject)
        }.resultList


        return ZenodoPublicationList(
            Page(
                itemsInTotal = count[0],
                itemsPerPage = pagination.itemsPerPage,
                pageNumber = pagination.page,
                items = publications.map { it.toModel() }
            )
        )
    }

    fun createUploadForFiles(jwt: DecodedJWT, name: String, filePaths: Set<String>): Int {
        if (!isConnected(jwt)) throw PublicationException.NotConnected()

        val filePathsList = filePaths.toList()
        var mutableFilesList = mutableListOf<String>()
        for (i in 0 until filePathsList.size) {
            var noDuplicate = true
            for (j in i+1 until filePathsList.size) {
                if ( filePathsList[i].substringAfterLast('/') == filePathsList[j].substringAfterLast('/')) {
                    noDuplicate = false
                    break
                }
            }
            if (noDuplicate) {
                mutableFilesList.add(filePathsList[i])
            }
        }
        println(mutableFilesList)
        createEntityManager().let { em ->
            return em.transaction.useTransaction {
                val now = Date()
                val publication = JPAZenodoPublication().apply {
                    status = ZenodoPublicationStatus.PENDING.toString()
                    zenodoId = null
                    createdTs = now
                    modifiedAt = now
                    personRefId = jwt.subject
                    this.name = name
                }
                em.persist(publication)

                mutableFilesList.forEach { objectId ->
                    em.persist(ZenodoPublicationDataobjectRel().apply {
                        publicationRefId = publication
                        dataobjectRefId = objectId
                        createdTs = now
                        modifiedTs = now
                        markedForDelete = false
                        hasBeenTransmitted = false
                    })
                }

                publication
            }.id
        }
    }

    fun markUploadAsCompleteInPublication(publicationId: Int, objectId: String) {
        createEntityManager().let { em ->
            em.transaction.useTransaction {
                val upload = em.createQuery(
                    """
                        SELECT z FROM ZenodoPublicationDataobjectRel z WHERE
                            z.publicationRefId.id = :publicationId AND
                            z.dataobjectRefId = :objectId
                    """,
                    JPAZenodoUpload::class.java
                ).apply {
                    setParameter("publicationId", publicationId)
                    setParameter("objectId", objectId)
                }.resultList.singleOrNull() ?: throw PublicationException.NotFound()

                upload.hasBeenTransmitted = true
                upload.modifiedTs = Date()
                em.persist(upload)
            }
        }
    }

    fun attachZenodoId(publicationId: Int, zenodoId: String) {
        createEntityManager().let { em ->
            em.transaction.useTransaction {
                val publication =
                    em.find(JPAZenodoPublication::class.java, publicationId) ?: throw PublicationException.NotFound()

                publication.zenodoId = zenodoId
                publication.modifiedAt = Date()
                em.persist(publication)
            }
        }
    }

    fun updateStatusOf(publicationId: Int, status: ZenodoPublicationStatus) {
        createEntityManager().let { em ->
            em.transaction.useTransaction {
                val publication =
                    em.find(JPAZenodoPublication::class.java, publicationId) ?: throw PublicationException.NotFound()

                publication.status = status.toString()
                publication.modifiedAt = Date()
                em.persist(publication)
            }
        }
    }

    private fun isConnected(jwt: DecodedJWT): Boolean = zenodo.isConnected(jwt)
}

inline fun <T> EntityTransaction.useTransaction(body: () -> T): T {
    begin()
    val result = body()
    commit()
    return result
}
