package dk.sdu.cloud.client

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.SecurityScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityScopeTest {
    @Test
    fun `Security scope - one to one`() {
        val scope = SecurityScope.construct(listOf("a", "b", "c"), AccessRight.READ)
        assertTrue(scope.isCoveredBy(scope))
    }

    @Test
    fun `Security scope - same segments, higher rights`() {
        val segments = listOf("a", "b", "c")
        val scopeToCover = SecurityScope.construct(segments, AccessRight.READ)
        val coveredBy = SecurityScope.construct(segments, AccessRight.READ_WRITE)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - same segments, lower rights`() {
        val segments = listOf("a", "b", "c")
        val scopeToCover = SecurityScope.construct(segments, AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(segments, AccessRight.READ)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - hierarchy, same rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b", "c"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf("a"), AccessRight.READ)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - hierarchy, higher rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b", "c"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf("a"), AccessRight.READ_WRITE)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - hierarchy, lower rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b", "c"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf("a"), AccessRight.READ)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - all scope, same rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - all scope, higher rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ_WRITE)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - all scope, same write rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a", "b"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ_WRITE)
        assertTrue(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - all scope does not cover special, same rights`() {
        val scopeToCover = SecurityScope.construct(listOf(SecurityScope.SPECIAL_SCOPE, "b"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ_WRITE)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - all scope does not cover special, higher rights`() {
        val scopeToCover = SecurityScope.construct(listOf(SecurityScope.SPECIAL_SCOPE, "b"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ_WRITE)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }


    @Test
    fun `Security scope - all scope does not cover special, lower rights`() {
        val scopeToCover = SecurityScope.construct(listOf(SecurityScope.SPECIAL_SCOPE, "b"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf(SecurityScope.ALL_SCOPE), AccessRight.READ)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - more specific should not cover less specific, same rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf("a", "b"), AccessRight.READ_WRITE)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - more specific should not cover less specific, higher rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a"), AccessRight.READ_WRITE)
        val coveredBy = SecurityScope.construct(listOf("a", "b"), AccessRight.READ)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }

    @Test
    fun `Security scope - more specific should not cover less specific, lower rights`() {
        val scopeToCover = SecurityScope.construct(listOf("a"), AccessRight.READ)
        val coveredBy = SecurityScope.construct(listOf("a", "b"), AccessRight.READ_WRITE)
        assertFalse(scopeToCover.isCoveredBy(coveredBy))
    }
}