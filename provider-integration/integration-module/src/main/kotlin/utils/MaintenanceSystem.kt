package dk.sdu.cloud.utils

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.providers.Maintenance
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.cli.findOption
import dk.sdu.cloud.cli.genericCommandLineHandler
import dk.sdu.cloud.cli.sendCommandLineUsage
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.config.VerifyResult
import dk.sdu.cloud.controllers.ControllerContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.sql.bindLongNullable
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

object MaintenanceSystem {
    lateinit var controllerContext: ControllerContext

    fun initialize(controllerContext: ControllerContext) {
        this.controllerContext = controllerContext

        cli()
        ipcServer()
    }

    private fun cli() {
        controllerContext.pluginContext.commandLineInterface?.addHandler(CliHandler("maintenance") { args ->
            fun sendHelp(reason: String? = null): Nothing {
                if (reason != null) {
                    sendTerminalMessage {
                        bold { line(reason) }
                        line()
                    }
                }

                sendCommandLineUsage("maintenance", "Manage the maintenance status of products") {
                    subcommand("ls", "List all maintenance periods this includes past and future")
                    subcommand("add", "Create a new maintenance period") {
                        arg(
                            "availability",
                            description = "The availability during the maintenance must be one of: 'minor', 'major' or 'unavailable'"
                        )

                        arg(
                            "productMatcher",
                            description = "A product matcher which targets a set of products (see products.yaml for reference)"
                        )

                        arg(
                            "--description=<description>",
                            description = "A description of the maintenance period."
                        )

                        arg(
                            "--starts-at=<date-and-time>",
                            description = "A date and time for when the maintenance period starts. Must be " +
                                    "formatted according to ISO-8601. For example: 2023-10-20T14:00:32+02:00."
                        )

                        arg(
                            "--ends-at=<date-and-time>",
                            optional = true,
                            description = "A date and time for when the maintenance period ends. Must be " +
                                    "formatted according to ISO-8601. For example: 2023-10-20T14:00:32+02:00."
                        )
                    }

                    subcommand("get", "Retrieves information about a maintenance period") {
                        arg("id", description = "The ID of the maintenance period (see ls)")
                    }

                    subcommand("update", "Updates a maintenance period") {
                        arg("id", description = "The ID of the maintenance period (see ls)")

                        arg(
                            "--availability=<availability>",
                            optional = true,
                            description = "The availability during the maintenance must be one of: 'minor', 'major' or 'unavailable'"
                        )

                        arg(
                            "--products=<productMatcher>",
                            optional = true,
                            description = "A product matcher which targets a set of products (see products.yaml for reference)"
                        )

                        arg(
                            "--description=<description>",
                            description = "A description of the maintenance period."
                        )

                        arg(
                            "--starts-at=<date-and-time>",
                            description = "A date and time for when the maintenance period starts. Must be " +
                                    "formatted according to ISO-8601. For example: 2023-10-20T14:00:32+02:00."
                        )

                        arg(
                            "--ends-at=<date-and-time>",
                            optional = true,
                            description = "A date and time for when the maintenance period ends. Must be " +
                                    "formatted according to ISO-8601. For example: 2023-10-20T14:00:32+02:00."
                        )
                    }

                    subcommand("stop", "Stops a maintenance period (identical to update with --ends-at = now)") {
                        arg("id", description = "The ID of the maintenance period (see ls)")
                    }
                }
            }

            val ipcClient = controllerContext.pluginContext.ipcClientOptional ?: error("no ipc client")

            genericCommandLineHandler {
                when (args.getOrNull(0)) {
                    "add" -> {
                        val availability = when (args.getOrNull(1)) {
                            "minor" -> Maintenance.Availability.MINOR_DISRUPTION
                            "major" -> Maintenance.Availability.MAJOR_DISRUPTION
                            "unavailable" -> Maintenance.Availability.NO_SERVICE
                            else -> sendHelp("availability must be either 'minor', 'major' or 'unavailable'")
                        }

                        val productMatcherString = args.getOrNull(2) ?: sendHelp("missing productMatcher")
                        parseProductMatcher(productMatcherString) ?: sendHelp("invalid product matcher")

                        val description = findOption("--description", args) ?: sendHelp("Missing description")
                        val startsAt = parseDateAndTime(findOption("--starts-at", args)) ?: sendHelp("missing/invalid starts at")
                        val endsAt = run {
                            val option = findOption("--ends-at", args) ?: return@run null
                            parseDateAndTime(option) ?: sendHelp("invalid ends at")
                        }

                        val id = ipcClient.sendRequest(
                            Ipc.create,
                            MaintenancePeriod.Spec(
                                description,
                                availability,
                                productMatcherString,
                                startsAt,
                                endsAt
                            )
                        ).id

                        sendTerminalMessage {
                            green { bold { line("OK! Period ID is $id") } }
                        }
                    }

                    "ls" -> {
                        val items = ipcClient.sendRequest(
                            Ipc.browse,
                            Unit
                        ).items

                        sendTerminalTable {
                            header("ID", 5)
                            header("Availability", 25)
                            header("Starts at", 25)
                            header("Ends at", 25)
                            header("Description", 40)

                            for (item in items) {
                                nextRow()

                                cell(item.id)

                                cell(when (item.specification.availability) {
                                    Maintenance.Availability.MINOR_DISRUPTION -> "Minor disruption"
                                    Maintenance.Availability.MAJOR_DISRUPTION -> "Major disruption"
                                    Maintenance.Availability.NO_SERVICE -> "Service unavailable"
                                })

                                cell(
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                                        Date(item.specification.startsAt).toInstant().atZone(ZoneId.systemDefault())
                                    )
                                )

                                cell(
                                    item.specification.endsAt?.let {
                                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                                            Date(it).toInstant().atZone(ZoneId.systemDefault())
                                        )
                                    } ?: "N/A"
                                )

                                cell((item.specification.description.lines().firstOrNull() ?: "").take(40))
                            }
                        }
                    }

                    "get" -> {
                        val id = args.getOrNull(1) ?: sendHelp()
                        val period = ipcClient.sendRequest(
                            Ipc.retrieve,
                            FindByStringId(id)
                        )

                        sendTerminalFrame {
                            title("Maintenance period", wide = true)

                            field("ID", id)

                            field("Availability", when (period.specification.availability) {
                                Maintenance.Availability.MINOR_DISRUPTION -> "Minor disruption"
                                Maintenance.Availability.MAJOR_DISRUPTION -> "Major disruption"
                                Maintenance.Availability.NO_SERVICE -> "Service unavailable"
                            })

                            field(
                                "Starts at",
                                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                                    Date(period.specification.startsAt).toInstant().atZone(ZoneId.systemDefault())
                                )
                            )

                            field(
                                "Ends at",
                                period.specification.endsAt?.let {
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                                        Date(it).toInstant().atZone(ZoneId.systemDefault())
                                    )
                                } ?: "N/A"
                            )

                            field(
                                "Matching products",
                                buildString {
                                    val products = controllerContext.pluginContext.config.products.allProducts
                                    val matcher = period.matcher()
                                    val matchedProducts = products.filter {
                                        matcher.match(ProductReferenceWithoutProvider(it.name, it.category.name)) > 0
                                    }

                                    for (product in matchedProducts) {
                                        appendLine("${product.name} / ${product.category.name}")
                                    }
                                }.trim()
                            )

                            field("Description", period.specification.description)
                        }
                    }

                    "update" -> {
                        val id = args.getOrNull(1) ?: sendHelp()

                        val availability = run {
                            val option = findOption("--availability", args) ?: return@run null
                            when (option) {
                                "minor" -> Maintenance.Availability.MINOR_DISRUPTION
                                "major" -> Maintenance.Availability.MAJOR_DISRUPTION
                                "unavailable" -> Maintenance.Availability.NO_SERVICE
                                else -> sendHelp()
                            }
                        }

                        run {
                            val option = findOption("--products", args) ?: return@run null
                            parseProductMatcher(option) ?: sendHelp()
                        }

                        val description = findOption("--description", args)

                        val startsAt = run {
                            val option = findOption("--starts-at", args) ?: return@run null
                            parseDateAndTime(option) ?: sendHelp()
                        }

                        val endsAt = run {
                            val option = findOption("--ends-at", args) ?: return@run null
                            parseDateAndTime(option) ?: sendHelp()
                        }

                        ipcClient.sendRequest(
                            Ipc.update,
                            MaintenanceUpdate(
                                id,
                                description = description,
                                availability = availability,
                                productMatcher = findOption("--products", args),
                                startsAt = startsAt,
                                endsAt = endsAt
                            )
                        )
                    }

                    "stop" -> {
                        val id = args.getOrNull(1) ?: sendHelp()

                        ipcClient.sendRequest(
                            Ipc.update,
                            MaintenanceUpdate(id, endsAt = System.currentTimeMillis())
                        )
                    }

                    else -> sendHelp()
                }
            }
        })
    }

    private fun parseProductMatcher(matcher: String?): VerifiedConfig.Plugins.ProductMatcher? {
        return when (val result = VerifiedConfig.Plugins.ProductMatcher.parse(matcher ?: return null)) {
            is VerifyResult.Ok -> {
                result.result
            }

            is VerifyResult.Error -> run {
                sendTerminalMessage {
                    red { bold { line("Error!") } }
                    line(result.message)
                }

                null
            }

            is VerifyResult.Warning -> {
                sendTerminalMessage {
                    red { bold { line("Error!") } }
                    line(result.message)
                }

                null
            }
        }
    }

    private fun parseDateAndTime(input: String?): Long? {
        if (input == null) return null
        return Date.from(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(input))).time
    }

    private fun ipcServer() {
        if (!controllerContext.configuration.shouldRunServerCode()) return

        controllerContext.pluginContext.ipcServerOptional?.let { ipcServer ->
            ipcServer.addHandler(Ipc.create.handler { user, request ->
                if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                var id: FindByStringId? = null

                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            insert into maintenance_periods (availability, product_matcher, starts_at, ends_at, description)
                            values (:availability, :product_matcher, :starts_at, :ends_at, :description)
                            returning id
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindString("availability", request.availability.name)
                            bindString("product_matcher", request.productMatcher)
                            bindLong("starts_at", request.startsAt)
                            bindLongNullable("ends_at", request.endsAt)
                            bindString("description", request.description)
                        },
                        readRow = { row ->
                            id = FindByStringId(row.getLong(0)!!.toString())
                        }
                    )
                }

                id ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            })

            ipcServer.addHandler(Ipc.update.handler { user, request ->
                if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            update maintenance_periods
                            set
                                availability = coalesce(:availability, availability),
                                description = coalesce(:description, description),
                                product_matcher = coalesce(:product_matcher, product_matcher),
                                starts_at = coalesce(:starts_at, starts_at),
                                ends_at = coalesce(:ends_at, ends_at)
                            where
                                id = :id
                        """
                    ).useAndInvokeAndDiscard {
                        bindLongNullable("id", request.id.toLongOrNull())
                        bindStringNullable("description", request.description)
                        bindStringNullable("availability", request.availability?.name)
                        bindStringNullable("product_matcher", request.productMatcher)
                        bindLongNullable("starts_at", request.startsAt)
                        bindLongNullable("ends_at", request.endsAt)
                    }
                }
            })

            ipcServer.addHandler(Ipc.browse.handler { user, request ->
                if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                val items = ArrayList<MaintenancePeriod>()
                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            select id, availability, product_matcher, starts_at, ends_at, description
                            from maintenance_periods
                            where
                                (ends_at is null or :now <= ends_at)
                            order by
                                starts_at asc
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindLong("now", System.currentTimeMillis())
                        },
                        readRow = { row ->
                            items.add(
                                MaintenancePeriod(
                                    row.getLong(0)!!.toString(),
                                    MaintenancePeriod.Spec(
                                        row.getString(5)!!,
                                        Maintenance.Availability.valueOf(row.getString(1)!!),
                                        row.getString(2)!!,
                                        row.getLong(3)!!,
                                        row.getLong(4)
                                    )
                                )
                            )

                        }
                    )
                }

                PageV2(items.size, items, null)
            })

            ipcServer.addHandler(Ipc.retrieve.handler { user, request ->
                if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                val items = ArrayList<MaintenancePeriod>()
                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            select id, availability, product_matcher, starts_at, ends_at, description
                            from maintenance_periods
                            where
                                id = :id
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindLongNullable("id", request.id.toLongOrNull())
                        },
                        readRow = { row ->
                            items.add(
                                MaintenancePeriod(
                                    row.getLong(0)!!.toString(),
                                    MaintenancePeriod.Spec(
                                        row.getString(5)!!,
                                        Maintenance.Availability.valueOf(row.getString(1)!!),
                                        row.getString(2)!!,
                                        row.getLong(3)!!,
                                        row.getLong(4)
                                    )
                                )
                            )

                        }
                    )
                }

                items.singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            })
        }
    }

    suspend fun fetchActiveMaintenancePeriods(): List<MaintenancePeriod> {
        val now = System.currentTimeMillis()
        return controllerContext.pluginContext.ipcClient.sendRequest(Ipc.browse, Unit).items
            .filter { now >= it.specification.startsAt }
    }
}

@Serializable
data class MaintenancePeriod(
    val id: String,
    val specification: Spec,
) {
    @Serializable
    data class Spec(
        val description: String,
        val availability: Maintenance.Availability,
        val productMatcher: String,
        val startsAt: Long,
        val endsAt: Long?
    )
}

fun MaintenancePeriod.matcher(): VerifiedConfig.Plugins.ProductMatcher {
    return (VerifiedConfig.Plugins.ProductMatcher.parse(specification.productMatcher) as VerifyResult.Ok).result
}

@Serializable
private data class MaintenanceUpdate(
    val id: String,
    val description: String? = null,
    val availability: Maintenance.Availability? = null,
    val productMatcher: String? = null,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
)

private object Ipc : IpcContainer("maintenance") {
    val create = createHandler(MaintenancePeriod.Spec.serializer(), FindByStringId.serializer())
    val browse = browseHandler(Unit.serializer(), PageV2.serializer(MaintenancePeriod.serializer()))
    val update = updateHandler("update", MaintenanceUpdate.serializer(), Unit.serializer())
    val retrieve = retrieveHandler(FindByStringId.serializer(), MaintenancePeriod.serializer())
}
