//file:noinspection unused
package ru.easydata.webfx.app

import com.install4j.api.launcher.SplashScreen
import getl.utils.FileUtils
import getl.utils.MapUtils
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Screen
import javafx.stage.Stage
import ru.easydata.webfx.config.ConfigManager
import ru.easydata.webfx.controllers.MainController

class WebFxApplication extends Application {
    static void main(String[] args) {
        Map<String, Object> mainArguments = null
        if (args != null && args.length > 0)
            mainArguments = MapUtils.ProcessArguments(args)

        def titleMainWindow = System.getProperty('APP_TITLE')?:'EasyData WebFx Browser'
        def userDirPath = FileUtils.ConvertToUnixPath((System.getProperty('USER_DIR') != null)?
                System.getProperty('USER_DIR'):System.getProperty("user.home") + "/easydata/webfxapplication")

        Image mainIcon
        try (def iconStream = this.getResourceAsStream("/icons/icon.png")) {
            mainIcon = new Image(iconStream)
        }

        SplashScreen.writeMessage('Load configuration ...')
        ConfigManager.config.init(userDirPath, titleMainWindow, mainIcon, mainArguments)
        SplashScreen.writeMessage('Launch application ...')

        launch(this, args)
    }

    @Override
    void start(Stage stage) {
        def controller = new MainController(stage)
        def loader = new FXMLLoader()
        def url = ConfigManager.getResource('/fxml/main.fxml')
        loader.setLocation(url)
        loader.controller = controller
        def root = loader.load() as Parent
        controller.initFavorites()

        stage.tap {
            setTitle(ConfigManager.config.titleMainWindow)
            icons.add(ConfigManager.config.mainIcon)

            scene = new Scene(root)

            minWidth = 620
            minHeight = 460

            def screenBounds = Screen.getPrimary().getBounds()

            width = screenBounds.width - 10
            height = screenBounds.height - 100
            centerOnScreen()

            ConfigManager.config.monitorWindow('MainWindow', stage)

            show()
            SplashScreen.hide()
        }
    }

    void stop() {
        ConfigManager.config.doneEngine()
    }
}