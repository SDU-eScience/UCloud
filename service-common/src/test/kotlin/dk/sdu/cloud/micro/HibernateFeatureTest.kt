package dk.sdu.cloud.micro

import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HibernateFeatureTest{
    @Test
    fun `test hibernate - test`() {
        val micro = initializeMicro()
        val hibernate =  HibernateFeature()
        hibernate.init(micro, mockk(), emptyList())

        micro.install(HibernateFeature)

        assertEquals("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE", micro.jdbcUrl)
        assertNotNull(micro.hibernateDatabase)
    }

    @Test
    fun `Config test`() {
        val defaultConf = HibernateFeature.Feature.Config()
        assertEquals(HibernateFeature.Feature.Profile.TEST_H2, defaultConf.profile)
        assertNull(defaultConf.hostname)
        assertNull(defaultConf.credentials)
        assertNull(defaultConf.driver)
        assertNull(defaultConf.dialect)
        assertNull(defaultConf.database)
        assertNull(defaultConf.port)
        assertFalse(defaultConf.logSql)

        val setConf = HibernateFeature.Feature.Config(
            profile = HibernateFeature.Feature.Profile.PERSISTENT_POSTGRES,
            hostname = "hostname",
            credentials = HibernateFeature.Feature.Credentials("username", "password"),
            driver = "driver",
            dialect = "dialect",
            database = "database",
            port = 5000,
            logSql = true
        )

        assertEquals(HibernateFeature.Feature.Profile.PERSISTENT_POSTGRES, setConf.profile)
        assertEquals("hostname", setConf.hostname)
        assertEquals("username", setConf.credentials?.username)
        assertEquals("password", setConf.credentials?.password)
        assertEquals("driver", setConf.driver)
        assertEquals("dialect", setConf.dialect)
        assertEquals("database", setConf.database)
        assertEquals(5000, setConf.port)
        assertTrue(setConf.logSql)
    }
}
