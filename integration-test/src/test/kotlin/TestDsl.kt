package dk.sdu.cloud.integration

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.faults.FaultInjections
import dk.sdu.cloud.prettyMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class UCloudTestSuite<In, Out>(
    val title: String,
    val execution: suspend (In) -> Out,
    val cleanup: suspend (In) -> Unit,
    val cases: List<UCloudTestCase<In, Out>>
) {
    var numberOfTests: Int = 0
    var completedTests = AtomicInteger(0)
}

data class UCloudTestCase<In, Out>(
    val subtitle: String,
    val input: In,
    val checks: List<suspend (output: Out?, exception: Throwable?) -> Unit>,
    val forceCleanup: Boolean
)

lateinit var rpcClient: RpcClient
lateinit var serviceClient: AuthenticatedClient
lateinit var serviceClientWs: AuthenticatedClient
const val adminUsername = "admin@dev"
lateinit var adminClient: AuthenticatedClient
lateinit var adminClientWs: AuthenticatedClient

private val firstTestEver = AtomicBoolean(true)

typealias IntegrationTest = UCloudTest
abstract class UCloudTest {
    private val allTests = ArrayList<UCloudTestSuite<*, *>>()
    protected var perCasePreparation: suspend () -> Unit = {}
    protected var perCaseCleanup: suspend () -> Unit = {}
    var testFilter: (title: String, subtitle: String) -> Boolean = { _, _ -> true }

    fun <In, Out> test(title: String, builder: UCloudTestSuiteBuilder<In, Out>.() -> Unit): UCloudTestSuite<In, Out> {
        val testSuite = UCloudTestSuiteBuilder<In, Out>(title).also(builder).build()
        allTests.add(testSuite)
        return testSuite
    }

    private fun restoreSnapshot() {
        if (!firstTestEver.compareAndSet(true, false)) return
        println(System.getenv("UCLOUD_LAUNCHER"))
        ExeCommand(
            listOf(
                System.getenv("UCLOUD_LAUNCHER") ?: error("Could not find UCLOUD_LAUNCHER env variable"),
                "restore",
                System.getenv("UCLOUD_TEST_SNAPSHOT") ?: error("Unable to find UCLOUD_TEST_SNAPSHOT env variable")
            )
        ).executeToText()

        runBlocking {
            val hosts = setOf("ucloud.localhost.direct", "k8.localhost.direct", "slurm.localhost.direct")
            for (host in hosts) {
                rpcClient.call(
                    FaultInjections.clearCaches,
                    Unit,
                    OutgoingHttpCall,
                    afterHook = {
                        it.attributes.outgoingTargetHost = HostInfo(host, "https", 443)
                    }
                )
            }
        }
    }

    @TestFactory
    fun testFactory(): List<DynamicTest> {
        run {
            // Configure RPC to the already running local cluster
            loadAndInitializeMissingCaCertsForLauncherInDevelopmentModeOnlyPlease()

            rpcClient = RpcClient().apply {
                attachFilter(OutgoingProject())
                OutgoingHttpRequestInterceptor().install(
                    this,
                    FixedOutgoingHostResolver(HostInfo("ucloud.localhost.direct", "https", 443))
                )
                attachRequestInterceptor(OutgoingWSRequestInterceptor())

                val callId = AtomicInteger(10000)
                val debugKey = AttributeKey<Int>("test-debug-id")
                attachFilter(object : OutgoingCallFilter.BeforeCall() {
                    override fun canUseContext(ctx: OutgoingCall): Boolean = true

                    override suspend fun run(
                        context: OutgoingCall,
                        callDescription: CallDescription<*, *, *>,
                        request: Any?
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        callDescription as CallDescription<Any, Any, Any>
                        val allocatedId = callId.getAndIncrement()
                        context.attributes[debugKey] = allocatedId
                        println(
                            buildString {
                                append("\uD83D\uDCE3 ")
                                append(callDescription.fullName)
                                append(" [")
                                append(allocatedId)
                                append("]")

                                if (request != null && request != Unit) {
                                    appendLine()
                                    append(
                                        prettyMapper.encodeToString(
                                            callDescription.requestType,
                                            request
                                        ).prependIndent("   ")
                                    )
                                }
                                "\uD83D\uDCE3 ${callDescription.fullName}($request) ..."
                            }
                        )
                    }
                })

                attachFilter(object : OutgoingCallFilter.AfterCall() {
                    override fun canUseContext(ctx: OutgoingCall): Boolean = true

                    override suspend fun run(
                        context: OutgoingCall,
                        callDescription: CallDescription<*, *, *>,
                        response: IngoingCallResponse<*, *>,
                        responseTimeMs: Long
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        callDescription as CallDescription<Any, Any, Any>

                        val allocatedId = context.attributes[debugKey]
                        println(
                            buildString {
                                when (response) {
                                    is IngoingCallResponse.Error -> append("❌ ")
                                    is IngoingCallResponse.Ok -> append("✅ ")
                                }

                                append(callDescription.fullName)
                                append(" [")
                                append(allocatedId)
                                append(", took ")
                                append(responseTimeMs)
                                append("ms]")

                                when (response) {
                                    is IngoingCallResponse.Error -> {
                                        appendLine()

                                        try {
                                            append(
                                                prettyMapper.encodeToString(
                                                    callDescription.errorType,
                                                    response.error!!
                                                ).prependIndent("   ")
                                            )
                                        } catch (ex: Throwable) {
                                            append("   - Unexpected error message received -")
                                        }
                                    }
                                    is IngoingCallResponse.Ok -> {
                                        appendLine()
                                        append(
                                            prettyMapper.encodeToString(
                                                callDescription.successType,
                                                response.result
                                            ).prependIndent("   ")
                                        )
                                    }
                                }
                            }
                        )
                    }
                })
            }

            val serviceAuth = RefreshingJWTAuthenticator(
                rpcClient,
                JwtRefresher.Normal("theverysecretservicetoken", OutgoingHttpCall)
            )
            val adminAuth = RefreshingJWTAuthenticator(
                rpcClient,
                JwtRefresher.Normal("theverysecretadmintoken", OutgoingHttpCall)
            )
            serviceClient = serviceAuth.authenticateClient(OutgoingHttpCall)
            serviceClientWs = serviceAuth.authenticateClient(OutgoingWSCall)
            adminClient = adminAuth.authenticateClient(OutgoingHttpCall)
            adminClientWs = adminAuth.authenticateClient(OutgoingWSCall)
        }

        val definedTests = ArrayList<DynamicTest>()
        defineTests()
        for (suite in allTests) {
            for (case in suite.cases) {
                if (testFilter(suite.title, case.subtitle)) {
                    suite.numberOfTests++

                    definedTests.add(DynamicTest.dynamicTest("${suite.title}/${case.subtitle}") {
                        restoreSnapshot()

                        runBlocking {
                            repeat(3) { println() }
                            println(String(CharArray(80) { '=' }))
                            println("${suite.title}/${case.subtitle}")
                            println(String(CharArray(80) { '=' }))
                            println()

                            @Suppress("UNCHECKED_CAST")
                            suite as UCloudTestSuite<Any?, Any?>
                            @Suppress("UNCHECKED_CAST")
                            case as UCloudTestCase<Any?, Any?>

                            try {
                                try {
                                    perCasePreparation()
                                } catch (ex: Throwable) {
                                    throw IllegalStateException(
                                        "${suite.title}/${case.subtitle}: Exception during preparation",
                                        ex
                                    )
                                }

                                val res = runCatching {
                                    suite.execution(case.input)
                                }

                                val output = res.getOrNull()
                                val exception = res.exceptionOrNull()

                                for (check in case.checks) {
                                    check(output, exception)
                                }
                            } finally {
                                perCaseCleanup()
                                suite.cleanup(case.input)
                                val completed = suite.completedTests.incrementAndGet()

                                if (suite.numberOfTests == completed || case.forceCleanup) {
                                    restoreSnapshot()
                                }
                            }
                        }
                    })
                }
            }
        }
        return definedTests
    }

