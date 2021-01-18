package dk.sdu.cloud.app.kubernetes.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicense
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenseBrowseRequest
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenseUpdateRequest
import dk.sdu.cloud.app.orchestrator.api.License
import dk.sdu.cloud.app.orchestrator.api.LicenseControl
import dk.sdu.cloud.app.orchestrator.api.LicenseControlUpdateRequestItem
import dk.sdu.cloud.app.orchestrator.api.LicenseState
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

object LicenseServerTable : SQLTable("license_servers") {
    val id = text("id")
    val address = text("address")
    val port = int("port")
    val license = text("license")
    val tags = jsonb("tags")
}

object LicenseInstancesTable : SQLTable("license_instances") {
    val orchestratorId = text("orchestrator_id")
    val serverId = text("server_id")
}

class LicenseService(
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
) {
    suspend fun createServer(request: BulkRequest<KubernetesLicense>) {
        db.withSession { session ->
            request.items.forEach { license ->
                session.insert(LicenseServerTable) {
                    set(LicenseServerTable.id, license.id)
                    set(LicenseServerTable.address, license.address)
                    set(LicenseServerTable.port, license.port)
                    set(LicenseServerTable.license, license.license)
                    set(LicenseServerTable.tags, defaultMapper.writeValueAsString(license.tags))
                }

                val resp = Products.createProduct.call(
                    license.toProduct(),
                    serviceClient
                )

                if (resp.statusCode != HttpStatusCode.Conflict) {
                    resp.orThrow()
                }
            }
        }
    }

    suspend fun browseServers(request: KubernetesLicenseBrowseRequest): PageV2<KubernetesLicense> {
        return db.paginateV2(
            Actor.System,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("tag", request.tag)
                    },
                    """
                        declare c cursor for
                        select *
                        from app_kubernetes.license_servers
                        where 
                            :tag::text is null or 
                            :tag in (select jsonb_array_elements_text(tags))
                        order by id
                    """
                )
            },
            mapper = { _, rows -> mapRows(rows) }
        )
    }

    suspend fun updateServer(request: KubernetesLicenseUpdateRequest) {
        db.withSession { session ->
            request.items.forEach { newLicense ->
                val success = session.sendPreparedStatement(
                    {
                        setParameter("id", newLicense.id)

                        setParameter("address", newLicense.address)
                        setParameter("license", newLicense.license)
                        setParameter("port", newLicense.port)
                        setParameter("tags", defaultMapper.writeValueAsString(newLicense.tags))
                    },
                    """
                        update app_kubernetes.license_servers
                        set
                            address = :address,
                            license = :license::text,
                            port = :port,
                            tags = :tags
                        where
                            id = :id
                    """
                ).rowsAffected != 0L

                if (!success) throw RPCException("License does not exist: ${newLicense.id}", HttpStatusCode.NotFound)

                Products.updateProduct.call(
                    newLicense.toProduct(),
                    serviceClient
                ).orThrow()
            }
        }
    }

    private fun KubernetesLicense.toProduct(): Product.License {
        return Product.License(
            id,
            1_000_000,
            ProductCategoryId(id, UCLOUD_PROVIDER),
            description = "Software license",
            tags = tags,
            availability = availability,
            paymentModel = paymentModel,
            priority = priority
        )
    }

    private fun mapRows(
        rows: ResultSet
    ): List<KubernetesLicense> {
        return rows.map {
            KubernetesLicense(
                it.getField(LicenseServerTable.id),
                it.getField(LicenseServerTable.address),
                it.getField(LicenseServerTable.port),
                defaultMapper.readValue(it.getField(LicenseServerTable.tags)),
                it.getFieldNullable(LicenseServerTable.license),
                TODO(),
                TODO()
            )
        }
    }

    suspend fun createInstance(request: BulkRequest<License>) {
        db.withSession { session ->
            request.items.forEach { license ->
                session.insert(LicenseInstancesTable) {
                    set(LicenseInstancesTable.orchestratorId, license.id)
                    set(LicenseInstancesTable.serverId, license.product.id)
                }
            }

            LicenseControl.update.call(
                bulkRequestOf(request.items.map {
                    LicenseControlUpdateRequestItem(it.id, LicenseState.READY, "License is ready for use")
                }),
                serviceClient
            ).orThrow()
        }
    }

    suspend fun deleteInstance(request: BulkRequest<License>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", request.items.map { it.id })
                },
                """
                    delete from app_kubernetes.license_instances 
                    where orchestrator_id in (select unnest(:ids::text[]))
                """
            )
        }
    }

    suspend fun retrieveServerFromInstance(orchestratorId: String): KubernetesLicense? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", orchestratorId)
                    },
                    """
                        select s.*
                        from app_kubernetes.license_servers s join license_instances i on s.id = i.server_id
                        where i.orchestrator_id = :id
                    """
                )
                .let { mapRows(it.rows) }
                .singleOrNull()
        }
    }
}
