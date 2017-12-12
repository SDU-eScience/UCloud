package org.esciencecloud.auth.saml

import com.onelogin.saml2.http.HttpRequest
import com.onelogin.saml2.util.Util
import io.ktor.application.ApplicationCall
import io.ktor.features.origin
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.request.*
import io.ktor.response.respondRedirect
import java.io.IOException
import java.util.*

object KtorUtils {

    /**
     * Creates an HttpRequest from an HttpServletRequest.
     *
     * @param call the incoming HttpServletRequest
     * @return a HttpRequest
     */
    suspend fun makeHttpRequest(call: ApplicationCall): HttpRequest {
        val requestUrl = getSelfURLhost(call) + "/" + call.request.uri.removePrefix("/")
        val queryString = call.request.queryString()
        val paramsAsList = HashMap<String, List<String>>()
        call.parameters.forEach { s, list -> paramsAsList[s] = list }

        val bodyParams = call.receiveOrNull<String>()?.parseUrlEncodedParameters(
                call.request.contentCharset() ?: Charsets.UTF_8
        )
        bodyParams?.forEach { s, list -> paramsAsList[s] = list }
        return HttpRequest(requestUrl, paramsAsList, queryString)
    }

    /**
     * Returns the protocol + the current host + the port (if different than
     * common ports).
     *
     * @param call
     * HttpServletRequest object to be processed
     *
     * @return the HOST URL
     */
    private fun getSelfURLhost(call: ApplicationCall): String {
        val serverPort = call.request.port()
        val scheme = call.request.origin.scheme
        val name = call.request.origin.host
        return if (serverPort == 80 || serverPort == 443 || serverPort == 0) {
            String.format("%s://%s", scheme, name)
        } else {
            String.format("%s://%s:%s", scheme, name, serverPort)
        }
    }

    /**
     * Returns the routed URL of the current host + current view.
     *
     * @param call
     * HttpServletRequest object to be processed
     *
     * @return the current routed url
     */
    fun getSelfRoutedURLNoQuery(call: ApplicationCall): String {
        var url = getSelfURLhost(call)

        val requestUri = call.request.uri

        if (!requestUri.isEmpty()) {
            url += requestUri
        }
        return url
    }

    /**
     * Redirect to location url
     *
     * @param response
     * HttpServletResponse object to be used
     * @param location
     * target location url
     * @param parameters
     * GET parameters to be added
     * @param stay
     * True if we want to stay (returns the url string) False to execute redirection
     *
     * @return string the target URL
     * @throws IOException
     */
    @Throws(IOException::class)
    suspend fun sendRedirect(
            response: ApplicationCall,
            location: String,
            parameters: Map<String, String> = emptyMap(),
            stay: Boolean = false
    ): String {
        var target = location

        if (!parameters.isEmpty()) {
            var first = !location.contains("?")
            for ((key, value) in parameters) {
                if (first) {
                    target += "?"
                    first = false
                } else {
                    target += "&"
                }
                target += key
                if (!value.isEmpty()) {
                    target += "=" + Util.urlEncoder(value)
                }
            }
        }
        if (!stay) {
            response.respondRedirect(target)
        }

        return target
    }


}