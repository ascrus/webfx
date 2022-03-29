//file:noinspection unused
package ru.easydata.webfx.utils

import groovy.transform.CompileStatic

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern

@CompileStatic
class Functions {
    static class TrustAllX509TrustManager extends X509ExtendedTrustManager  {
        @Override
        void checkClientTrusted(X509Certificate[] certs, String authType) {  }
        @Override
        void checkServerTrusted(X509Certificate[] certs, String authType) {  }
        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { }
        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) { }
        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override
        X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0]
        }
    }

    static class HostnameVerifierAll implements HostnameVerifier {
        boolean verify(String string, SSLSession ssl) {
            return true
        }
    }

    static public final TrustManager[] trustManagers = [new TrustAllX509TrustManager()]
    static public final SecureRandom secureRandom = new SecureRandom()
    static public final HostnameVerifierAll hostnameVerifierAll = new HostnameVerifierAll()

    static void DisableSslVerification() {
        SSLContext sc = SSLContext.getInstance('TLS')
        sc.init(null, trustManagers, secureRandom)
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifierAll)
    }

    static Boolean PingHost(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout)
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    static URI Url2UriWithRoot(String url) {
        def u = new URL(url)
        return new URL(u.protocol, u.host, u.port, '/').toURI()
    }

    static Boolean LoadFile(String url) {
        if (url == null)
            throw new NullPointerException("Null url!")

        def res = false
        if (!IsValidUrl(url))
            return res

        def urlObj = new URL(url)
        def con = urlObj.openConnection()
        if (urlObj.protocol == 'https')
            (con as HttpsURLConnection).requestMethod = 'GET'
        else if (urlObj.protocol == 'http')
            (con as HttpURLConnection).requestMethod = 'GET'
        else
            return res

        con.connect()
        def headers = con.headerFields
        def content = headers.get("Content-Disposition") ?: headers.get("content-disposition")
        if (content != null) {
            def elem = content[0]
            if (elem.contains('attachment;')) {
                def fn = elem.split('filename=')
                def fileName = fn[fn.length - 1].replace('"', '')
                res = true
            }
        }

        return res
    }

    static Boolean IsValidUrl(String url) {
        if (url == null)
            throw new NullPointerException("Null url!")

        Boolean res
        try {
            res = (new URL(url) != null)
        }
        catch (Exception ignored) {
            res = false
        }

        return res
    }

    static private final Pattern urlPattern = Pattern.compile('(?i)^(http.*://)(.+)')

    static String Url2TabText(String url) {
        if (url == null)
            throw new NullPointerException("Null url!")

        def matcher = urlPattern.matcher(url)
        if (!matcher.find())
            return null

        def res = matcher.group(2).with {
            def i = it.indexOf('/')
            return (i != -1)?it.substring(0, i):it
        }

        return res
    }
}