//file:noinspection unused
package ru.easydata.webfx.utils

import getl.utils.FileUtils
import getl.utils.StringUtils
import groovy.transform.CompileStatic
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import javafx.stage.WindowEvent
import ru.easydata.webfx.config.ConfigManager
import ru.easydata.webfx.controllers.DownloadController
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

    static String CheckAttachmentFile(String url, String requestMethod = 'GET') {
        if (url == null)
            throw new NullPointerException("Null url!")
        if (requestMethod == null)
            throw new NullPointerException("Null requestMethod!")

        if (!IsValidUrl(url))
            return null

        def urlObj = new URL(url)
        def con = urlObj.openConnection()
        if (urlObj.protocol == 'https')
            (con as HttpsURLConnection).requestMethod = requestMethod
        else if (urlObj.protocol == 'http')
            (con as HttpURLConnection).requestMethod = requestMethod
        else
            return null

        String res = null

        con.connect()
        try {
            def headers = con.headerFields
            def content = headers.get('Content-Disposition') ?: headers.get('content-disposition')
            if (content != null) {
                def elem = content[0]
                if (elem.contains('attachment;')) {
                    def fn = elem.split('filename=')
                    res = fn[fn.length - 1].replace('"', '').trim()
                }
            } else {
                content = headers.get('Content-Type') ?: headers.get('content-type')
                if (content != null && content[0] == 'application/octet-stream') {
                    Platform.runLater(new Runnable() {
                        @Override
                        void run() {
                            def alert = new Alert(Alert.AlertType.ERROR, 'Multi-part download not supported!')
                            alert.title = 'Download error'
                            alert.showAndWait()
                        }
                    })
                }
            }
        }
        finally {
            if (urlObj.protocol == 'https')
                (con as HttpsURLConnection).disconnect()
            else if (urlObj.protocol == 'http')
                (con as HttpURLConnection).disconnect()
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

    static Long DetectAttachmentSize(URL url) {
        if (url == null)
            throw new NullPointerException("Null url!")

        if (!(url.protocol in ['http', 'https']))
            throw new Exception("Invalid protocol \"${url.protocol}\"!")

        Long res = null
        if (url.protocol == 'http') {
            def con = url.openConnection() as HttpURLConnection
            try {
                con.setRequestMethod("HEAD")
                res = con.getContentLengthLong()
            }
            finally {
                con.disconnect()
            }
        }
        else {
            def con = url.openConnection() as HttpsURLConnection
            try {
                con.setRequestMethod("HEAD")
                res = con.getContentLengthLong()
            }
            finally {
                con.disconnect()
            }
        }

        return res
    }

    static void LoadFile(String url, String fileName, Window owner) {
        def uri = Url2UriWithRoot(url)
        def rootUrl = uri.toString()
        def defaultDir = ConfigManager.config.downloadsParams.get(rootUrl)

        def fs = new FileChooser()
        if (defaultDir != null)
            fs.initialDirectory = new File(defaultDir)
        fs.initialFileName = fileName
        def ext = FileUtils.FileExtension(fileName)
        fs.extensionFilters.add(new FileChooser.ExtensionFilter(StringUtils.ToCamelCase(ext) + ' file', "*.$ext"))
        fs.extensionFilters.add(new FileChooser.ExtensionFilter('All files', '*.*'))
        def file = fs.showSaveDialog(owner)
        if (file == null)
            return
        if (!file.exists())
            file.parentFile.mkdirs()

        ConfigManager.config.downloadsParams.put(rootUrl, file.parent)

        def urlObj = new URL(url)
        def fileSize = DetectAttachmentSize(urlObj)
        def percentFile = fileSize.intdiv(100)

        def con = urlObj.openConnection()
        if (urlObj.protocol == 'https')
            (con as HttpsURLConnection).requestMethod = 'GET'
        else
            (con as HttpURLConnection).requestMethod = 'GET'

        con.connect()
        try {
            def controller = new DownloadController()
            def loader = new FXMLLoader()
            def fxml = ConfigManager.getResource('/fxml/download.fxml')
            loader.setLocation(fxml)
            loader.controller = controller
            def root = loader.load() as Parent

            def stage = new Stage(StageStyle.UTILITY)
            stage.title = "Downloading file"
            stage.initModality(Modality.APPLICATION_MODAL)
            stage.scene = new Scene(root)
            controller.labelFileName.text = 'File: ' + file.path
            controller.labelFileSize.text = 'Size: ' + FileUtils.SizeBytes(fileSize)
            controller.progressBar.progress = 0

            stage.onCloseRequest = new EventHandler<WindowEvent>() {
                @Override
                void handle(WindowEvent event) {
                    event.consume()
                }
            }

            def allowDownload = true
            def isError = false

            controller.buttonCancel.onAction = new EventHandler<ActionEvent>() {
                @Override
                void handle(ActionEvent event) {
                    def dialog = new Alert(Alert.AlertType.CONFIRMATION)
                    (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
                    dialog.title = 'Confirm'
                    dialog.headerText = "Cancel download file"
                    dialog.contentText = 'You are sure?'
                    dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
                    def res = dialog.showAndWait()
                    if (res.get() == ButtonType.YES)
                        allowDownload = false
                }
            }
            controller.buttonClose.onAction = new EventHandler<ActionEvent>() {
                @Override
                void handle(ActionEvent event) {
                    stage.close()
                }
            }

            def task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    def curPercent = 0
                    try (def is = con.getInputStream(); def os = new FileOutputStream(file)) {
                        int size = 0
                        int len = 0
                        byte[] buf = new byte[4096]
                        while (allowDownload && (size = is.read(buf)) != -1) {
                            len += size
                            os.write(buf, 0, size)
                            def newPercent = len.intdiv(percentFile)
                            if (newPercent > curPercent) {
                                updateProgress(newPercent, 100)
                                updateTitle("Downloading file ${newPercent}%")
                                curPercent = newPercent
                            }
                        }
                    }
                    catch (Exception e) {
                        isError = true
                        throw e
                    }
                    finally {
                        controller.buttonClose.disable = false
                        controller.buttonCancel.disable = true
                        if (isError)
                            updateTitle("Download file error")
                        else if (allowDownload)
                            updateTitle("Download file complete")
                        else
                            updateTitle("Download file cancel")
                    }
                }
            }

            stage.titleProperty().bind(task.titleProperty())
            controller.progressBar.progressProperty().bind(task.progressProperty())
            new Thread(task).start()

            stage.showAndWait()
            if (!allowDownload || isError)
                file.delete()
        }
        finally {
            if (urlObj.protocol == 'https')
                (con as HttpsURLConnection).disconnect()
            else
                (con as HttpURLConnection).disconnect()
        }
    }
}