package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ComputationDescriptions

class NamedComputationBackendDescriptions(name: String) : ComputationDescriptions(name)

class ComputationBackendService {
    // We can validate stuff here
    fun getByName(name: String): NamedComputationBackendDescriptions = NamedComputationBackendDescriptions(name)
}
