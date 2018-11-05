package dk.sdu.cloud.service

import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.generateDDL
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.withTransaction
import org.hibernate.annotations.Type
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

@Entity
@Table
data class SimpleWithJson(
    @Id
    @GeneratedValue
    var id: Long = 0,

    var text: String,

    @Type(type = JSONB_TYPE)
    var json: LetMeBeJson
) {
    companion object : HibernateEntity<SimpleWithJson>, WithId<Long>
}


data class LetMeBeJson(
    val list: List<Pair<String, Int>>,
    val map: Map<String, Any>
)

fun main(args: Array<String>) {
    HibernateSessionFactory.create(H2_TEST_CONFIG.copy(showSQLInStdout = true)).use { factory ->
        /*
        factory.withTransaction { session ->
            repeat(10) {
                session.save(SimpleEntity(someValue = "Testing $it"))
            }

            println(SimpleEntity.list(session))
        }

        println(factory.generateDDL())
        */

        println(factory.generateDDL())

        factory.withTransaction { session ->
            val id = session.save(
                SimpleWithJson(
                    text = "Hello!",
                    json = LetMeBeJson(
                        listOf("good" to 42, "bad" to 0),
                        mapOf(
                            "alsoGood" to 1337,
                            "alsoBad" to 3
                        )
                    )
                )
            ) as Long

            val result = SimpleWithJson[session, id]
            repeat(10) { println() }
            println("Result is: $result")
            repeat(10) { println() }
        }
    }

}