    abstract fun defineTests()
}

class UCloudTestSuiteBuilder<In, Out>(val title: String) {
    private var cleanup: (suspend (In) -> Unit)? = null
    private var execution: (suspend (In) -> Out)? = null
    private val cases = ArrayList<UCloudTestCase<In, Out>>()

    data class InputContext<In>(val input: In, val testId: Int)

    fun execute(fn: suspend InputContext<In>.() -> Out) {
        execution = {
            InputContext(it, testIds.incrementAndGet()).fn()
        }
    }

    fun case(subtitle: String, builder: UCloudTestCaseBuilder<In, Out>.() -> Unit) {
        cases.add(UCloudTestCaseBuilder<In, Out>(title, subtitle).also(builder).build())
    }

    fun build(): UCloudTestSuite<In, Out> {
        if (execution == null) error("execute() was never called in $title")
        if (cases.isEmpty()) error("missing case() in $title")
        return UCloudTestSuite(title, execution!!, cleanup ?: {}, cases)
    }

    companion object {
        val testIds = AtomicInteger(0)
    }
}

data class UCloudTestCaseBuilder<In, Out>(private val parentTitle: String, val subtitle: String) {
    private var input: In? = null
    private val checks = ArrayList<suspend (output: Out?, exception: Throwable?) -> Unit>()

    data class ExceptionContext(val exception: Throwable)
    data class OutputContext<In, Out>(val input: In, val output: Out)
    var forceCleanup = false

    fun build(): UCloudTestCase<In, Out> {
        if (input == null) error("input() was never called in $parentTitle/$subtitle")
        if (checks.isEmpty()) error("missing check(), expectSuccess() or expectFailure() in $parentTitle/$subtitle")
        return UCloudTestCase(subtitle, input!!, checks, forceCleanup)
    }

    fun input(input: In) {
        this.input = input
    }

    fun expectFailure(message: String? = null, fn: suspend ExceptionContext.() -> Unit = {}) {
        checks.add { _, throwable ->
            if (throwable == null) {
                error("$parentTitle/$subtitle: Is not supposed to succeed. ${message ?: ""}")
            }

            ExceptionContext(throwable).fn()
        }
    }

    @Suppress("unused")
    fun expectStatusCode(statusCode: HttpStatusCode) {
        expectFailure {
            assertThatInstance(exception, "should have status code $statusCode") {
                it is RPCException && it.httpStatusCode == statusCode
            }
        }
    }

    fun expectSuccess() {
        check {
            // This is only OK if execute {} is successful.
        }
    }

    fun check(fn: suspend OutputContext<In, Out>.() -> Unit) {
        checks.add { out, throwable ->
            if (throwable != null) {
                throw throwable
            }

            @Suppress("UNCHECKED_CAST")
            OutputContext(input as In, out as Out).fn()
        }
    }
}
