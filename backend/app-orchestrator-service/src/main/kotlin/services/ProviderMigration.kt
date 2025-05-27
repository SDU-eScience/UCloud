package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.AccountingV2
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.app.orchestrator.AppOrchestratorServices.serviceClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

class ProviderMigration(
    private val db: DBContext,
) {
    fun dumpProviderData(providerId: String, dryRun: Boolean): ReceiveChannel<String> {
        val channel = Channel<String>()

        BackgroundScope.get().launch {
            db.withSession { session ->
                try {
                    val outputDir = File("/tmp/provider-$providerId").also { it.mkdirs() }
                    channel.send("Ready to begin data migration: providerId=$providerId dryRun=$dryRun")

                    // Change accounting units
                    // ---------------------------------------------------------------------------------------------------------

                    if (!dryRun) {
                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                with d as (
                                    insert into accounting.accounting_units (name, name_plural, floating_point, display_frequency_suffix)
                                    values ('link', 'links', false, false)
                                    on conflict (name, name_plural, floating_point, display_frequency_suffix) do update set name = excluded.name
                                    returning id as new_unit
                                )
                                update accounting.product_categories
                                set
                                    accounting_unit = new_unit,
                                    accounting_frequency = 'ONCE'
                                from d
                                where
                                    provider = :provider
                                    and product_type = 'INGRESS'
                                ;                                
                            """
                        )

                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                with d as (
                                    insert into accounting.accounting_units (name, name_plural, floating_point, display_frequency_suffix)
                                    values ('Core', 'Core', false, true)
                                    on conflict (name, name_plural, floating_point, display_frequency_suffix) do update set name = excluded.name
                                    returning id as new_unit
                                )
                                update accounting.product_categories
                                set
                                    accounting_unit = new_unit,
                                    accounting_frequency = 'PERIODIC_HOUR'
                                from d
                                where
                                    provider = :provider
                                    and category = 'syncthing'
                                ;                               
                            """
                        )
                    }

                    // Resets accounting scopes in storage
                    // ---------------------------------------------------------------------------------------------------------

                    channel.send("Reset accounting scopes 1/4")
                    session.sendPreparedStatement(
                        {},
                        "create temp table scopes_to_delete(key text primary key)"
                    )
                    channel.send("Reset accounting scopes 2/4")

                    session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            insert into scopes_to_delete(key)
                            select u.key
                            from
                                file_orchestrator.file_collections fc
                                join provider.resource r on fc.resource = r.id
                                join accounting.products p on r.product = p.id
                                join accounting.product_categories pc on p.category = pc.id
                                join accounting.scoped_usage u on u.key ilike e'%\n' || fc.resource
                            where
                                pc.provider = :provider
                        """
                    )
                    channel.send("Reset accounting scopes 3/4")

                    if (!dryRun) {
                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                delete from accounting.scoped_usage u
                                using scopes_to_delete s
                                where s.key = u.key;
                            """
                        )
                        channel.send("Reset accounting scopes 3a")
                    }

                    val scopesToDelete = session.sendPreparedStatement(
                        {},
                        "select key from scopes_to_delete"
                    ).rows
                    channel.send("Reset accounting scopes 4/4")

                    run {
                        val outputFile = File(outputDir, "scopes_deleted.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in scopesToDelete) {
                            val scope = drive.getString(0)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    scope,
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }
                    session.sendPreparedStatement({}, "drop table scopes_to_delete")
                    channel.send("Reset accounting scopes complete")

                    // Resets accounting data in storage
                    // ---------------------------------------------------------------------------------------------------------

                    if (!dryRun) {
                        channel.send("Reset storage accounting 1/2")
                        val storageCategories = session.sendPreparedStatement(
                            { setParameter("provider", providerId) },
                            """
                                select pc.category
                                from accounting.product_categories pc
                                where pc.product_type = 'STORAGE' and pc.provider = :provider
                            """
                        ).rows
                        channel.send("Reset storage accounting 2/2")

                        for (cat in storageCategories) {
                            channel.send("Reset storage accounting 3: ${cat.getString(0)!!}")
                            AccountingV2.adminReset.call(
                                AccountingV2.AdminReset.Request(ProductCategoryIdV2(cat.getString(0)!!, providerId)),
                                serviceClient,
                            ).orThrow()
                        }
                        channel.send("Reset storage accounting complete")
                    }

                    // Dumps tracked allocations
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Allocation dump 1/3")
                    val productCategories = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            select pc.category
                            from accounting.product_categories pc
                            where pc.provider = :provider and not pc.free_to_use                    
                        """
                    ).rows
                    channel.send("Allocation dump 2/3")

                    for (category in productCategories) {
                        channel.send("Allocation dump 3/3: ${category.getString(0)!!}")
                        val cat = ProductCategoryIdV2(category.getString(0)!!, providerId)
                        val data = AccountingV2.adminProviderDump.call(
                            AccountingV2.AdminProviderDump.Request(cat),
                            serviceClient,
                        ).orThrow().dump

                        val writer = PrintWriter(File(outputDir, "allocations-${cat.name}.csv"))
                        writer.println(data)
                        writer.close()
                    }
                    channel.send("Allocation dump complete")

                    // Set all jobs to be done and unbind all job resources
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Reset all jobs 1/4")
                    session.sendPreparedStatement({}, "create temp table temp_jobs_removed(job_id bigint primary key);")
                    channel.send("Reset all jobs 2/4")
                    session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            insert into temp_jobs_removed(job_id)
                            select j.resource
                            from
                                app_orchestrator.jobs j
                                join provider.resource r on j.resource = r.id
                                join accounting.products p on r.product = p.id
                                join accounting.product_categories pc on p.category = pc.id
                            where
                                pc.provider = :provider
                                and (
                                    j.current_state = 'RUNNING'
                                    or j.current_state = 'IN_QUEUE'
                                )
                            ;
                        """
                    )
                    channel.send("Reset all jobs 3/4")

                    if (!dryRun) {
                        session.sendPreparedStatement(
                            {},
                            """
                                update app_orchestrator.jobs j
                                set current_state = 'FAILURE'
                                from
                                    temp_jobs_removed r
                                where
                                    j.resource = r.job_id
                                ;  
                            """
                        )
                        channel.send("Reset all jobs 3a/4")

                        session.sendPreparedStatement(
                            {},
                            """
                                insert into provider.resource_update(resource, created_at, status, extra)
                                select job_id, now(), 'The job has been terminated due to scheduled maintenance.', '{}'::jsonb
                                from temp_jobs_removed;                    
                            """
                        )
                        channel.send("Reset all jobs 3b/4")

                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                update app_orchestrator.licenses l
                                set status_bound_to = '{}'
                                from
                                    provider.resource r
                                    join accounting.products p on r.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                where
                                    r.id = l.resource
                                    and pc.provider = :provider
                                ;                    
                            """
                        )
                        channel.send("Reset all jobs 3c/4")

                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                update app_orchestrator.network_ips l
                                set status_bound_to = '{}'
                                from
                                    provider.resource r
                                    join accounting.products p on r.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                where
                                    r.id = l.resource
                                    and pc.provider = :provider
                                ;                    
                            """
                        )
                        channel.send("Reset all jobs 3d/4")

                        session.sendPreparedStatement(
                            {
                                setParameter("provider", providerId)
                            },
                            """
                                update app_orchestrator.ingresses l
                                set status_bound_to = '{}'
                                from
                                    provider.resource r
                                    join accounting.products p on r.product = p.id
                                    join accounting.product_categories pc on p.category = pc.id
                                where
                                    r.id = l.resource
                                    and pc.provider = :provider
                                ;                    
                            """
                        )
                    }

                    channel.send("Reset all jobs 4/4")
                    val jobsKilled = session.sendPreparedStatement(
                        {},
                        """
                            select job_id from temp_jobs_removed
                        """
                    ).rows

                    run {
                        val outputFile = File(outputDir, "jobs_killed.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in jobsKilled) {
                            val jobId = drive.getLong(0)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    jobId.toString(),
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }
                    session.sendPreparedStatement({}, "drop table temp_jobs_removed")
                    channel.send("Reset all jobs complete")

                    // Dumps tracked drives
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Dump drives 1/2")
                    val trackedDrives = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            with
                                resource as (
                                    select
                                        r,
                                        fc,
                                        p,
                                        pc,
                                        provider.accessible_resources(
                                            '#P_' || :provider,
                                            'file_collection',
                                            '{PROVIDER}',
                                            r.id,
                                            '',
                                            true,
                                            true,
                                            false
                                        ) ar
                                    from
                                        accounting.product_categories pc
                                        join accounting.products p on pc.id = p.category
                                        join provider.resource r on p.id = r.product
                                        join file_orchestrator.file_collections fc on r.id = fc.resource
                                    where
                                        r.type = 'file_collection'
                                        and pc.provider = :provider
                                )
                            select
                                (r).id as drive_id,
                                (p).name as product_id,
                                (pc).category as product_category,
                                (r).created_by as created_by,
                                (r).project as project_id,
                                (r).provider_generated_id as provider_generated_id,
                                provider.resource_to_json(ar, file_orchestrator.file_collection_to_json(fc)) as resource
                            from
                                resource
                            ;
                        """
                    ).rows

                    channel.send("Dump drives 2/2")
                    run {
                        val drivesFile = File(outputDir, "tracked_drives.jsonl")
                        val drivesWriter = PrintWriter(drivesFile)
                        for (drive in trackedDrives) {
                            val driveId = drive.getLong(0)!!
                            val productName = drive.getString(1)!!
                            val productCategory = drive.getString(2)!!
                            val createdBy = drive.getString(3)!!
                            val projectId = drive.getString(4)
                            val providerGeneratedId = drive.getString(5)
                            val resourceJson = drive.getString(6)!!


                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    driveId.toString(),
                                    productName,
                                    productCategory,
                                    createdBy,
                                    projectId,
                                    providerGeneratedId,
                                    resourceJson
                                )
                            )
                            drivesWriter.println(string)
                        }
                        drivesWriter.close()
                    }
                    channel.send("Dump drives complete")

                    // Dumps tracked ingresses
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Dump ingresses 1/2")
                    val trackedIngresses = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            with
                                resource as (
                                    select
                                        r,
                                        fc,
                                        p,
                                        pc,
                                        provider.accessible_resources(
                                            '#P_' || :provider,
                                            'ingress',
                                            '{PROVIDER}',
                                            r.id,
                                            '',
                                            true,
                                            true,
                                            false
                                        ) ar
                                    from
                                        accounting.product_categories pc
                                        join accounting.products p on pc.id = p.category
                                        join provider.resource r on p.id = r.product
                                        join app_orchestrator.ingresses fc on r.id = fc.resource
                                    where
                                        pc.provider = :provider
                                )
                            select
                                (r).id as drive_id,
                                (p).name as product_id,
                                (pc).category as product_category,
                                (r).created_by as created_by,
                                (r).project as project_id,
                                (r).provider_generated_id as provider_generated_id,
                                provider.resource_to_json(ar, app_orchestrator.ingress_to_json(fc)) as resource
                            from
                                resource
                            ;
                        """
                    ).rows
                    channel.send("Dump ingresses 2/2")

                    run {
                        val outputFile = File(outputDir, "tracked_ingress.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in trackedIngresses) {
                            val driveId = drive.getLong(0)!!
                            val productName = drive.getString(1)!!
                            val productCategory = drive.getString(2)!!
                            val createdBy = drive.getString(3)!!
                            val projectId = drive.getString(4)
                            val providerGeneratedId = drive.getString(5)
                            val resourceJson = drive.getString(6)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    driveId.toString(),
                                    productName,
                                    productCategory,
                                    createdBy,
                                    projectId,
                                    providerGeneratedId,
                                    resourceJson
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }
                    channel.send("Dump ingresses complete")


                    // Dumps IPs
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Dump IPs 1/2")
                    val trackedIps = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            with
                                resource as (
                                    select
                                        r,
                                        fc,
                                        p,
                                        pc,
                                        provider.accessible_resources(
                                            '#P_' || :provider,
                                            'network_ip',
                                            '{PROVIDER}',
                                            r.id,
                                            '',
                                            true,
                                            true,
                                            false
                                        ) ar
                                    from
                                        accounting.product_categories pc
                                        join accounting.products p on pc.id = p.category
                                        join provider.resource r on p.id = r.product
                                        join app_orchestrator.network_ips fc on r.id = fc.resource
                                    where
                                        pc.provider = :provider
                                )
                            select
                                (r).id as drive_id,
                                (p).name as product_id,
                                (pc).category as product_category,
                                (r).created_by as created_by,
                                (r).project as project_id,
                                (r).provider_generated_id as provider_generated_id,
                                provider.resource_to_json(ar, app_orchestrator.network_ip_to_json(fc)) as resource
                            from
                                resource
                            ;
                        """
                    ).rows
                    channel.send("Dump IPs 2/2")

                    run {
                        val outputFile = File(outputDir, "tracked_ips.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in trackedIps) {
                            val driveId = drive.getLong(0)!!
                            val productName = drive.getString(1)!!
                            val productCategory = drive.getString(2)!!
                            val createdBy = drive.getString(3)!!
                            val projectId = drive.getString(4)
                            val providerGeneratedId = drive.getString(5)
                            val resourceJson = drive.getString(6)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf(
                                    driveId.toString(),
                                    productName,
                                    productCategory,
                                    createdBy,
                                    projectId,
                                    providerGeneratedId,
                                    resourceJson
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }

                    channel.send("Dump IPs complete")

                    // Dumps licenses
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Dump licenses 1/2")
                    val trackedLicenses = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            with
                                resource as (
                                    select
                                        r,
                                        fc,
                                        p,
                                        pc,
                                        provider.accessible_resources(
                                            '#P_' || :provider,
                                            'license',
                                            '{PROVIDER}',
                                            r.id,
                                            '',
                                            true,
                                            true,
                                            false
                                        ) ar
                                    from
                                        accounting.product_categories pc
                                        join accounting.products p on pc.id = p.category
                                        join provider.resource r on p.id = r.product
                                        join app_orchestrator.licenses fc on r.id = fc.resource
                                    where
                                        pc.provider = :provider
                                )
                            select
                                (r).id as drive_id,
                                (p).name as product_id,
                                (pc).category as product_category,
                                (r).created_by as created_by,
                                (r).project as project_id,
                                (r).provider_generated_id as provider_generated_id,
                                provider.resource_to_json(ar, app_orchestrator.license_to_json(fc)) as resource
                            from
                                resource
                            ;
                        """
                    ).rows
                    channel.send("Dump licenses 2/2")

                    run {
                        val outputFile = File(outputDir, "tracked_licenses.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in trackedLicenses) {
                            val driveId = drive.getLong(0)!!
                            val productName = drive.getString(1)!!
                            val productCategory = drive.getString(2)!!
                            val createdBy = drive.getString(3)!!
                            val projectId = drive.getString(4)
                            val providerGeneratedId = drive.getString(5)
                            val resourceJson = drive.getString(6)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    driveId.toString(),
                                    productName,
                                    productCategory,
                                    createdBy,
                                    projectId,
                                    providerGeneratedId,
                                    resourceJson
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }
                    channel.send("Dump licenses complete")

                    // Dumps projects
                    // ---------------------------------------------------------------------------------------------------------
                    channel.send("Dump projects 1/2")
                    val trackedProjects = session.sendPreparedStatement(
                        {
                            setParameter("provider", providerId)
                        },
                        """
                            with
                                relevant_project_ids as (
                                    select distinct p.id, p.title
                                    from
                                        project.projects p
                                        join accounting.wallet_owner wo on p.id = wo.project_id
                                        join accounting.wallets_v2 w on wo.id = w.wallet_owner
                                        join accounting.product_categories pc on w.product_category = pc.id
                                    where
                                        pc.provider = :provider
                                ),
                                relevant_projects as (
                                    select p.id, p
                                    from
                                        relevant_project_ids pid
                                        join project.projects p on pid.id = p.id
                                ),
                                relevant_members as (
                                    select pid.id, array_agg(pm) arr
                                    from
                                        relevant_project_ids pid
                                        join project.project_members pm on pm.project_id = pid.id
                                    group by
                                        pid.id
                                ),
                                relevant_groups as (
                                    select pid.id, array_agg(pm) arr
                                    from
                                        relevant_project_ids pid
                                        join project.groups pm on pm.project = pid.id
                                    group by
                                        pid.id
                                ),
                                relevant_group_members as (
                                     select pid.id, array_agg(pm) arr
                                    from
                                        relevant_project_ids pid
                                        join project.groups g on pid.id = g.project
                                        join project.group_members pm on pm.group_id = g.id
                                    group by
                                        pid.id
                                )
                            select
                                (p).id as project_id,
                                project.project_to_json(p, g.arr, gm.arr, mem.arr, false, 'USER') as ucloud_project,
                                (floor(extract(epoch from (p).modified_at) * 1000))::int8 as last_update
                            from
                                relevant_projects p
                                join relevant_members mem on p.id = mem.id
                                join relevant_groups g on p.id = g.id
                                join relevant_group_members gm on p.id = gm.id
                            ;                    
                        """
                    ).rows
                    channel.send("Dump projects 2/2")

                    run {
                        val outputFile = File(outputDir, "tracked_projects.jsonl")
                        val writer = PrintWriter(outputFile)
                        for (drive in trackedProjects) {
                            val projectId = drive.getString(0)!!
                            val projectJson = drive.getString(1)!!
                            val lastUpdate = drive.getLong(2)!!

                            val string = defaultMapper.encodeToString(
                                arrayListOf<String?>(
                                    projectId, projectJson, lastUpdate.toString()
                                )
                            )
                            writer.println(string)
                        }
                        writer.close()
                    }
                    channel.send("Dump projects complete")

                    channel.send("Data export complete")
                } catch (ex: Throwable) {
                    channel.send("Error: ${ex.toReadableStacktrace()}")
                } finally {
                    channel.close()
                }
            }
        }

        return channel
    }
}