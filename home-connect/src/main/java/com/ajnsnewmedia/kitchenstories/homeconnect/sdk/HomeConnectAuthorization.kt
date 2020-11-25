package com.ajnsnewmedia.kitchenstories.homeconnect.sdk

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ajnsnewmedia.kitchenstories.homeconnect.model.auth.HomeConnectClientCredentials
import com.ajnsnewmedia.kitchenstories.homeconnect.model.auth.toHomeConnectAccessToken
import com.ajnsnewmedia.kitchenstories.homeconnect.util.DefaultErrorHandler
import com.ajnsnewmedia.kitchenstories.homeconnect.util.DefaultTimeProvider
import com.ajnsnewmedia.kitchenstories.homeconnect.util.HomeConnectApiFactory
import com.ajnsnewmedia.kitchenstories.homeconnect.util.HomeConnectError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object AuthorizationDependencies {

    lateinit var baseUrl: String
    lateinit var credentials: HomeConnectClientCredentials
    lateinit var homeConnectApiFactory: HomeConnectApiFactory
    lateinit var homeConnectSecretsStore: HomeConnectSecretsStore

}

// TODO move testable code without web view dependency somewhere else and write tests
// TODO make sure that no memory leaks happen here
object HomeConnectAuthorization {

    /**
     * @param onRequestAccessTokenStarted This callback will be triggered when the user has given his consent and the request for the initial
     * access token is ongoing. Use this to e.g. show a loading indicator to keep the user informed about the progress.
     */
    suspend fun authorize(webView: WebView, onRequestAccessTokenStarted: () -> Unit) {
        val authorizationCode = initWebAuthorization(webView)
        loadAccessToken(authorizationCode, onRequestAccessTokenStarted)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun initWebAuthorization(webView: WebView): String = suspendCancellableCoroutine { continuation ->
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.startsWith("https://apiclient.home-connect.com/o2c.html")) {
                    val authorizationCode = Uri.parse(url).parseAuthorizationCode()
                    if (authorizationCode != null) {
                        continuation.resume(authorizationCode)
                    } else {
                        continuation.resumeWithException(HomeConnectError.Unspecified("Couldn't parse authorization code", null))
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // TODO error handling!
            }

        }
        webView.webChromeClient = object : WebChromeClient() {}

        val baseUrl = AuthorizationDependencies.baseUrl
        val credentials = AuthorizationDependencies.credentials
        val authUrl = "${baseUrl}security/oauth/authorize?client_id=${credentials.clientId}&response_type=code"
        webView.loadUrl(authUrl)

        continuation.invokeOnCancellation {
            webView.webChromeClient = null
            webView.stopLoading()
        }
    }

    private suspend fun loadAccessToken(authorizationCode: String, onRequestAccessTokenStarted: () -> Unit) {
        onRequestAccessTokenStarted()
        try {
            val tokenResponse = AuthorizationDependencies.homeConnectApiFactory.getHomeConnectApi().postAuthorizationCode(
                    authorizationCode = authorizationCode,
                    clientId = AuthorizationDependencies.credentials.clientId,
                    clientSecret = AuthorizationDependencies.credentials.clientSecret,
            )
            val currentTimestamp = DefaultTimeProvider().currentTimestamp
            AuthorizationDependencies.homeConnectSecretsStore.accessToken = tokenResponse.toHomeConnectAccessToken(currentTimestamp)
        } catch (e: Throwable) {
            DefaultErrorHandler().handle(e)
        }
    }

    /**
     * Parses the authorization code from the redirection url after the user has logged in and authorized the app
     * example url: //https://apiclient.home-connect.com/o2c.html?code=very_nice_auth_code=3D&grant_type=authorization_code
     */
    private fun Uri.parseAuthorizationCode() = this.getQueryParameter("code")

}