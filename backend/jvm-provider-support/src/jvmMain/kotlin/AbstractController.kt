package dk.sdu.cloud

import org.springframework.web.bind.annotation.GetMapping

abstract class Testing {
    @GetMapping("/fie")
    abstract fun retrieveTest(): String
}
