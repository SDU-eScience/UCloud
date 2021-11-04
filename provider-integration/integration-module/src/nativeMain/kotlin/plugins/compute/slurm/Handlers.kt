
package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*

import dk.sdu.cloud.app.store.api.AppParameterValue.Text
import dk.sdu.cloud.accounting.api.ProductReference

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.*
import kotlinx.coroutines.runBlocking
import platform.posix.*
import kotlinx.cinterop.*
import dk.sdu.cloud.utils.*

import dk.sdu.cloud.sql.*
import sqlite3.*

import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.ipc.*


import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

import io.ktor.http.*
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper

import platform.posix.mkdir

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

import dk.sdu.cloud.app.store.api.SimpleDuration




class Handlers {

    companion object {
        val log = Log("SlurmComputePlugin")
        val ipc = listOf<IpcHandler>(

            IpcHandler("slurm.jobs.create") { user, jsonRequest ->
                log.debug("Asked to add new job mapping!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                //println(req)

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            insert into job_mapping (local_id, ucloud_id, partition, status) values ( :local_id, :ucloud_id, :partition, :status )
                                        """
                        ).useAndInvokeAndDiscard {
                                            bindString("ucloud_id", req.ucloudId)
                                            bindString("local_id", req.slurmId )
                                            bindString("partition", req.partition )
                                            bindInt("status", req.status )
                        }
                }

                JsonObject(emptyMap())
            },


            IpcHandler("slurm.jobs.retrieve") { user, jsonRequest ->
                log.debug("Asked to get job!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                var slurmJob:SlurmJob? = null

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            select * 
                                            from job_mapping 
                                            where ucloud_id = :ucloud_id
                                        """
                        ).useAndInvoke (
                            prepare = { bindString("ucloud_id", req.ucloudId) },
                            readRow = { slurmJob = SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!!, it.getInt(3)!! ) }
                        )
                }

                //println(" DATABASE RESULT $slurmJob")

                defaultMapper.encodeToJsonElement(slurmJob) as JsonObject 
            },


        // Jobs Browse sample call 
        // TODO: improve for cases involving next and itemstoskip
        // val result = ipcClient.sendRequestBlocking( 
        //         JsonRpcRequest( "slurm.jobs.browse",   JobsBrowseRequest( listOf( Criteria("status", "=1") ) ).toJson()  )       
        //     ).orThrow<JsonObject>()

        // val mJobs = result.get("jobs") as List<JsonObject>
        // mJobs.forEach{ item ->
        //     val job = defaultMapper.decodeFromJsonElement<SlurmJob>(item) ;
        //     println(job)
        // }
            

            IpcHandler("slurm.jobs.browse") { user, jsonRequest ->
                log.debug("Asked to browse jobs!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<JobsBrowseRequest>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                println(req)
                //JobsBrowseRequest(filters=[Criteria(field=status, condition==1)], itemsPerPage=null, next=null, consistency=REQUIRE, itemsToSkip=null)

                var jobs:MutableList<SlurmJob> = mutableListOf()
                val filters = req.filters.fold("where "){ acc, e -> acc.plus(e.field).plus(e.condition).plus(" and ")}.dropLast(5)
                var limit = ""

                if(req.itemsPerPage != null) limit = " limit " + req.itemsPerPage.toString()

                val conditions = filters.plus(limit)
                

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->
                            connection.prepareStatement(
                                                        """
                                                            select * 
                                                            from job_mapping 
                                                            :conditions
                                                        """.replace(":conditions", conditions )
                                        ).useAndInvoke(
                                            //prepare = {  bindString("conditions", "status=1" )   },
                                            readRow = {  jobs.add(SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!!, it.getInt(3)!! )  ) }
                                        )
                }

                println(" DATABASE RESULT $jobs")
                

                buildJsonObject{ 
                    putJsonArray("jobs"){
                            for (j in jobs) add(  defaultMapper.encodeToJsonElement( j )  )
                    }
                }

            }


        )






    }
}



