package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.ucloud.data.extraction.api.ProductType
import kotlinx.coroutines.runBlocking
import org.joda.time.LocalDateTime
import kotlin.math.ceil

class PostgresDataService(val db: AsyncDBSessionFactory) {

    fun calculateProductUsage(startDate: LocalDateTime, endDate: LocalDateTime, productType: ProductType): Long {
        if (productType == ProductType.GPU && startDate.isBefore(LocalDateTime.parse("2021-03-01"))) {
            return 0
        }
        if (productType == ProductType.STORAGE) {
            val storageInGB = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("endDate", endDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                            },
                            """
                                SELECT MAX(sum)
                                FROM (
                                    SELECT SUM(units) as sum, time
                                    FROM (
                                        SELECT units, DATE(completed_at) as time
                                        FROM "accounting"."transactions"
                                        WHERE completed_at <= :endDate :: timestamp
                                            AND completed_at >= :startDate :: timestamp
                                            AND product_category = :type
                                            AND initiated_by = '_storage'
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
            val amount = runBlocking {
                db.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("startDate", startDate.toLocalDate().toString())
                                setParameter("endDate", endDate.toLocalDate().toString())
                                setParameter("type", productType.catagoryId)
                            },
                            """
                            SELECT sum(amount)::bigint
                            FROM  "accounting"."transactions"
                            WHERE completed_at >= :startDate::timestamp
                                AND completed_at <= :endDate::timestamp
                                AND product_category = :type 
                                AND initiated_by NOT LIKE '\_%'
                        """
                        ).rows
                        .firstOrNull()
                        ?.getLong(0) ?: 0L
                }
            }
            return ceil(amount / productType.getPricing() ).toLong()
        }
    }

}
