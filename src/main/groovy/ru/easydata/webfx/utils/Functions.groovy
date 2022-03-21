package ru.easydata.webfx.utils

import getl.utils.Logs

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

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
        boolean verify(String string, SSLSession ssls) {
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
}
