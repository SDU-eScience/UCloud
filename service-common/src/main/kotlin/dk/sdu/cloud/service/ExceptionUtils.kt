package dk.sdu.cloud.service

import java.io.PrintWriter
import java.io.StringWriter

fun Throwable.stackTraceToString(): String = StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()