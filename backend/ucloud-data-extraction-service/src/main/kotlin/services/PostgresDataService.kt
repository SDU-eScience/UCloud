package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.ucloud.data.extraction.api.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime

class PostgresDataService(val db: AsyncDBSessionFactory) {

    fun getUsernames(): List<UCloudUser> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                            SELECT id, created_at
                            FROM auth.principals
                        """
                    ).rows
                    .map {
                        UCloudUser(
                            it.getString(0)!!,
                            it.getDate(1)!!
                        )
                    }
                    .filter { !it.username.startsWith("_") }
            }
        }
    }

    fun getUniversity(username: String): UniversityID {
        return runBlocking {
            val orgId = db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", username)
                        },
                        """
                            SELECT org_id
                            FROM auth.principals
                            WHERE id = :username
                        """
                    ).rows
                    .singleOrNull()
                    ?.getString(0) ?: "PASSWORD"
            }
            UniversityID.fromOrgId(orgId)
        }
    }

    fun calculateProductUsageForUserInProjectForDate(
        startDate: LocalDateTime,
        productType: ProductType,
        username: String,
        projectId: String
    ): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            //Uses the day with highest storage usage
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("projectid", projectId)
                            },
                            """
                                SELECT units::bigint, DATE(completed_at) as time
                                FROM "accounting"."transactions"
                                WHERE account_id = :projectid
                                  AND original_account_id = :projectid
                                  AND DATE(completed_at) = :startDate::timestamp
                                  AND product_category = :type
                                  AND initiated_by = '_storage'
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return storageInGB * 1000
        } else {
            return runBlocking {
                val amount = db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("username", username)
                                setParameter("projectid", projectId)
                            },
                            """
                                SELECT SUM(amount)::bigint
                                FROM accounting.transactions
                                WHERE account_id = :projectid
                                    AND original_account_id = :projectid
                                    AND product_category = :type
                                    AND initiated_by = :username
                                    AND DATE(completed_at) = :startDate::timestamp
                                    
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
                //Get Corehours by dividing amount with pricing and then with 60 to get in hours
                ((amount / productType.getPricing()) / 60).toLong()
            }
        }
    }

    fun calculateProductUsageForUserInProject(
        startDate: LocalDateTime,
        productType: ProductType,
        username: String,
        projectId: String
    ): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            //Uses the day with highest storage usage
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("projectId", projectId)
                            },
                            """
                                SELECT MAX(sum)::bigint
                                FROM (
                                    SELECT SUM(units)::bigint as sum, time
                                    FROM (
                                        SELECT units::bigint, DATE(completed_at) as time
                                        FROM "accounting"."transactions"
                                        WHERE completed_at >= :startDate :: timestamp
                                            AND product_category = :type
                                            AND initiated_by = '_storage'
                                            AND account_id = :projectId
                                            AND original_account_id = :projectId
                                        GROUP BY time, units
                                        ORDER BY time
                                    ) AS amount
                                GROUP BY time
                                ) as summations;
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return storageInGB * 1000
        } else {
            return runBlocking {
                val amount = db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toString().substringBefore("T"))
                                setParameter("type", productType.catagoryId)
                                setParameter("username", username)
                                setParameter("projectid", projectId)
                            },
                            """
                            SELECT sum(amount)::bigint
                            FROM  "accounting"."transactions"
                            WHERE completed_at >= :startDate::timestamp
                                AND product_category = :type 
                                AND initiated_by = :username
                                AND account_id = :projectid 
                                AND original_account_id = :projectid
                                AND transaction_comment NOT LIKE 'Transf%'
                        """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
                //Get Corehours by dividing amount with pricing and then with 60 to get in hours
                ((amount / productType.getPricing()) / 60).toLong()
            }
        }
    }

    fun calculateProductUsage(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        productType: ProductType,
    ): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            //Uses the day with highest storage usage
            val excludingProjectsIds = listOf(
                "5057c9d4-a0f5-4d6a-90ed-16061327dd7b", //high energy
                "5de7b78c-2fa4-4893-beb1-ff5c231d4586", //sdukoldby
                "1e67f75a-ef63-4489-aeb7-7a0d2039930a", //sdujk
                "c6eaf989-2705-49aa-8ff3-fd97a5b785c2", //sduvarcall
                "1e8916f5-2491-4f34-b38e-0356370c7770", //sdularsen
                "5f7562d3-e77d-4805-a66f-58846397d6b7" // sdumembio
            )
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("endDate", endDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("sdu", SDU_CLOUD_PROJECT_ID)
                                setParameter("sdutype1", SDU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("aautype1", AAU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("autype1", AU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("cbstype1", CBS_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("dtutype1", DTU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("itutype1", ITU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("kutype1", KU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("ructype1", RUC_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("excludingProjects", excludingProjectsIds)

                            },
                            """
                                SELECT MAX(sum)::bigint
                                FROM (
                                    SELECT SUM(units)::bigint as sum, time
                                    FROM (
                                        SELECT units::bigint, DATE(completed_at) as time
                                        FROM "accounting"."transactions"
                                        WHERE completed_at <= :endDate :: timestamp
                                            AND completed_at >= :startDate :: timestamp
                                            AND product_category = :type
                                            AND initiated_by = '_storage'
                                            AND (account_id = :sdu 
                                                OR account_id = :sdutype1
                                                OR account_id = :aautype1
                                                OR account_id = :autype1
                                                OR account_id = :cbstype1
                                                OR account_id = :dtutype1
                                                OR account_id = :itutype1
                                                OR account_id = :kutype1
                                                OR account_id = :ructype1
                                            )
                                            AND original_account_id NOT IN (select unnest(:excludingProjects::text[]) )
                                            AND transaction_comment NOT LIKE 'Transf%'
                                        GROUP BY time, units
                                        ORDER BY time
                                    ) AS amount
                                GROUP BY time
                                ) as summations;
                            """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return storageInGB * 1000
        } else {
            //amount = amount payed for jobs in period SDU (Does not include personal workspaces)
            val amount = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("endDate", endDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                                setParameter("sdu", SDU_CLOUD_PROJECT_ID)
                                setParameter("sdutype1", SDU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("aautype1", AAU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("autype1", AU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("cbstype1", CBS_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("dtutype1", DTU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("itutype1", ITU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("kutype1", KU_TYPE_1_CLOUD_PROJECT_ID)
                                setParameter("ructype1", RUC_TYPE_1_CLOUD_PROJECT_ID)
                            },
                            """
                            SELECT sum(amount)::bigint
                            FROM  "accounting"."transactions"
                            WHERE completed_at >= :startDate::timestamp
                                AND completed_at <= :endDate::timestamp
                                AND product_category = :type 
                                AND initiated_by NOT LIKE '\_%'
                                AND (account_id = :sdu 
                                    OR account_id = :sdutype1
                                    OR account_id = :aautype1
                                    OR account_id = :autype1
                                    OR account_id = :cbstype1
                                    OR account_id = :dtutype1
                                    OR account_id = :itutype1
                                    OR account_id = :kutype1
                                    OR account_id = :ructype1
                                )
                           AND transaction_comment NOT LIKE 'Transf%'
                        """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            //Get Corehours by dividing amount with pricing and then with 60 to get in hours
            return ((amount / productType.getPricing()) / 60).toLong()
        }
    }

    fun getWallets(projectId: String): List<WalletInfo> {
        return runBlocking {
            db.withTransaction { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectid", projectId)
                        },
                        """
                            SELECT account_id, allocated::bigint, product_category
                            FROM accounting.wallets
                            WHERE account_id = :projectid
                        """
                    ).rows
                    .map {
                        WalletInfo(
                            it.getString(0)!!,
                            it.getLong(1)!!,
                            ProductType.createFromCatagory(it.getString(2)!!)
                        )
                    }
            }
        }
    }

    fun getStorageQuotaInBytes(projectId: String): Long {
        return runBlocking {
            db.withTransaction { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectid", projectId)
                        },
                        """
                            SELECT quota_in_bytes
                            FROM storage.quotas
                            WHERE path LIKE '%' || :projectid;
                        """
                    ).rows
                    .singleOrNull()
                    ?.getLong(0) ?: 0L
            }
        }
    }

    fun findProjects(): List<Project> {
        return runBlocking {
             db.withTransaction { session ->
                //Projects excluded root projects and SDU_* and TYPE1_* projects (rootish)
                session
                    .sendPreparedStatement(
                        {},
                        """
                            SELECT id, title, parent, archived
                            FROM project.projects
                            WHERE parent IS NOT NULL
                                AND parent != '3196deee-c3c2-464b-b328-4d3c5d02b953'
                                AND parent != 'e37a704e-34e3-4f11-931c-2ecf3f07ffcb'
                                AND parent != '7672413e-d43b-4425-aa96-4cdd846e1192'
                        """
                    ).rows
                    .map {
                        Project(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2),
                            it.getBoolean(3)!!
                        )
                    }
            }
        }
    }

    fun findProject(
        id: String
    ): Project {
        return runBlocking {
            db.withSession { session ->
                val project = session
                    .sendPreparedStatement(
                        { setParameter("id", id) },
                        "select id, title, parent, archived from project.projects where id = :id"
                    )
                    .rows
                    .singleOrNull()
                if (project != null) {
                    Project(
                        project.getString(0)!!,
                        project.getString(1)!!,
                        project.getString(2),
                        project.getBoolean(3)!!
                    )
                } else {
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }
            }
        }
    }

    fun viewAncestors(
        projectId: String
    ): List<Project> {
        val resultList = ArrayList<Project>()
        runBlocking {
            db.withSession { session ->
                var currentProject: Result<Project> = runCatching { findProject(projectId) }
                currentProject.getOrThrow() // Throw immediately if the initial project is not found
                while (currentProject.isSuccess) {
                    val nextProject = currentProject.getOrThrow()
                    resultList.add(nextProject)

                    val parent = nextProject.parent ?: break
                    currentProject = runCatching { findProject(parent) }
                }

                val ex = currentProject.exceptionOrNull()
                if (ex != null) {
                    if (ex is RPCException &&
                        ex.httpStatusCode in setOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden)
                    ) {
                        // All good, we expected one of these
                    } else {
                        // Not good, rethrow to caller
                        throw ex
                    }
                }
            }
        }
        return resultList.asReversed()
    }

    fun findProjectMembers(projectId: String): List<ProjectMemberInfo> {
        return runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectid", projectId)
                        },
                        """
                            SELECT created_at, username, project_id
                            FROM project.project_members
                            WHERE project_id = :projectid
                        """
                    ).rows
                    .map {
                        ProjectMemberInfo(
                            it.getDate(0)!!,
                            it.getString(1)!!,
                            it.getString(2)!!
                        )
                    }
            }
        }
    }
}
