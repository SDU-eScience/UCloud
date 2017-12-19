package esciencecloudui

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.locations.get
import io.ktor.locations.location
import io.ktor.locations.post
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Route
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth
import org.esciencecloud.asynchttp.asJson
import java.util.*


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

//TODO - modifications must be made by backend provider

fun Route.ajaxOperations() {
    requireAuthentication()

    get<GetFiles> {
        val fixedPath = when (it.path) {
            "/" -> "/home/${call.irodsUser.username}/"
            else -> it.path.removePrefix("storage://tempZone")
        }
        val files = call.getFiles(fixedPath)
        call.respond(files)
    }

    get<GetBreadCrumbs> {
        val cleanedPath = it.path.removePrefix("/home/${call.irodsUser.username}/")
        val paths = cleanedPath.split("/").filterNot { it == "" }
        val breadcrumbs = arrayListOf(Pair("home", "/home/${call.irodsUser.username}/"))
        paths.forEachIndexed { index, s ->
            var currentPath = "/home/${call.irodsUser.username}/"
            (0..index).forEach { i ->
                currentPath += paths[i] + "/"
            }
            breadcrumbs.add(Pair(s, currentPath))
        }
        call.respond(breadcrumbs)
    }

    get<FavouriteFile> {
        println("Favouriting files is not yet implemented!")
        call.respond(200)
    }

    get<FavouriteSubset> {
        // TODO Get actual favourites
        val files = call.getFiles("/home/${call.irodsUser.username}/")
        call.respond(files.subList(0, minOf(files.size, 10)))
    }

    get<RecentlyModified> {
        // TODO Get actual most recently used files
        val files = call.getFiles("/home/${call.irodsUser.username}/")
        val sorted = files.sortedByDescending(StorageFile::modifiedAt)
        call.respond(sorted.subList(0, minOf(sorted.size, 10)))
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

    get<Favourites> {
        // TODO get actual favourite files
        val wrongFavouriteFiles = call.getFavouriteFiles()
        call.respond(wrongFavouriteFiles)
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
}

data class StatusNotification(val title: String, val level: String, val body: String)

val status = StatusNotification("No issues", "NO ISSUES", "No scheduled maintenance.")

suspend fun ApplicationCall.getAbacusApplication(name: String, version: String): ApplicationAbacus? {
    val user = irodsUser
    val response = HttpClient.get("http://cloud.sdu.dk:8080/api/hpc/apps/$name/$version") {
        addBasicAuth(user.username, user.password)
    }
    return when (response.statusCode == 200) {
        true -> response.asJson()
        false -> null
    }
}

private suspend fun ApplicationCall.getAbacusApplications(): List<ApplicationAbacus> {
    val user = irodsUser
    val response = HttpClient.get("http://cloud.sdu.dk:8080/api/hpc/apps") {
        addBasicAuth(user.username, user.password)
    }
    return when (response.statusCode == 200) {
        true -> response.asJson()
        false -> emptyList()
    }
}

private suspend fun ApplicationCall.getHPCJobs(): List<Analysis> {
    val user = irodsUser
    val response = HttpClient.get("http://cloud.sdu.dk:8080/api/hpc/myjobs") {
        addBasicAuth(user.username, user.password)
    }
    return when (response.statusCode == 200) {
        true -> response.asJson()
        false -> emptyList()
    }
}

private suspend fun ApplicationCall.getFiles(path: String): List<StorageFile> {
    val user = irodsUser
    val response = HttpClient.get("http://cloud.sdu.dk:8080/api/files?path=$path") {
        addBasicAuth(user.username, user.password)
    }

    return when (response.statusCode == 200) {
        true -> response.asJson()
        false -> emptyList()
    }
}

private suspend fun ApplicationCall.getFavouriteFiles(): List<StorageFile> {
    val user = irodsUser
    // TODO -- Get correct favourites
    val wrongLocation = "http://cloud.sdu.dk:8080/api/files?path=/home/"
    val response = HttpClient.get(wrongLocation) {
        addBasicAuth(user.username, user.password)
    }
    return when (response.statusCode == 200) {
        true -> response.asJson()
        false -> emptyList()
    }
}

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