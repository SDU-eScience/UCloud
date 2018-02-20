package dk.sdu.cloud.zenodo.services

import dk.sdu.cloud.jpa.sduclouddb.ZenodoPublication
import dk.sdu.cloud.zenodo.api.ZenodoPublicationStatus
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import javax.persistence.EntityManager
import javax.persistence.Persistence
import javax.persistence.TypedQuery
import kotlin.reflect.KProperty1

fun <T> Array<T>.random(): T = get(ThreadLocalRandom.current().nextInt(size))

fun main(args: Array<String>) {
    val cloudEmf = Persistence.createEntityManagerFactory("SduClouddbJpaPU")
    val cloudEntityManager = cloudEmf.createEntityManager()

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

    val publicationById = cloudEntityManager.createNamedQuery("ZenodoPublication.findById",
        ZenodoPublication::class.java)
    publicationById.setParameter("id", "84915e93-4656-4173-982a-635fd028a21f")
    val publication = publicationById.resultList.first()
    println(publication.id)
    println(publication.status)
    println(publication.createdTs)
    println(publication.modifiedAt)
}

// It is not kotlin :-(
inline fun <reified T, P> EntityManager.createFindByQuery(property: KProperty1<T, P>, value: P): TypedQuery<T> {
    val result = createNamedQuery("${T::class.java.simpleName}.findBy${property.name.capitalize()}", T::class.java)
    result.setParameter(property.name, value)
    return result
}
