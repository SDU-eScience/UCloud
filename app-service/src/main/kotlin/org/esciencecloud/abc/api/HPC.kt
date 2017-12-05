package org.esciencecloud.abc.api

import kotlinx.coroutines.experimental.runBlocking
import org.esciencecloud.client.AuthenticatedCloud
import org.esciencecloud.client.EScienceCloud
import org.esciencecloud.client.basicAuth
import org.esciencecloud.client.callDescription

object HPCApplications {
    val baseContext = "/hpc/apps/"

    data class FindAllByName(val name: String) {
        companion object {
            val description = callDescription<FindAllByName, List<ApplicationDescription>, List<ApplicationDescription>> {
                path {
                    using(baseContext)
                    +boundTo(FindAllByName::name)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }

    data class FindByNameAndVersion(val name: String, val version: String) {
        companion object {
            val description = callDescription<FindByNameAndVersion, ApplicationDescription, String> {
                path {
                    using(baseContext)
                    +boundTo(FindByNameAndVersion::name)
                    +boundTo(FindByNameAndVersion::version)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }

    object ListAll {
        // TODO Pagination will be required for this
        val description = callDescription<Unit, List<ApplicationDescription>, List<ApplicationDescription>> {
            path { using(baseContext) }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(Unit).call(cloud)
    }

    enum class TestEnum {
        A,
        B,
        C,
        D
    }

    data class Test(val name: String, val notRequired: Int?, val enum: TestEnum?) {
        companion object {
            val description = callDescription<Test, Test, Map<String, Any?>> {
                path {
                    using(baseContext)
                    +"test"
                    +boundTo(Test::name)
                    +boundTo(Test::notRequired)
                    +boundTo(Test::enum)
                }
            }
        }

        suspend fun call(cloud: AuthenticatedCloud) = description.prepare(this).call(cloud)
    }
}

object HPCJobs {

}

fun main(args: Array<String>) {
    val cloud = EScienceCloud("http://localhost:8080/")
    val authenticated = cloud.basicAuth("rods", " rods")
    /*
    runBlocking {
        val all = HPCApplications.FindByNameAndVersion("hello", "1.0.0").call(authenticated)
        when (all) {
            is RESTResponse.Ok -> {
                println("Yay!")
                println(all.result)
            }

            is RESTResponse.Err -> {
                println(":-(")
                println(all)
            }
        }
    }

    runBlocking {
        println(HPCApplications.ListAll.call(authenticated))
    }
    */

    runBlocking {
        repeat(10) {
            println(HPCApplications.Test("test", 42, HPCApplications.TestEnum.B).call(authenticated))
        }
    }
}
