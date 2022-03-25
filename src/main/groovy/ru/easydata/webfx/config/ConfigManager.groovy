//file:noinspection unused
package ru.easydata.webfx.config

import getl.config.ConfigSlurper
import getl.utils.BoolUtils
import getl.utils.DateUtils
import getl.utils.FileUtils
import getl.utils.Logs
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.image.Image
import javafx.stage.Stage
import ru.easydata.webfx.cookie.CookieStoreManager
import ru.easydata.webfx.servers.ListServers
import ru.easydata.webfx.utils.Functions

import java.util.concurrent.ConcurrentHashMap

class ConfigManager {
    static public final ConfigManager config = new ConfigManager().tap {
        it.addShutdownHook {
            unregisterLocalServers()
        }
    }

    static {
        Logs.Init()
    }

    void init(String userDirPath, String titleMainWindow, Image mainIcon, Map<String, Object> mainArguments) {
        if (userDirPath == null)
            throw new NullPointerException("Null userDirPath!")
        if (titleMainWindow == null)
            throw new NullPointerException("Null titleMainWindow!")
        if (mainIcon == null)
            throw new NullPointerException("Null mainIcon!")

        this.userDirPath = userDirPath
        this.titleMainWindow = titleMainWindow
        this.mainIcon = mainIcon
        this.mainArguments = mainArguments?:new HashMap<String, Object>()

        FileUtils.ValidFilePath(this.userDirPath)
        FileUtils.ValidFilePath("${this.userDirPath}/userdata")

        this.userDir = new File(userDirPath)
        this.engineConfigFile = new File(userDir.path + '/engine.conf')
        this.favoritesConfigFile = new File(userDir.path + '/favorites.conf')
        this.windowsConfigFile = new File(userDir.path + '/windows.conf')

        Logs.global.logFileName = this.userDirPath + '/log/webfx-{date}.log'
        Logs.Info "*** Start $this.titleMainWindow"
        Logs.Info "User data directory: ${this.userDir.path}"
        Logs.Info "Engine configuration: ${this.engineConfigFile.path}"
        Logs.Info "Favorites configuration: ${this.favoritesConfigFile.path}"

        initEngine()
        loadFavorites()
        loadWindowsParams()

        this.cookieStore = new CookieStoreManager(userDir.path + '/userdata')
        this.cookieManager = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        CookieHandler.setDefault(cookieManager)
    }

    public static final Properties WebFxProperties = new Properties()
    public static final String WebFxVersion
    public static final Date WebFxBuildDate
    static {
        try (def is = this.getResourceAsStream("/webfx.properties")) {
            WebFxProperties.load(is)
            WebFxVersion = WebFxProperties.getProperty('webfx.version')
            WebFxBuildDate = DateUtils.ParseDate(WebFxProperties.getProperty('webfx.builddate'))
        }
    }

    private String userDirPath

    private Map<String, Object> mainArguments
    Map<String, Object> getMainArguments() { mainArguments }

    private File userDir
    File getUserDir() { userDir }
    private File engineConfigFile
    File getEngineConfigFile() { engineConfigFile }
    private File favoritesConfigFile
    File getFavoritesConfigFile() { favoritesConfigFile }
    private File windowsConfigFile
    File getWindowsConfigFile() { windowsConfigFile }

    private Boolean autoStartServers
    Boolean getAutoStartServers() { autoStartServers }

    private Boolean autoStopServers
    Boolean getAutoStopServers() { autoStopServers }

    private Boolean sslVerification
    Boolean getSslVerification() { sslVerification }

    private String titleMainWindow
    String getTitleMainWindow() { titleMainWindow }
    private Image mainIcon
    Image getMainIcon() { mainIcon }

    public final Map<String, Map<String, Map<String, Object>>> favorites = new ConcurrentHashMap<String, Map<String, Map<String, Object>>>()
    public final ListServers localServers = Collections.synchronizedList(new ListServers())
    public final Map<String, Map<String, Object>> windowsParams = new ConcurrentHashMap<String, Map<String, Object>>()

