import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import java.text.*
import org.jetbrains.exposed.sql.*
import com.zaxxer.hikari.*
import dk.sdu.cloud.pg_rest_service.controller.AppController
import dk.sdu.cloud.pg_rest_service.dao.AppEntity
import io.ktor.request.receive
import mu.*

/*
    Init Postgresql database connection
 */
fun initDB() {
    val config = HikariConfig("/hikari.properties")
    val ds = HikariDataSource(config)
    Database.connect(ds)
}

fun Application.main() {
    install(Compression)
    install(CORS) {
        anyHost()
    }
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }
    initDB()
    install(Routing) {

        val logger = KotlinLogging.logger { }

        route("/api") {
            route("/messages") {

                val appController = AppController()

                get("/") {
                    call.respond(appController.index())
                }

                post("/") {
                    val app = call.receive<AppEntity>()
                    logger.debug { app }
                    call.respond(appController.create(app))
                }

                get("/{id}") {
                    val id = call.parameters["id"]!!.toInt()
                    call.respond(appController.show(id))
                }

                put("/{id}") {
                    val id = call.parameters["id"]!!.toInt()
                    val app = call.receive<AppEntity>()
                    call.respond(appController.update(id, app))
                }

                delete("/{id}") {
                    val id = call.parameters["id"]!!.toInt()
                    call.respond(appController.delete(id))
                }
            }
        }
    }
}