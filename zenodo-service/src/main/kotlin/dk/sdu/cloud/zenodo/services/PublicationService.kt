package dk.sdu.cloud.zenodo.services

import com.auth0.jwt.impl.NullClaim
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.jpa.sduclouddb.ZenodoPublicationDataobjectRel
import dk.sdu.cloud.zenodo.api.*
import io.ktor.http.HttpStatusCode
import org.eclipse.persistence.config.PersistenceUnitProperties
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.Persistence

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
        offset: Int = 0,
        maxDesiredResults: Int = 10
    ): ZenodoPublicationList {
        if (!isConnected(jwt)) throw PublicationException.NotConnected()
        val cloudEntityManager = createEntityManager()

        val publications = cloudEntityManager.createQuery(
            "SELECT z FROM ZenodoPublication z WHERE z.personRefId = :personRefId ORDER BY z.modifiedAt DESC",
            JPAZenodoPublication::class.java
        ).apply {
            setParameter("personRefId", jwt.subject)
            firstResult = offset
            maxResults = Math.min(maxDesiredResults, 30)
        }.resultList

        return ZenodoPublicationList(publications.map { it.toModel() })
    }

    fun createUploadForFiles(jwt: DecodedJWT, name: String, filePaths: Set<String>): Int {
        if (!isConnected(jwt)) throw PublicationException.NotConnected()
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

                filePaths.forEach { objectId ->
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

fun main(args: Array<String>) {

    /*
    cloudEntityManager.transaction.begin()
    repeat(100) {
        val publication = ZenodoPublication(
            UUID.randomUUID().toString(),
            ZenodoPublicationStatus.values().random().toString(),
            Date(),
            Date()
        )
        cloudEntityManager.persist(publication)
    }
    cloudEntityManager.transaction.commit()
    */

    /*
    val publicationById = cloudEntityManager.createNamedQuery(
        "ZenodoPublication.findById",
        JPAZenodoPublication::class.java
    )
    publicationById.setParameter("id", "84915e93-4656-4173-982a-635fd028a21f")
    val publication = publicationById.resultList.first()
    println(publication.id)
    println(publication.status)
    println(publication.createdTs)
    println(publication.modifiedAt)
    */

//    val zenodoPublicationService = PublicationService()
//    val jwt = DummyToken("test")

//    val message = zenodoPublicationService.findForUser(jwt)
//    println(message)
//    println(zenodoPublicationService.findById(jwt, message.inProgress.first().id))
    /*
    zenodoPublicationService.createUploadForFiles(
        jwt, "Testing", listOf(
            "/home/test/1.txt",
            "/home/test/2.txt",
            "/home/test/3.txt",
            "/home/test/4.txt"
        )
    )
    */
//    println(zenodoPublicationService.findById(jwt, 3))
//    zenodoPublicationService.markUploadAsCompleteInPublication(3, "/home/test/1.txt")
//    println(zenodoPublicationService.findById(jwt, 3).uploads.find { it.dataObject == "/home/test/1.txt" })
}

class DummyToken(
    subject: String,
    vararg audience: String
) : DecodedJWT {
    private val _subject = subject
    private val audience: MutableList<String> = audience.toMutableList()

    override fun getAlgorithm(): String? = null
    override fun getExpiresAt(): Date = Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)
    override fun getAudience(): MutableList<String> = audience
    override fun getId(): String? = null
    override fun getType(): String? = null
    override fun getSignature(): String? = null
    override fun getKeyId(): String? = null
    override fun getToken(): String = ""
    override fun getContentType(): String? = null
    override fun getNotBefore(): Date? = null
    override fun getSubject(): String = _subject
    override fun getIssuer(): String = "https://cloud.sdu.dk"
    override fun getIssuedAt(): Date = Date()
    override fun getClaim(name: String?): Claim = NullClaim()
    override fun getHeaderClaim(name: String?): Claim = NullClaim()
}