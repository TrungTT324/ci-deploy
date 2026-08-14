package hdisoft.app.qa

import hdisoft.app.qa.model.ActionModel
import hdisoft.app.webserver.SimpleHttpServer
import java.io.OutputStream

/** appQa-specific HTTP endpoints that are available whenever its web server is running. */
object QaWebRequestHandler : SimpleHttpServer.RequestHandler {
    override fun handleRequest(
        method: String,
        path: String,
        queryMap: Map<String, String>,
        body: String,
        output: OutputStream
    ): Boolean {
        if (method != "GET" || path != "/api/qa/steps") return false

        SimpleHttpServer.sendHttpResponse(
            output,
            "HTTP/1.1 200 OK",
            "application/json",
            ActionModel.definitionsToJson().toByteArray()
        )
        return true
    }
}
