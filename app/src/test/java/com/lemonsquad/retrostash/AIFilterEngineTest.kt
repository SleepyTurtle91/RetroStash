package com.lemonsquad.retrostash

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AIFilterEngineTest {

    private val jsonDecoder = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    @Test
    fun testValidAIJsonResponseParsing() {
        val simulatedJsonOutput = """["Game 1.iso", "Game 2.iso", "Game 3.chd"]"""
        
        val parsedList = jsonDecoder.decodeFromString<List<String>>(simulatedJsonOutput)
        
        assertEquals(3, parsedList.size)
        assertEquals("Game 1.iso", parsedList[0])
        assertEquals("Game 3.chd", parsedList[2])
    }

    @Test
    fun testMalformedAIJsonResponseHandling() {
        val structuralTrash = """This is a mistake: ["Game 1.iso"]"""
        
        val parsedList = try {
            jsonDecoder.decodeFromString<List<String>>(structuralTrash)
        } catch (e: Exception) {
            emptyList<String>()
        }
        
        assertEquals(true, parsedList.isEmpty())
    }
}
