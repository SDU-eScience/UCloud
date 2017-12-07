package org.esciencecloud.transactions.util

import java.io.PrintWriter
import java.io.StringWriter

internal fun Exception.stackTraceToString(): String = StringWriter().apply {
    printStackTrace(PrintWriter(this))
}.toString()
