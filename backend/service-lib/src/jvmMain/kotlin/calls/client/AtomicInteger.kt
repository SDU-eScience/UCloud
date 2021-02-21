package dk.sdu.cloud.calls.client

import java.util.concurrent.atomic.AtomicInteger

actual typealias AtomicInteger = AtomicInteger
actual fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)
