package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.db.generateDDL
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ScriptFeatureTest{

    private val serviceDescription = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    @Test
    fun `init test - no args`() {
        val micro = Micro()
        val scriptFeature = ScriptFeature()
        scriptFeature.init(micro, serviceDescription, emptyList())
    }

    @Test
    fun `init test - run script - no scripts`() {
        val micro = Micro()
        val scriptFeature = ScriptFeature()
        scriptFeature.init(micro, serviceDescription, listOf("--run-script"))
    }

    @Test
    fun `init test - run script`() {
        val micro = Micro()
        val scriptFeature = ScriptFeature()
        scriptFeature.init(micro, serviceDescription, listOf("--run-script", "script1", "script2"))
    }

    @Test
    fun `addScriptHandler test`() {
        val scriptFeature = ScriptFeature()
        val scriptHandler = mockk<ScriptHandler>()
        scriptFeature.addScriptHandler("script", scriptHandler)
        //double to hit both branches
        scriptFeature.addScriptHandler("script", scriptHandler)
    }

    @Test
    fun `runscript test`() {
        val micro = initializeMicro(listOf("--run-script", "migrate-db"))
        micro.optionallyAddScriptHandler("migrate-db") {
            println("Running script")
            ScriptHandlerResult.STOP
        }
        micro.runScriptHandler()
    }
}
