package esciencecloudui

import io.ktor.application.call
import io.ktor.locations.get
import io.ktor.locations.location
import io.ktor.locations.post
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Route


@location("/getFiles")
data class GetFiles(val path: String)

@location("/favouriteFile")
data class FavouriteFile(val path: String)

@location("/getBreadcrumbs")
data class GetBreadCrumbs(val path: String)

@location("/getFavouritesSubset")
class FavouriteSubset

@location("/getMostRecentFiles")
class RecentlyModified

@location("/getRecentWorkflowStatus")
class RecentWorkFlowStatus

@location("/getWorkflows")
class WorkFlows

@location("/getApplications")
class GetApplications

@location("/getFavourites")
class Favourites

@location("/startJob")
class StartJob

@location("/getAnalyses")
class Analyses

@location("/getMessages")
class GetMessages

@location("/getNotifications")
class GetNotifications

@location("/getApplicationInfo")
data class GetApplicationInfo(val name: String, val version: String)

@location("/createDir")
data class CreateDirectory(val dirPath: String)

@location("/sendMessage")
class SendMessage

@location("/getRecentActivity")
class RecentActivity

@location("/getStatus")
class Status

@location("/getProjects")
class Projects

//TODO - modifications must be made by backend provider

fun Route.ajaxOperations() {

    get<FavouriteFile> {
        println("Favouriting files is not yet implemented!")
        call.respond(200)
    }

    get<RecentWorkFlowStatus> {
        // TODO Get actual workflow statuses
        call.respond(analyses.subList(0, 10))
    }

    get<WorkFlows> {
        // TODO Get actual workflows
        call.respond(workflows)
    }

    get<GetApplications> {
        // TODO Get actual applications
        call.respond(applications)
    }

    get<GetApplicationInfo> {
        call.respond(getApp(it.name, it.version) ?: "failure")
    }

    get<Analyses> {
        call.respond(analyses)
    }

    post<StartJob> {
        val application = call.receiveParameters()["application"]
        call.respond(200)
    }

    get<CreateDirectory> {
        call.respond(200)
    }

    get<GetMessages> {
        call.respond(messages)
    }

    get<GetNotifications> {
        call.respond(notifications)
    }
    post<SendMessage> {
        val parameters = call.receiveParameters()
        val to = parameters["to"]
        val content = parameters["content"]
        println(to)
        println(content)
        call.respond("")
    }
    get<RecentActivity> {
        val subset = notifications.subList(0, Math.min(10, notifications.size))
        subset.sortByDescending { it.timestamp }
        call.respond(subset)
    }
    get<Status> {
        call.respond(status)
    }

    get<Projects> {
        call.respond(projects)
    }
}

data class StatusNotification(val title: String, val level: String, val body: String)

val status = StatusNotification("No issues", "NO ISSUES", "No scheduled maintenance.")

/* Why coroutines are better
fun main(args: Array<String>) {
    val now = System.currentTimeMillis()
    runBlocking { repeat(10_000) { async { println(it) } } }
    val now2 = System.currentTimeMillis()
    val end1 = now2 - now
    System.currentTimeMillis()
    (0 until 10_000).map { Thread { println(it) }.also { it.start() } }.forEach { it.join() }
    val end2 = System.currentTimeMillis() - now2
    println("Coroutines: $end1")
    println("Threads: $end2")
}*/