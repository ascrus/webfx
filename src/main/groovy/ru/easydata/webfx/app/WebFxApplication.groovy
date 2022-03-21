//file:noinspection unused
package ru.easydata.webfx.app

import getl.config.ConfigSlurper
import getl.utils.BoolUtils
import getl.utils.FileUtils
import getl.utils.Logs
import getl.utils.MapUtils
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import ru.easydata.webfx.controllers.MainController
import ru.easydata.webfx.exception.LocalServerAlreadyRegister
import ru.easydata.webfx.exception.LocalServerUnknown
import ru.easydata.webfx.servers.LocalServer
import ru.easydata.webfx.utils.Functions

class WebFxApplication extends Application {
    @SuppressWarnings('SpellCheckingInspection')
    static void main(String[] args) {
        Logs.Init()

        if (args != null && args.length > 0)
            mainArguments.putAll(MapUtils.ProcessArguments(args))

        titleMainWindow = System.getProperty('APP_TITLE')?:'EasyData WebFx Browser'
        userDirPath = FileUtils.ConvertToUnixPath((System.getProperty('USER_DIR') != null)?
                System.getProperty('USER_DIR'):System.getProperty("user.home") + "/easydata/webfxapplication")

        FileUtils.ValidFilePath(userDirPath)
        FileUtils.ValidFilePath("$userDirPath/userdata")

        Logs.global.logFileName = userDirPath + '/log/easyworkspace-{date}.log'
        Logs.Info "*** Start $titleMainWindow"
        Logs.Info "User data directory: $userDirPath"

        launch(this, args)
    }

    static public final Map<String, Object> mainArguments = [:] as Map<String, Object>

    static public String titleMainWindow
    static public final Image mainIcon
    static public String userDirPath

    static {
        try (def iconStream = this.getResourceAsStream("/icons/icon.png")) {
            mainIcon = new Image(iconStream)
        }
    }

    public Stage mainWindow
    public final File userDir = new File(userDirPath)
    public final File engineConfigFile = new File(userDir.path + '/engine.conf')

    public final File favoritesConfigFile = new File(userDir.path + '/favorites.conf')
    private final MainController controller = new MainController(this)

    private Boolean autoStartServers
    Boolean getAutoStartServers() { autoStartServers }

    private Boolean autoStopServers
    Boolean getAutoStopServers() { autoStopServers }

    private Boolean sslVerification
    Boolean getSslVerification() { sslVerification }

    public final Map<String, Map<String, Map<String, Object>>> favorites = [:] as Map<String, Map<String, Map<String, Object>>>

    Map<String, Map<String, Map<String, Object>>> loadConfigFavorites() {
        if (!favoritesConfigFile.exists())
            return [:] as Map<String, Map<String, Map<String, Object>>>

        return ConfigSlurper.LoadConfigFile(file: favoritesConfigFile) as Map<String, Map<String, Map<String, Object>>>
    }

    void saveConfigFavorites() {
        if (favoritesConfigFile.exists()) {
            FileUtils.CopyToFile(favoritesConfigFile, new File(FileUtils.ExcludeFileExtension(favoritesConfigFile.path) + '.bak'))
        }
        ConfigSlurper.SaveConfigFile(data: favorites, file: favoritesConfigFile)
    }

    static private final Map<String, Object> defaultEngineOptions = [
            engine: [
                    ssl_verification: false,
                    https: [
                            protocols: 'TLSv1,TLSv1.1,TLSv1.2'
                    ]
            ]
    ]

    public final List<LocalServer> localServers = new LinkedList<LocalServer>()

    LocalServer findServerByName(String name) {
        if (name == null)
            throw new NullPointerException("Null name!")

        name = name.trim().toLowerCase()
        localServers.find { it.name.toLowerCase() == name }
    }

    LocalServer findServerByUri(URI uri) {
        if (uri == null)
            throw new NullPointerException("Null uri!")

        localServers.find { (it.uri == uri) }
    }

