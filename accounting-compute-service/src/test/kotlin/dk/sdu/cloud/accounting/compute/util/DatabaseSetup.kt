package dk.sdu.cloud.accounting.compute.util

import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking

data class DatabaseContext(val micro: Micro)

fun withDatabase(closure: suspend DatabaseContext.(HibernateSessionFactory) -> Unit) {
    runBlocking {
        val micro = initializeMicro().apply {
            install(HibernateFeature)
        }

        val ctx = DatabaseContext(micro)
        ctx.closure(micro.hibernateDatabase)
    }
}
