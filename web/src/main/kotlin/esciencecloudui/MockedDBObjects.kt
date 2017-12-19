package esciencecloudui

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

data class OptionNode(val name: String, var icon: String, var href: String? = null, var children: ArrayList<OptionNode>? = null)

object DashboardOptions {
    val nodes = arrayListOf(
            OptionNode("Dashboard", "nav-icon", "/dashboard"),
            OptionNode("Files", "nav-icon", "/files"),
            OptionNode("Projects", "", "/projects"),
            OptionNode("Apps", "nav-icon", children = arrayListOf(
                    (OptionNode("Applications", "", "/applications")),
                    (OptionNode("Workflows", "", "/workflows")),
                    (OptionNode("Analyses", "", "/analyses")))),
            OptionNode("Activity", "", children = arrayListOf(
                    // (OptionNode("Messages", "", "/activity/messages")),
                    (OptionNode("Notifications", "", "/activity/notifications")))))
}