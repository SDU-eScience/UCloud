package dk.sdu.cloud.app.abacus.api

import dk.sdu.cloud.app.api.ComputationDescriptions

// Note: We need to keep this here to ensure that audit topics are still created
object AbacusComputationDescriptions : ComputationDescriptions("abacus")
