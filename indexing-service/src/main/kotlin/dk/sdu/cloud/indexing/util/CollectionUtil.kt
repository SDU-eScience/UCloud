package dk.sdu.cloud.indexing.util

fun Collection<*>?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()
