package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import java.util.concurrent.atomic.AtomicInteger

actual typealias AtomicInteger = AtomicInteger
actual fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)