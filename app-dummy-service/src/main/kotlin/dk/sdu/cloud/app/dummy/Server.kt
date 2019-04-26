package dk.sdu.cloud.app.dummy

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.app.dummy.rpc.*
import dk.sdu.cloud.app.dummy.services.ControlService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.HttpCall
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.Executors
import javax.swing.JFrame
import javax.swing.SwingUtilities

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        lateinit var controlService: ControlService

        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        JFrame("Control Panel").apply {
            val form = ControlForm()
            add(form.panel)
            setSize(800, 450)
            isVisible = true
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE

            form.outputArea.isEditable = false

            val executor = Executors.newSingleThreadExecutor()
            controlService = ControlService(
                authenticatedClient,
                println = {
                    SwingUtilities.invokeLater {
                        form.outputArea.append(it + "\n")
                        form.outputArea.text = form.outputArea.text.lines().takeLast(25).joinToString("\n")
                    }
                }
            )

            form.inputField.addKeyListener(object : KeyAdapter() {
                override fun keyTyped(e: KeyEvent) {
                    if (e.keyChar == '\n') {
                        val text = form.inputField.text.trim()
                        executor.submit { controlService.onInput(text) }
                        form.inputField.text = ""
                    }
                }
            })

            form.inputField.requestFocus()
        }

        with(micro.server) {
            configureControllers(
                ComputeController(controlService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
