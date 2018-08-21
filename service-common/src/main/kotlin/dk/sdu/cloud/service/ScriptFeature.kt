package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription

typealias ScriptHandler = () -> ScriptHandlerResult

class ScriptFeature : MicroFeature {
    private val scriptsToRun = ArrayList<String>()
    private val handlers = HashMap<String, MutableList<ScriptHandler>>()

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val iterator = cliArgs.iterator()
        while (iterator.hasNext()) {
            val arg = iterator.next()
            if (arg == RUN_SCRIPT_ARG) {
                val scriptName = if (iterator.hasNext()) iterator.next() else null
                if (scriptName == null) {
                    log.info("Dangling $RUN_SCRIPT_ARG. Correct syntax is $RUN_SCRIPT_ARG <scriptName>")
                } else {
                    scriptsToRun.add(scriptName)
                }
            }
        }

        ctx.scriptsToRun = scriptsToRun
    }

    fun addScriptHandler(scriptName: String, handler: ScriptHandler) {
        val list = handlers[scriptName] ?: arrayListOf()
        list.add(handler)
        handlers[scriptName] = list
    }

    fun runScripts(): Boolean {
        var found = false
        scriptsToRun.forEach { scriptName ->
            val handlersForScript = handlers[scriptName] ?: arrayListOf()
            handlerLoop@ for (scriptHandler in handlersForScript) {
                log.debug("Invoking handler for '$scriptName' ($scriptHandler)")
                found = true
                val result = scriptHandler()
                when (result) {
                    ScriptHandlerResult.CONTINUE -> continue@handlerLoop
                    ScriptHandlerResult.STOP -> {
                        log.debug("Breaking in '$scriptName'")
                        break@handlerLoop
                    }
                }
            }
        }
        return found
    }

    companion object Feature : MicroFeatureFactory<ScriptFeature, Unit>, Loggable {
        override val key = MicroAttributeKey<ScriptFeature>("script-feature")
        override fun create(config: Unit): ScriptFeature = ScriptFeature()

        override val log = logger()

        internal val SCRIPTS_TO_RUN_KEY = MicroAttributeKey<List<String>>("scripts-to-run")

        const val RUN_SCRIPT_ARG = "--run-script"
    }
}

enum class ScriptHandlerResult {
    CONTINUE,
    STOP
}

var Micro.scriptsToRun: List<String>
    get() = attributes.getOrNull(ScriptFeature.SCRIPTS_TO_RUN_KEY) ?: emptyList()
    set(value) {
        attributes[ScriptFeature.SCRIPTS_TO_RUN_KEY] = value
    }

fun Micro.optionallyAddScriptHandler(scriptName: String, handler: ScriptHandler) {
    featureOrNull(ScriptFeature)?.addScriptHandler(scriptName, handler)
}

fun Micro.runScriptHandler(): Boolean {
    val feature = featureOrNull(ScriptFeature) ?:
        throw IllegalStateException("Missing ScriptFeature. You should install() it")
    return feature.runScripts()
}