package sduclouddb.utils

import org.slf4j.Logger

class Utilities {

    inline fun catchAll(LOG: Logger, message: String, action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            LOG.warn("Failed to $message. ${t.message}", t)
        }
    }
}