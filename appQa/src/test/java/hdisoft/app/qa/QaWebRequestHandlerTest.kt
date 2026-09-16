package hdisoft.app.qa

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaWebRequestHandlerTest {
    @Test
    fun stepsEndpointIncludesNewRegisteredCoreStep() {
        val output = ByteArrayOutputStream()

        val handled = QaWebRequestHandler.handleRequest(
            "GET", "/api/qa/steps", emptyMap(), "", output
        )
        val response = output.toString(Charsets.UTF_8.name())

        assertTrue(handled)
        assertTrue(response.contains("HTTP/1.1 200 OK"))
        assertTrue(response.contains("\"name\":\"wait\""))
        assertTrue(response.contains("\"category\":\"core\""))
    }

    @Test
    fun unrelatedEndpointIsLeftForTheSharedWebServer() {
        val handled = QaWebRequestHandler.handleRequest(
            "GET", "/api/qa/scripts", emptyMap(), "", ByteArrayOutputStream()
        )

        assertFalse(handled)
    }
}
