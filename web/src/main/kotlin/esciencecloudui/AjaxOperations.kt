package esciencecloudui

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.get
import io.ktor.locations.location
import io.ktor.locations.post
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.post
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth
import org.esciencecloud.asynchttp.asJson


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

@location("/getApplicationInfo")
data class GetApplicationInfo(val name: String, val version: String)

@location("/createDir")
data class CreateDirectory(val dirPath: String)

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
        call.respond(MockAnalyses.analyses.subList(0, 10))
    }

    get<WorkFlows> {
        // TODO Get actual workflows
        call.respond(WorkflowObject.workflows)
    }

    get<GetApplications> {
        // TODO Get actual applications
        call.respond(ApplicationsAbacus.applications)
    }

    get<Favourites> {
        // TODO get actual favourite files
        val wrongFavouriteFiles = call.getFavouriteFiles()
        call.respond(wrongFavouriteFiles)
    }

    get<GetApplicationInfo> {
        val app = getApp(it.name, it.version)!!
        call.respond(app)
    }

    get<Analyses> {
        call.respond(MockAnalyses.analyses)
    }

    post<StartJob> {
        val application = call.receiveParameters()["application"]
        call.respond(200)
    }

    get<CreateDirectory> {
        call.respond(200)
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


/* Possible types: integer, text, float, input_file, output_file */
data class ApplicationField(val name: String, val prettyName: String, val description: String, val type: String, val defaultValue: String?, val isOptional: Boolean)

data class ApplicationAbacus(val info: ApplicationInfo, val parameters: List<ApplicationField>)
data class ApplicationInfo(val name: String, val version: String, val rating: Double = 5.0, val isPrivate: Boolean = false, val description: String = "An app to be run on Abacus", val author: String = "Anyone")
data class Workflow(val name: String, val applications: ArrayList<ApplicationAbacus>)
data class Analysis(val name: String, val status: String)

object WorkflowObject {
    val workflows = arrayListOf(Workflow("Particle Simulation and Video Generation", ApplicationsAbacus.applications))
}

object ApplicationsAbacus {
    val applications = arrayListOf(
            ApplicationAbacus(ApplicationInfo("Particle Simulator", "1.0"),
                    arrayListOf(ApplicationField("input", "Input File","The input file for the application.", "input_file", null, false),
                            ApplicationField("speed", "MPI Threads", "The number of MPI threads to be used.", "integer", "4",true))),
            ApplicationAbacus(ApplicationInfo("Particle Simulation Video Generator", "5.0"),
                    arrayListOf(ApplicationField("input", "Input file", "The input file containing the results of a particle simulation.", "input_file", null, false),
                            ApplicationField("format", "File format", "The format which the file should be outputted as. Possible values: ogg (default)", "text", "ogg",true))))
}

object MockAnalyses {
    val analyses = arrayListOf(
            Analysis("My analysis", "Completed"),
            Analysis("Test analysis", "Pending"),
            Analysis("File conversion", "Failed"),
            Analysis("Group analysis", "Completed"),
            Analysis("Large analysis", "Pending"),
            Analysis("Moderate analysis", "Completed"),
            Analysis("Latest analysis", "Pending"),
            Analysis("Thesis analysis", "In Progress"),
            Analysis("Particle Simulation", "In Progress"),
            Analysis("Abacus benchmarking", "Completed")
    )
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