    private CookieStoreManager cookieStore
    CookieStoreManager getCookieStore() { cookieStore }
    private CookieManager cookieManager
    CookieManager getCookieManager() { cookieManager }

    static private final Map<String, Object> defaultEngineOptions = [
            engine: [
                    ssl_verification: false,
                    https: [
                            protocols: 'TLSv1,TLSv1.1,TLSv1.2'
                    ]
            ]
    ]

    Map<String, Map<String, Map<String, Object>>> loadConfigFavorites() {
        if (!favoritesConfigFile.exists())
            return [:] as Map<String, Map<String, Map<String, Object>>>

        return ConfigSlurper.LoadConfigFile(file: favoritesConfigFile) as Map<String, Map<String, Map<String, Object>>>
    }

    void loadFavorites() {
        favorites.clear()
        favorites.putAll(loadConfigFavorites())
        Logs.Info("Loaded ${favorites.size()} favorites")
    }

    void saveFavorites() {
        if (favoritesConfigFile.exists()) {
            FileUtils.CopyToFile(favoritesConfigFile, new File(FileUtils.ExcludeFileExtension(favoritesConfigFile.path) + '.bak'))
        }
        ConfigSlurper.SaveConfigFile(data: favorites, file: favoritesConfigFile)
        Logs.Info("Saved ${favorites.size()} favorites")
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
            localServers.registerLocalServer(name, data.url as String,  data.directory as String, data.command as String, data.command_timeout as Integer,
                    data.shutdown as String, data.shutdown_timeout as Integer, data.encode as String)
        }
        Logs.Info ">>> Application started"
    }

    void doneEngine() {
        try {
            unregisterLocalServers()
        }
        finally {
            cookieStore.close()
            saveWindowsParams()
        }
    }

    void unregisterLocalServers() {
        synchronized (localServers) {
            localServers.each { server ->
                localServers.unregisterLocalServer(server.name)
            }
        }
    }

    void loadWindowsParams() {
        windowsParams.clear()
        if (windowsConfigFile.exists()) {
            windowsParams.putAll(ConfigSlurper.LoadConfigFile(file: windowsConfigFile) as Map<String, Map<String, Object>>)
            Logs.Info("Loaded ${windowsParams.size()} windows description")
        }
    }

    void saveWindowsParams() {
        ConfigSlurper.SaveConfigFile(data: windowsParams, file: windowsConfigFile)
        Logs.Info("Saved ${windowsParams.size()} windows description")
    }

    void monitorWindow(String name, Stage stage) {
        def wp = windowsParams.get(name)
        if (wp == null) {
            wp = [maximized: stage.maximized] as Map<String, Object>
            if (!stage.maximized)
                wp.putAll([x: stage.x, y: stage.y, width: stage.width, height: stage.height])

            windowsParams.put(name, wp)
        }
        else {
            stage.maximized = wp.maximized as Boolean
            if (!stage.maximized) {
                if (wp.containsKey('x'))
                    stage.x = wp.x as Double
                if (wp.containsKey('x'))
                    stage.y = wp.y as Double
                if (stage.resizable) {
                    if (wp.containsKey('width'))
                        stage.width = wp.width as Double
                    if (wp.containsKey('height'))
                        stage.height = wp.height as Double
                }
            }
        }

        stage.xProperty().addListener(new ChangeListener<Number>() {
            @Override
            void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue != null && newValue >= 0 && !stage.maximized)
                    windowsParams.get(name).put('x', newValue)
            }
        })

        stage.yProperty().addListener(new ChangeListener<Number>() {
            @Override
            void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue != null && newValue >= 0 && !stage.maximized)
                    windowsParams.get(name).put('y', newValue)
            }
        })

        stage.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue != null && newValue >= 0 && !stage.maximized)
                    windowsParams.get(name).put('width', newValue)
            }
        })

        stage.heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue != null && newValue >= 0 && !stage.maximized)
                    windowsParams.get(name).put('height', newValue)
            }
        })

        stage.maximizedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue != null)
                    windowsParams.get(name).put('maximized', newValue)
            }
        })
    }
}