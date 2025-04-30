package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.Prediction
import dk.sdu.cloud.accounting.api.WalletPrediction
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.delay
import kotlin.math.pow

class SimplePredictor(
    val db: DBContext
) {

    private lateinit var predictions: MutableMap<Long, WalletPrediction>

    suspend fun start() {
        predictions = mutableMapOf<Long, WalletPrediction>()
        var lastAddition = 0L
        while (true) {
            if (Time.now() - lastAddition > 1000L * 60 * 60 * 24 ) {
                generatePredictions()
                lastAddition = Time.now()
            }
            //Only check every 1 hour
            delay(1000L * 60 * 60)
        }
    }

    private fun pushWalletPrediction(prediction: WalletPrediction) {
        predictions[prediction.walletId] = prediction
    }

    fun getPrediction(walletId: Long): WalletPrediction {
        return predictions[walletId] ?: WalletPrediction(walletId, emptyList())
    }


    private suspend fun generatePredictions() {
        var lastID = 0L
        var day = 1
        val xs = arrayListOf<Int>()
        val ys = arrayListOf<Double>()
        db.withSession { session ->
            session.sendPreparedStatement(
                """
                    with relevant_samples as (
                        select row_number() over ( partition by sampled_at::date, wallet_id) as partrow, sampled_at, tree_usage , wallet_id
                        from accounting.wallet_samples_v2
                        where sampled_at > now() - interval '30 day'
                        order by  wallet_id, sampled_at
                    )
                    select wallet_id,
                           rsam.sampled_at,
                           case
                               when au.floating_point = true then rsam.tree_usage / 1000000.0
                               when au.floating_point = false and pc.accounting_frequency = 'ONCE'
                                   then rsam.tree_usage::double precision
                               when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_MINUTE'
                                   then rsam.tree_usage::double precision / 60.0
                               when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_HOUR'
                                   then rsam.tree_usage::double precision / 60.0 / 60.0
                               when au.floating_point = false and pc.accounting_frequency = 'PERIODIC_DAY'
                                   then rsam.tree_usage::double precision / 60.0 / 60.0 / 24.0
                        end tusage
                    from relevant_samples rsam join
                         accounting.wallets_v2 w on rsam.wallet_id = w.id join
                         accounting.product_categories pc on w.product_category = pc.id join
                         accounting.accounting_units au on pc.accounting_unit = au.id
                    where partrow = 1
                    order by wallet_id, sampled_at
                """.trimIndent()
            )
        }.rows.forEach { row ->
            val walletID = row.getLong(0)!!
            if (lastID == 0L) {
                lastID = walletID
            }
            if (lastID != walletID) {
                createPredictions(lastID, xs, ys)
                lastID = walletID
                day = 1
                xs.clear()
                ys.clear()
            }
            xs.add(day)
            ys.add(row.getDouble(2)!!)
            day++
        }
    }

    // Given a list of x values and y values this function creates a simple linear regression.
    // Will create the predicted values for the next 30 days
    private fun createPredictions(walletId: Long, xs: ArrayList<Int>, ys: ArrayList<Double>) {
        if (xs.size != ys.size && xs.size < 2) {
            println("Sizes are of: xs:${xs.size}, ys:${ys.size}")
            return
        }
        val numberOfXs = xs.size
        // Variance
        val variance = xs.sumOf { x -> (x - xs.average()).pow(2) }
        // Covariance
        val covariance = xs.zip(ys) { x, y -> (x - xs.average()) * (y - ys.average()) }.sum()
        // Slope
        val slope = covariance / variance
        // Y Intercept
        val yIntercept = ys.average() - slope * xs.average()
        // Simple Linear Regression
        val simpleLinearRegression = { independentVariable: Int -> slope * independentVariable + yIntercept }

        val results = mutableListOf<Prediction>()
        var daysInFuture = 1
        (numberOfXs+1..numberOfXs + 30).forEach {
            results.add(
                Prediction(
                    daysInFuture,
                    simpleLinearRegression(it))
            )
            daysInFuture++

        }
        pushWalletPrediction(WalletPrediction(walletId, results))
    }
}