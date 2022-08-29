package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.service.BashEscaper

fun safeBashArgument(arg: String): String = BashEscaper.safeBashArgument(arg)
