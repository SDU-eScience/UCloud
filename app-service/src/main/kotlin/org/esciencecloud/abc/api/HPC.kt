package org.esciencecloud.abc.api

object HPC {
    private val endpoint = "/hpc"
    object Applications {
        private
        fun findByNameAndVersion(name: String, version: String): PreparedRESTCall<ApplicationDescription?, Any> =
                preparedCallWithJsonOutput("/hpc/apps/$name/$version")

        fun findAllByName(name: String, version: String): PreparedRESTCall<List<ApplicationDescription>, Any> =
                preparedCallWithJsonOutput("/hpc/apps/")

        fun list(): PreparedRESTCall<List<ApplicationDescription>, Any> =
                TODO()
    }

    object Jobs {

    }
}