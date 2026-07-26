package app.utillock.android.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    @Test
    fun matchesExactAndSubdomainWithoutFalseSuffix() {
        val blocked = setOf("example.com")
        assertTrue(DomainMatcher.matches("https://example.com/path", blocked, emptySet()))
        assertTrue(DomainMatcher.matches("m.example.com", blocked, emptySet()))
        assertFalse(DomainMatcher.matches("notexample.com", blocked, emptySet()))
    }
}

