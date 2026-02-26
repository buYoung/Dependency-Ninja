package com.github.buyoung.dependencyninja

import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import junit.framework.TestCase.assertEquals
import org.junit.Test

class MyPluginTest {

    @Test
    fun testVersionComparator() {
        assert(VersionComparator.compare("1.2.3", "1.3.0") < 0)
        assert(VersionComparator.compare("2.0.0", "1.9.9") > 0)
        assert(VersionComparator.compare("^1.2.3", "1.2.3") == 0)
    }

    @Test
    fun testRequirementsParser() {
        val text = "requests==2.31.0\nfastapi>=0.115.0"
        val parser = ManifestDependencyParser()
        val dependencies = parser.parse(
            fileName = "requirements.txt",
            text = text,
            manifestPath = "/repo/requirements.txt",
            moduleName = "repo",
        )

        assertEquals(2, dependencies.size)
        assertEquals("requests", dependencies.first().coordinate.name)
        assertEquals("2.31.0", dependencies.first().currentVersion)
    }
}
