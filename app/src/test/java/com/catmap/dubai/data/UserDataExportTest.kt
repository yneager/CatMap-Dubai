package com.catmap.dubai.data

import org.junit.Assert.*
import org.junit.Test

class UserDataExportTest {
    @Test fun encodesOwnershipVersionAndSpecialCharacters() {
        val result=UserDataExport.build("me",123,mapOf("posts" to listOf(mapOf("name" to "A\"B\nC","reward" to 5,"active" to true))))
        assertTrue(result.contains("\"userId\":\"me\""))
        assertTrue(result.contains("A\\\"B\\nC"))
        assertTrue(result.contains("\"schemaVersion\":1"))
    }
    @Test fun nonFiniteNumbersProduceValidJsonNull() { assertTrue(UserDataExport.build("me",1,mapOf("posts" to listOf(mapOf("bad" to Double.NaN)))).contains("\"bad\":null")) }
}