    LocalServer findServerByUrl(String url) {
        if (url == null)
            throw new NullPointerException("Null url!")

        return findServerByUri(Functions.Url2UriWithRoot(url))
    }


    void registerLocalServer(String name, String url, String directory, String command, Integer commandTimeout,
                             String shutdownService, Integer shutdownTimeout, String encode) {
        if (name == null || name.length() == 0)
            throw new NullPointerException("Null name!")

        if (url == null || url.length() == 0)
            throw new NullPointerException("Null url!")

        if (command == null || command.length() == 0)
            throw new NullPointerException("Null command!")

        if (findServerByName(name) != null || findServerByUrl(url) != null)
            throw new LocalServerAlreadyRegister(name, url)

        localServers.add(new LocalServer(this, name, url, directory, command, commandTimeout, shutdownService, shutdownTimeout, encode))
        Logs.Info "Registered local server $name with url $url"
    }

    void unregisterLocalServer(String name) {
        if (name == null || name.length() == 0)
            throw new NullPointerException("Null name!")

        def server = findServerByName(name)
        if (server == null)
            throw new LocalServerUnknown(name)

        if (!server.isStop) {
            server.stop()
        }

        localServers.remove(server)
        Logs.Info "Unregistered local server $name with ${server.uri.toString()}"
    }

    void stopLocalServer(String name) {
        if (name == null || name.length() == 0)
            throw new NullPointerException("Null name!")

        def server = findServerByUrl(name)
        if (server == null)
            throw new LocalServerUnknown(name)

        if (!server.isStop)
            server.stop()
    }

    void initEngine() {
        def fileData = (engineConfigFile.exists())?ConfigSlurper.LoadConfigFile(file: engineConfigFile):defaultEngineOptions
        def engConfig = (fileData.engine as Map<String, Object>)?:[:] as Map<String, Object>

        sslVerification = BoolUtils.IsValue(engConfig.ssl_verification)
        Logs.Info "SSL verification: $sslVerification"

        autoStartServers = BoolUtils.IsValue(engConfig.autostart_servers)
        Logs.Info "Auto start local servers: $autoStartServers"

        autoStopServers = BoolUtils.IsValue(engConfig.autostop_servers)
        Logs.Info "Auto stop local servers: $autoStopServers"

        def httpsConfig = (engConfig.https as Map<String, Object>)?:[:] as Map<String, Object>
        httpsConfig.each { key, value ->
            Logs.Info "https.${key} = $value"
            System.setProperty("https.$key".toString(), value.toString())
        }

        def httpConfig = (engConfig.http as Map<String, Object>)?:[:] as Map<String, Object>
        httpConfig.each { key, value ->
            Logs.Info "http.${key} = $value"
            System.setProperty("http.$key".toString(), value.toString())
        }

        if (!sslVerification)
            Functions.DisableSslVerification()

        def serversConfig = (fileData.servers as Map<String, Map<String, Object>>)?:[:] as Map<String, Map<String, Object>>
        serversConfig.each { name, data ->
            registerLocalServer(name, data.url as String,  data.directory as String, data.command as String, data.command_timeout as Integer,
                    data.shutdown as String, data.shutdown_timeout as Integer, data.encode as String)
        }
        Logs.Info ">>> Application started"
    }

    void unregisterLocalServers() {
        synchronized (localServers) {
            localServers.each { server ->
                unregisterLocalServer(server.name)
            }
        }
    }

    @Override
    void start(Stage stage) {
        initEngine()
        favorites.putAll(loadConfigFavorites())

        def loader = new FXMLLoader()
        def url = getClass().getResource('/fxml/main.fxml')
        loader.setLocation(url)
        loader.controller = controller
        def root = loader.load() as Parent

        controller.initFavorites()

        stage.tap {
            addShutdownHook {
                unregisterLocalServers()
            }

            setTitle(titleMainWindow)
            icons.add(mainIcon)

            scene = new Scene(root)
            stage.maximized = true
            stage.width = 600
            stage.height = 400
            stage.show()
        }

        mainWindow = stage
    }

    void stop() {
        unregisterLocalServers()
    }
}