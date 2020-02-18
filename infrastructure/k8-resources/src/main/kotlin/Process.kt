package dk.sdu.cloud.k8

data class ProcessOutput(val stdout: String, val stderr: String, val exitCode: Int)

object Process {
    fun runAndPrint(vararg command: String) {
        val (stdout, stderr, statusCode) = runAndCollect(*command)
        println(stdout)
        System.err.println(stderr)
        if (statusCode != 0) throw IllegalStateException("Process ${command[0]} status code: $statusCode")
    }

    fun runAndCollect(vararg command: String): ProcessOutput {
        val process = ProcessBuilder().command(*command).start()
        lateinit var stdoutText: String
        lateinit var stderrText: String

        val stdout = Thread {
            stdoutText = process.inputStream.bufferedReader().readText()
        }

        val stderr = Thread {
            stderrText = process.errorStream.bufferedReader().readText()
        }

        stdout.start()
        stderr.start()

        val status = process.waitFor()
        stdout.join()
        stderr.join()
        return ProcessOutput(stdoutText, stderrText, status)
    }

    fun runAndDiscard(vararg command: String): Int {
        val process = ProcessBuilder().command(*command).start()
        val stdout = Thread {
            val ins = process.inputStream
            val discardBuffer = ByteArray(1024)
            while (ins.read(discardBuffer) >= 0) {
                // Do nothing
            }
        }

        val stderr = Thread {
            val ins = process.errorStream
            val discardBuffer = ByteArray(1024)
            while (ins.read(discardBuffer) >= 0) {
                // Do nothing
            }
        }

        stdout.start()
        stderr.start()

        val status = process.waitFor()
        stdout.join()
        stderr.join()
        return status
    }
}
