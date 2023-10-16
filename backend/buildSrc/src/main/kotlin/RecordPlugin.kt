import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/*
This binary format is based on how FlatBuffers work. The short version is:

- Each message starts with a pointer. This points to the root record.

- Inside the root record, we store all of our data. The only properties which can be stored inline are primitive
  numeric types (and booleans)

- All other types are accessed through a pointer

- Strings are encoded as a count followed by the UTF-8 binary data

- Lists are encoded the same way as strings, currently all elements are stored through pointers but this should change.

- Dictionaries are implemented as lists of pairs (which is a record type)

- Optional types are always accessed through a pointer. A pointer of 0 is a null pointer (exactly as it works in C).

The following stuff should work:

- Encoding and decoding of all the primitive types, strings, records, lists and dictionaries

- Generation of Kotlin code which supports all types along with JSON encoding + decoding

- The HTTP stack (Kotlin client and Kotlin server) supports binary format

- Generation of TypeScript code which supports all types, except for lists and dictionaries. It does not yet support
  JSON.

- There is no client support for TypeScript, yet

- The avatar service has been updated to use these new types. By default, it will simply produce backwards compatible
  JSON. As a result, it could be used in production.

Things to improve:

- Lists and dictionaries (since they are just lists) shouldn't use indirection in their elements. This is currently adding
  quite a lot of overhead since these all add 4 bytes on top of the actual elements. Instead, we should just store the elements
  inline. This shouldn't be problematic since we know the element type ahead of time.

- We should create a tool which automatically detects when a change is breaking. This should produce a warning or an error
  depending on the stability of the type. The stability of the type should be implemented using an annotation.

- The code for generation could definitely be simplified quite a bit. This is the first version, and you can definitely tell.
  If we want to extend this code, then we should consider simplifying it before making too many big changes.
 */
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
            println(generateTypeScriptCode(ast, types))
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
