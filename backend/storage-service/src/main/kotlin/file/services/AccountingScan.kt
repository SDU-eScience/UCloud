package dk.sdu.cloud.file.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.*
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.linuxfs.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.*
import kotlin.math.*
import kotlin.system.*

class AccountingScan(
    private val fs: LinuxFS,
    private val processRunner: LinuxFSRunnerFactory,
    private val client: AuthenticatedClient,
    private val db: DBContext
) {
    suspend fun scan() {
        val product = Products.listProductsByType.call(
            ListProductsByAreaRequest(UCLOUD_PROVIDER, ProductArea.STORAGE),
            client
        ).orThrow().items.singleOrNull() ?: throw IllegalStateException("Could not find the UCloud storage product")

        scanRoot("/home", WalletOwnerType.USER, product)
        scanRoot("/projects", WalletOwnerType.PROJECT, product)
    }

    private suspend fun scanRoot(path: String, type: WalletOwnerType, product: Product) {
        log.info("Initializing scan of file system root: $path")
        val later = Time.now() + (1000 * 60 * 60 * 24 * 7)
        val dateString = LocalDate.ofInstant(
            Instant.ofEpochMilli(Time.now()),
            Time.javaTimeZone
        ).format(DateTimeFormatter.ofPattern("YYYY.MM.dd"))

        processRunner.withContext(SERVICE_USER) { ctx ->
            val directory = fs.listDirectory(ctx, path, setOf(StorageFileAttribute.path))
            val numberOfChunks = ceil(directory.size / 50.0)
            log.info("Root contains $numberOfChunks chunks")
            for ((index, chunk) in directory.chunked(50).withIndex()) {
                var success = false
                retries@ for (attempt in 1..10) {
                    log.info("[$path] Processing chunk ${index + 1} of $numberOfChunks (attempt: $attempt)")
                    log.info("Determining usage of users")
                    val charges = ArrayList<ReserveCreditsRequest>()
                    for (homeFolder in chunk) {
                        val wallet = Wallet(
                            homeFolder.path.fileName(),
                            type,
                            product.category
                        )

                        val usage = fs.calculateRecursiveStorageUsed(ctx, homeFolder.path)
                        val gigabytes = ceil(usage.toDouble() / 1.GiB).toLong()
                        charges.add(
                            ReserveCreditsRequest(
                                // This is unique so we shouldn't be able to double-charge users
                                jobId = "storage-$dateString-${wallet.id}",
                                amount = product.pricePerUnit * gigabytes,
                                expiresAt = later,
                                account = wallet,
                                jobInitiatedBy = SERVICE_USER,
                                productId = product.id,
                                productUnits = gigabytes,
                                chargeImmediately = true,

                                // We ask the accounting system to ignore our request if we have already charged today
                                skipIfExists = true,

                                // This resource has already been consumed, don't check if we are reaching the limit
                                // just charge the amount they have used.
                                skipLimitCheck = true
                            )
                        )
                    }

                    log.info("Charging users")
                    val chargeResult = Wallets.reserveCreditsBulk.call(
                        ReserveCreditsBulkRequest(charges),
                        client
                    )

                    if (chargeResult is IngoingCallResponse.Ok) {
                        success = true

                        // This is done mostly for debugging purposes in case we get a failed run. This will allow us
                        // to actually clean up after ourselves.
                        log.info("Successfully charged chunk")
                        markAsScanned(chunk.map { it.path.fileName() }, type, dateString)
                        break@retries
                    } else {
                        log.warn("Caught an error while processing chunk: $chunk")
                        log.warn("Status code: ${chargeResult.statusCode}")
                        delay(30 * 1000)
                    }
                }

                if (!success) {
                    log.error("Was unable to process the following chunk: $chunk")
                    exitProcess(1)
                }
            }
        }
    }

    private suspend fun markAsScanned(entities: List<String>, type: WalletOwnerType, dateString: String) {
        db.withSession { session ->
            entities.forEach { entity ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("datestring", dateString)
                            setParameter("entity", entity)
                            setParameter("type", type.name)
                        },
                        """
                            insert into scans (date_string, entity, entity_type) 
                            values (:datestring, :entity, :type)
                            on conflict (date_string, entity, entity_type) do nothing
                        """
                    )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
