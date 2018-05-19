import dk.sdu.cloud.service.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>) {
    Database.connect(
        url = "jdbc:postgresql://localhost:5432/test",
        driver = "org.postgresql.Driver",
        user = "postgres",
        password = "postgres"
    )

    transaction {
        drop(Test)
        create(Test)

        drop(ObjectTable)
        create(ObjectTable)

        drop(DynamicTable)
        create(DynamicTable)
    }

    transaction {
        Test.insert {
            it[array] = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        }

        repeat(10) { num ->
            Test.insert {
                it[array] = listOf(num)
            }
        }

        repeat(10) { num ->
            ObjectTable.insert {
                it[foo] = SomeData(Pair(num, num * 10))
            }
        }
    }

    transaction {
        val test = castToJson<Any>(stringParam("[1, 2, 3, 4]"))//.jsonLookup<Any>(intLiteral(1))
        Test.slice(test).selectAll().toList().forEach {
            val message = it[test]
            println(message)
            println(message.javaClass)
        }
    }

    transaction {
        Test.select {
            Test.array.jsonLookup<Int>(0) eq 4
        }.toList().forEach {
            println(it[Test.array])
        }
    }

    transaction {
        ObjectTable.select {
            ObjectTable.foo eq SomeData(Pair(1, 10))
        }.toList().forEach {
            println(it[ObjectTable.foo])
        }
    }

    transaction {
        DynamicTable.insert {
            it[dynamic] = mapOf(
                "a" to "bar",
                "b" to mapOf(
                    "foo" to 42,
                    "bar" to 100
                )
            )
        }

        DynamicTable.insert {
            it[dynamic] = mapOf(
                "a" to "bar"
            )
        }

        DynamicTable.select {
            DynamicTable.dynamic jsonContainsRight literalJson(mapOf("b" to mapOf("foo" to 42)))
        }.toList().forEach {
            println(it[DynamicTable.dynamic])
        }
    }
}

object Test : Table() {
    val array = jsonb<List<Int>>("array").nullable()
}

data class SomeData(val data: Pair<Int, Int>)

object ObjectTable : Table() {
    val foo = jsonb<SomeData>("foo")
}

object DynamicTable : Table() {
    val dynamic = jsonb<Map<String, Any?>>("dynamic")
}