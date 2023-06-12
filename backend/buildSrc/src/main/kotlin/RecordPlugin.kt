import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class RecordPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.create("generateRecords") {
            doFirst {
                val dirs = target.subprojects
                    .filter { it.name.endsWith("-service") }
                    .map { it.projectDir }
                doGenerateRecords(dirs)
            }
        }
    }

    private fun doGenerateRecords(projectDirs: List<File>) {
        val parsedFiles = ArrayList<Parser.Ast>()

        for (project in projectDirs) {
            val directory = File(project, "src/main/messages")
            if (!directory.exists() || !directory.isDirectory) continue

            val msgFiles = directory.listFiles()?.filter { it.extension == "msg" } ?: emptyList()

            for (file in msgFiles) {
                val cursor = Cursor(file.absolutePath, file.readText())
                val element = Parser(cursor).parse()
                parsedFiles.add(element)
            }
        }

        val types = buildTypeTable(parsedFiles)

        for (ast in parsedFiles) {
            val code = generateKotlinCode(ast, types)

            val subPath = run {
                var fileId = ast.fileIdentifier
                for (dir in projectDirs) fileId = fileId.removePrefix(dir.absolutePath)

                fileId.removePrefix("/").removePrefix("src/main/messages")
            }

            val projectDir = ast.fileIdentifier.substringBefore("/src/main/messages/")

            val newSubPath = subPath.removeSuffix(".msg") + ".kt"
            val destinationFile = File(projectDir, "api/src/main/kotlin/$newSubPath")
            destinationFile.writeText(code)
        }
    }
}
