package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.messages.BinaryAllocator

private val ucloudMsgRequestAllocatorKey = AttributeKey<BinaryAllocator>("ucloud-msg-request-allocator")
private val ucloudMsgResponseAllocatorKey = AttributeKey<BinaryAllocator>("ucloud-msg-response-allocator")

var IngoingCall.requestAllocatorOrNull: BinaryAllocator?
    get() = attributes.getOrNull(ucloudMsgRequestAllocatorKey)
    internal set(value) {
        if (value != null) attributes[ucloudMsgRequestAllocatorKey] = value
        else attributes.remove(ucloudMsgRequestAllocatorKey)
    }

var IngoingCall.responseAllocatorOrNull: BinaryAllocator?
    get() = attributes.getOrNull(ucloudMsgResponseAllocatorKey)
    internal set(value) {
        if (value != null) attributes[ucloudMsgResponseAllocatorKey] = value
        else attributes.remove(ucloudMsgResponseAllocatorKey)
    }

val IngoingCall.requestAllocator: BinaryAllocator
    get() = requestAllocatorOrNull ?: error("requestAllocator has not been initialized. Are you sure this request needs it?")

val IngoingCall.responseAllocator: BinaryAllocator
    get() = responseAllocatorOrNull ?: error("responseAllocator has not been initialized. Are you sure this request needs it?")

