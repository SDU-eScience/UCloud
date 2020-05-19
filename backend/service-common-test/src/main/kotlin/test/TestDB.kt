import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*

class TestDB {

    private fun getResourceFolderFiles(folder: String): Array<File?> {
        val loader = Thread.currentThread().contextClassLoader
        val url = loader.getResource(folder)
        val path: String = url?.path ?: "notta"
        return File(path).listFiles() ?: throw IllegalArgumentException("No files found")
    }

    private fun runMigrate(path: String, db: EmbeddedPostgres) {
        val connection = db.postgresDatabase.connection
        val files = getResourceFolderFiles(path)
        files.sort()
        files.forEach { file ->
            if (file != null) {
                val sc = Scanner(file)
                var query = ""
                while (sc.hasNextLine()) {
                    query += sc.nextLine()
                }
                connection.createStatement().executeUpdate(query)
            }
        }
    }

    fun getTestDB(resourcePath: String): EmbeddedPostgres {
        val db = EmbeddedPostgres.start()
        runMigrate(resourcePath, db)
        return db
    }
}
