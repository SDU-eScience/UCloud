package dk.sdu.cloud

actual fun Throwable.toReadableStacktrace(): ReadableStackTrace {
    val type = this::class.simpleName ?: "UnknownThrowable"
    val message = this.message ?: "No message given"
    val frames = ArrayList<String>()

    for (frame in stackTraceToString().lineSequence()) {
        if (!frame.contains("kfun:")) continue
        val interestingPart = frame.substringAfter("kfun:").substringBeforeLast('+').trim()
        val className = interestingPart.substringBefore('#')
        val methodName = interestingPart.substringAfter('#')
        val readable = makeStackFrameReadable(className, methodName) ?: continue
        frames.add("${readable.className}#${readable.methodName}")
        println(readable)
    }

    val deduplicatedFrames = ArrayList<String>(frames.size)

    run {
        var prevLine: String? = null
        var dupeCounter = 0
        for (frame in frames) {
            if (frame == prevLine) {
                dupeCounter++
            } else {
                if (prevLine != null) {
                    if (dupeCounter == 1) deduplicatedFrames.add(prevLine)
                    else deduplicatedFrames.add("$prevLine ($dupeCounter duplicates)")
                }
                dupeCounter = 1
            }

            prevLine = frame
        }
        if (prevLine != null) {
            if (dupeCounter == 1) deduplicatedFrames.add(prevLine)
            else deduplicatedFrames.add("$prevLine ($dupeCounter duplicates)")
        }
    }

    return ReadableStackTrace(type, message, deduplicatedFrames)
}