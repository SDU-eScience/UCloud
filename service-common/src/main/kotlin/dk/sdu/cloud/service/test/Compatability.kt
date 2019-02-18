package dk.sdu.cloud.service.test

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install

@Deprecated("Should no longer be used")
fun <T> withDatabase(consumer: (HibernateSessionFactory) -> T): T {
    val micro = initializeMicro()
    micro.install(HibernateFeature)

    return consumer(micro.hibernateDatabase)
}
