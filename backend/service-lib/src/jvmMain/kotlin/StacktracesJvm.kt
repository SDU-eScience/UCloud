package dk.sdu.cloud

actual fun Throwable.toReadableStacktrace(): ReadableStackTrace {
    val type = this::class.simpleName ?: "UnknownThrowable"
    val message = this.message ?: "No message given"
    val frames = ArrayList<String>()

    for (frame in stackTrace) {
        val readable = makeStackFrameReadable(frame.className, frame.methodName) ?: continue
        frames.add("${readable.className}#${readable.methodName}:${frame.lineNumber}")
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