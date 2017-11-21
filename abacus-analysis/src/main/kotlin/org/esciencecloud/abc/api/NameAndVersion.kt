package org.esciencecloud.abc.api

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}