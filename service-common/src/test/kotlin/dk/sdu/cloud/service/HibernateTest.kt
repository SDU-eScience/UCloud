package dk.sdu.cloud.service

import dk.sdu.cloud.service.db.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "simple_entity")
data class SimpleEntity(
    @Id
    @GeneratedValue
    var id: Long = 0,

    var someValue: String
) {
    companion object : HibernateEntity<SimpleEntity>, WithId<Long>
}

fun main(args: Array<String>) {
    HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = true)).use { factory ->
        factory.withTransaction { session ->
            repeat(10) {
                session.save(SimpleEntity(someValue = "Testing $it"))
            }

            println(SimpleEntity.list(session))
        }

        println(factory.generateDDL())
    }

}
