package org.esciencecloud.storage.ext.irods.tools

import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.model.*
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DataTest(
        private val storageConnectionFactory: StorageConnectionFactory,
        adminAccount: Pair<String, String>,
        private val userAccounts: Map<String, String>
) {
    private val adminAccount = storageConnectionFactory.createForAccount(adminAccount.first, adminAccount.second)
    private val executor = Executors.newScheduledThreadPool(16)
    private val random = Random()

    fun startTest() {
        userAccounts.forEach { user, password ->
            executor.scheduleAtFixedRate(
                    scheduledTaskForUser(user, password),
                    random.nextInt(1).toLong(),
                    5L,
                    TimeUnit.SECONDS
            )
        }

        executor.scheduleAtFixedRate(scheduledAdminTask(), 0, 4, TimeUnit.MINUTES)
    }

    @Suppress("FunctionName")
    class ScheduledTasksForUser(
            private val storageConnectionFactory: StorageConnectionFactory,
            private val username: String,
            private val password: String
    ) : Runnable {
        private val random = Random()
        private val tasks = arrayListOf<() -> Unit>()
        private var _conn: StorageConnection? = null
        private val lock = Any()
        private val conn: StorageConnection
            get() = _conn!!

        init {
            tasks.add { `create a bunch of files`() }
            tasks.add { `list some files`() }
            tasks.add { `create some directories and delete them all`()}
            tasks.add { `create some files and change acl`() }
            tasks.add { `upload and download some stuff`() }
            tasks.add { `delete all in home`() }
            tasks.add { `upload and download some stuff`() }
            tasks.add { `create some files and change acl`() }
        }

        override fun run() {
            synchronized(lock) {
                _conn = storageConnectionFactory.createForAccount(username, password)
                try {
                    tasks[random.nextInt(tasks.size)]()
                } catch (ex: Exception) {
                    log("Exception when running task. See error log")
                    System.err.println(Date().toInstant())
                    ex.printStackTrace()
                }
                conn.close()
                _conn = null
            }
        }

        private fun `create a bunch of files`() {
            val filesToCreate = random.nextInt(30) + 1
            val filesCreated = arrayListOf<StoragePath>()
            val sizes = arrayListOf<Int>()

            log("Creating $filesToCreate files")

            (1..filesToCreate).forEach {
                val name = conn.paths.homeDirectory.push(random.nextString(20))
                val amountOfData = random.nextInt(1024 * 10)
                val data = ByteArray(amountOfData) { CHARS[random.nextInt(CHARS.size)].toByte() }
                conn.files.put(name, ByteArrayInputStream(data))
                filesCreated.add(name)
                sizes.add(amountOfData)
            }

            val stats = conn.fileQuery.statBulk(*filesCreated.toTypedArray()).orThrow()
            stats.forEachIndexed { index, fileStat ->
                assertNotEquals(null, fileStat, "File stat")
                val stat = fileStat ?: return@forEachIndexed
                assertEquals(sizes[index].toLong(), stat.sizeInBytes, "File size")
                if (stat.path.name != filesCreated[index].name) {
                    System.err.println("Expected stat path to match (${stat.path}) and ${filesCreated[index]}")
                }
            }
            log("Done")
        }

        private fun `list some files`() {
            log("Listing at home")
            val filesAtHome = conn.fileQuery.listAt(conn.paths.homeDirectory).orThrow()
            log("Done. Found ${filesAtHome.size} files at home")
        }

        private fun `create some directories and delete them all`() {
            val filesPerLevel = random.nextInt(3) + 1
            val depth = random.nextInt(10)
            val root = conn.paths.homeDirectory.push(random.nextString(10))
            if (conn.fileQuery.exists(root).orThrow()) return

            log("Creating files at $root with depth=$depth and fpl=$filesPerLevel")

            val dummyFile = createDummyFile()

            conn.files.createDirectory(root)
            var currentPath = root
            (0 until depth).forEach { level ->
                (0 until filesPerLevel).forEach { fileIndex ->
                    conn.files.put(currentPath.push("file-$fileIndex"), dummyFile)
                }

                currentPath = currentPath.push("/directory-$level")
                conn.files.createDirectory(currentPath)
            }

            // Time to validate
            currentPath = root
            (0 until depth).forEach { level ->
                val allFiles = conn.fileQuery.listAt(currentPath).orThrow()
                val normalFiles = allFiles.filter { it.path.name.startsWith("file-") && it.type == FileType.FILE }
                val directories = allFiles.filter { it.path.name.startsWith("directory-$level") &&
                        it.type == FileType.DIRECTORY }

                if (normalFiles.size != filesPerLevel) {
                    throw IllegalStateException("With fpl $filesPerLevel and depth $depth. " +
                            "Unexpected state at $currentPath: " +
                            normalFiles.joinToString("\n") { it.path.path })
                }

                if (directories.size != 1) {
                    throw IllegalStateException("With fpl $filesPerLevel and depth $depth. " +
                            "Unexpected state at $currentPath: " +
                            directories.joinToString("\n") { it.path.path })
                }

                currentPath = currentPath.push("/directory-$level")
            }

            conn.files.delete(root, true)
            if (conn.fileQuery.exists(root).orThrow()) {
                throw IllegalStateException("Did not manage to delete directory, but no exception")
            }
            log("Done")
        }

        private fun `create some files and change acl`() {
            val dummyFile = createDummyFile()
            val path = conn.paths.homeDirectory.push(random.nextString(20))
            conn.files.put(path, dummyFile)
            conn.accessControl.updateACL(path, listOf(AccessEntry(User("rods"), AccessRight.READ_WRITE)))
            val newAcl = conn.accessControl.listAt(path).orThrow()
            assertEquals(2, newAcl.size, "ACL size")
            val aclEntry = newAcl.find { it.entity.name.startsWith("rods") } ?:
                    throw IllegalStateException("could not find rods acl entry")
            assertEquals(AccessRight.READ_WRITE, aclEntry.right, "ACL right")
            conn.files.delete(path)
            log("Done")
        }

        private fun `upload and download some stuff`() {
            val numberOfFiles = random.nextInt(10) + 10
            log("Uploading $numberOfFiles files and downloading")
            (0 until numberOfFiles).forEach {
                val file = createDummyFile()
                val outputFile = Files.createTempFile("dummy", "file").toFile()
                val checksum = file.checksum()
                val targetPath = conn.paths.homeDirectory.push(random.nextString(20))

                conn.files.put(targetPath, file)
                conn.files.get(targetPath, outputFile)
                val outputChecksum = outputFile.checksum()
                if (!Arrays.equals(checksum, outputChecksum)) {
                    throw IllegalStateException("Checksum mismatch!")
                }
                conn.files.delete(targetPath)
            }
            log("Done")
        }

        private fun `delete all in home`() {
            log("Deleting everything we have in the home directory")
            val files = conn.fileQuery.listAt(conn.paths.homeDirectory).orThrow()
            files.forEach {
                conn.files.delete(it.path, true)
            }
            val filesAfterDeletion = conn.fileQuery.listAt(conn.paths.homeDirectory).orThrow()
            if (filesAfterDeletion.isNotEmpty()) {
                throw IllegalStateException("Did not manage to delete all files, but no exception were thrown. " +
                        "$filesAfterDeletion")
            }
            log("Done")
        }

        private fun createDummyFile(): File {
            val dummyFile = Files.createTempFile("dummy", "file").toFile()
            dummyFile.outputStream().bufferedWriter().use { out ->
                val bytes = random.nextInt(1024)
                out.write("This is a dummy file, with an additional $bytes bytes of data")
                out.newLine()
                out.write(CharArray(bytes) { CHARS[random.nextInt(CHARS.size)] })
            }
            return dummyFile
        }

        private fun <T> assertEquals(expected: T?, actual: T?, what: String) {
            if (expected?.equals(actual) != true) {
                throw IllegalStateException("When running test for $username: $what. " +
                        "Expected value to be $expected but actual value was $actual")
            }
        }

        private fun <T> assertNotEquals(notExpected: T?, actual: T?, what: String) {
            if (notExpected?.equals(actual) == true) {
                throw IllegalStateException("When running test for $username: $what. " +
                        "Expected value to not be $notExpected but actual value was $actual")
            }
        }

        private fun log(what: String) {
            val methodName = Thread.currentThread().stackTrace[3].methodName
            val simpleMethodName = methodName.split("$").last()
            println("${Date().toInstant()}[$username/$simpleMethodName]: $what")
        }

        private fun File.checksum(algorithm: String = "md5"): ByteArray {
            val dg = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(1024 * 8)

            inputStream().use { ins ->
                while (true) {
                    val read = ins.read(buffer)
                    if (read <= 0) break
                    dg.update(buffer, 0, read)
                }
            }
            return dg.digest()
        }
    }

    private fun scheduledTaskForUser(username: String, password: String): Runnable {
        // Runs every 4 minutes for every user. Time to do some shenanigans!
        return ScheduledTasksForUser(storageConnectionFactory, username, password)
    }

    private fun scheduledAdminTask(): () -> Unit = {

    }
}

fun main(args: Array<String>) {
    val connectionInfo = IRodsConnectionInformation(
            host = "localhost",
            port = 1247,
            zone = "tempZone",
            storageResource = "radosRandomResc",
            authScheme = AuthScheme.STANDARD,
            sslNegotiationPolicy = CS_NEG_REFUSE
    )

    val factory = IRodsStorageConnectionFactory(connectionInfo)
    DataTest(factory, "irodsadmin" to "irodsadmin", mapOf(
            "irodsadmin" to "irodsadmin"
    )).startTest()
}

private fun Random.nextString(size: Int): String {
    return StringBuilder().apply { (1..size).forEach { append(CHARS[nextInt(CHARS.size)]) } }.toString()
}

private val CHARS = "abcdefghijklmnopqrstuvwxyz".toCharArray()
