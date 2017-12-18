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
}

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


/* Possible types: integer, text, float, input_file, ~output_file~ */
data class ApplicationField(val name: String, val prettyName: String, val description: String, val type: String, val defaultValue: String?, val isOptional: Boolean)
data class ApplicationAbacus(val info: ApplicationInfo, val parameters: List<ApplicationField>)
data class ApplicationInfo(val name: String, val version: String, val rating: Double = 5.0, val isPrivate: Boolean = false, val description: String = "An app to be run on Abacus", val author: String = "Anyone")
data class Workflow(val name: String, val applications: ArrayList<ApplicationAbacus>)
data class Analysis(val name: String, val status: String, var comments: List<Comment> = emptyList())
data class Notification(val message: String, val body: String, val timestamp: Long, val type: String, val jobId: String)
data class Comment(val author: String, val content: String, val timestamp: Long = 0)
data class Message(val from:String, val fromDate:Long, val content:String)

val messages = arrayListOf(
        Message("Dan Sebastian Thrane", 1, "I have a genuine dislike of iRODS."),
        Message("Jonas Malte Hinchely", 12903, "I writing to you from the future to warn you about the inconsistencies in date formats around the world.."),
        Message("Peter Alberg Schulz", 214980, "Time for lunch? Please reply soon.."),
        Message("Firstname Lastname", 1412, "Is this necessary?"),
        Message("Firstname Lastname", 1212, "Is this necessary?"),
        Message("Firstname Lastname", 1242, "Is this necessary?"),
        Message("Firstname Lastname", 1241, "Is this necessary?"),
        Message("Firstname Lastname", 12412, "Is this necessary?")
)

val applications = arrayListOf(
            ApplicationAbacus(ApplicationInfo("Particle Simulator", "1.0"),
                    arrayListOf(ApplicationField("input", "Input File","The input file for the application.", "input_file", null, false),
                            ApplicationField("speed", "MPI Threads", "The number of MPI threads to be used.", "integer", "4",true))),
            ApplicationAbacus(ApplicationInfo("Particle Simulation Video Generator", "5.0"),
                    arrayListOf(ApplicationField("input", "Input file", "The input file containing the results of a particle simulation.", "input_file", null, false),
                            ApplicationField("format", "File format", "The format which the file should be outputted as. Possible values: ogg (default)", "text", "ogg",true))))


/* Types: Complete, In Progress, Pending, Failed */
val notifications = arrayListOf(
        Notification("Job ABGO-104 completed", "Job ABGO-104 has completed.", 1413090181037, "Complete", "AOGB-1133"),
        Notification("Job AGOB-424 failed", "Job AGOB-424 has failed.", 1503090081037, "Failed", "AGOB-424"),
        Notification("Job BGOA-401 in progress", "Job BGOA-401 is in progress.", 1512090181037, "In Progress", "BGOA-401"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111"),
        Notification("Job ABGG-111 is complete", "Job ABGG-111 is complete.", 1413090181037, "Complete", "ABGG-111"),
        Notification("Job ABGG-111 is in progress", "Job ABGG-111 is in progress.", 1413090181037, "In Progress", "ABGG-111"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111"),
        Notification("Job ABGG-111 is complete", "Job ABGG-111 is complete.", 1413090181037, "Complete", "ABGG-111"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111"),
        Notification("Job ABGG-111 is pending", "Job ABGG-111 is pending execution.", 1413090181037, "Pending", "ABGG-111")
)

val workflows = arrayListOf(Workflow("Particle Simulation and Video Generation", applications))


val analyses = arrayListOf(
            Analysis("My analysis", "Completed", listOf(Comment("You", "That was fast."), Comment("You", "#"))),
            Analysis("Test analysis", "Pending", listOf(Comment("Person McPerson", "sudo start app"), Comment("You", "That doesn't work."))),
            Analysis("File conversion", "Failed"),
            Analysis("Group analysis", "Completed"),
            Analysis("Large analysis", "Pending"),
            Analysis("Moderate analysis", "Completed"),
            Analysis("Latest analysis", "Pending"),
            Analysis("Thesis analysis", "In Progress"),
            Analysis("Particle Simulation", "In Progress"),
            Analysis("Abacus benchmarking", "Completed")
)

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