package dk.sdu.cloud.service.test

import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install

fun <T> withDatabase(consumer: (HibernateSessionFactory) -> T): T {
    val micro = initializeMicro()
    micro.install(HibernateFeature)

    return consumer(micro.hibernateDatabase)
}
