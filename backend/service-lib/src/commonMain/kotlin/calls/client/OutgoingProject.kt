package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeKey

private val key = AttributeKey<String>("outgoing-project")

var OutgoingCall.project: String?
    get() = attributes.getOrNull(key)
    internal set(value) {
        if (value != null) attributes[key] = value
        else attributes.remove(key)
    }
