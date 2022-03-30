//file:noinspection unused
package ru.easydata.webfx.controllers

import getl.proc.Executor
import getl.utils.DateUtils
import getl.utils.Logs
import groovy.transform.Synchronized
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.concurrent.Worker
import javafx.event.ActionEvent
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ListView
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TextInputDialog
import javafx.scene.control.Tooltip
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.PopupFeatures
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.stage.WindowEvent
import javafx.util.Callback
import ru.easydata.webfx.config.ConfigManager
import ru.easydata.webfx.utils.Functions
import java.util.concurrent.ConcurrentLinkedDeque

class MainController {
    MainController(Stage mainWindow) {
        this.mainWindow = mainWindow
        mainWindow.onCloseRequest = new EventHandler<WindowEvent>() {
            @Override
            void handle(WindowEvent event) {
                def dialog = new Alert(Alert.AlertType.CONFIRMATION)
                (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
                dialog.title = 'WebFx Warning'
                dialog.headerText = 'Closing program'
                dialog.contentText = 'You are sure?'
                dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
                def res = dialog.showAndWait()
                if (res.get() != ButtonType.YES)
                    event.consume()
            }
        }
    }

    static {
        Logs.Init()
    }

    private Stage mainWindow

    @FXML
    Menu menuFavorites

    @FXML
    TabPane tabPages

    @FXML
    void quit() {
        def dialog = new Alert(Alert.AlertType.CONFIRMATION)
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'WebFx Warning'
        dialog.headerText = 'Closing program'
        dialog.contentText = 'You are sure?'
        dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
        def res = dialog.showAndWait()
        if (res.get() == ButtonType.YES)
            mainWindow.close()
    }

    Tab getCurrentTab() { tabPages.selectionModel.selectedItem }

    WebView getCurrentWebView() {
        def tab = currentTab
        if (tab == null)
            return null
        return (tab.content as VBox).children[0] as WebView
    }

    @FXML
    void openSite() {
        def dialog = new TextInputDialog()
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'Open new page'
        dialog.headerText = 'Enter https or http url (default https)'
        Optional<String> res = dialog.showAndWait()
        if (res.present)
            loadPage(res.get())
    }

    static String tabId(String groupName, String name) {
        if (groupName == null || name == null)
            return null

        return 'tab_' + groupName + '/' + name
    }

    static String menuId(String groupName, String name) {
        return 'menu_' + groupName + '/' + name
    }

    Tab findTab(String id) {
        for (t in tabPages.tabs) {
            if (t.id == id)
                return t
        }

        return null
    }

    void loadPage(MenuItem element) {
        def url = (element.userData as Map<String, Object>)?.url as String
        if (url != null)
            loadPage(url, element.parentMenu.text, element.text)
    }

    void initFavorites() {
        ConfigManager.config.favorites.each { groupName, groupElements ->
            def menu = new Menu(groupName).tap { mnemonicParsing = false }
            groupElements.each { elementName, data ->
                def item = new MenuItem(elementName).tap { el ->
                    mnemonicParsing = false
                    el.id = menuId(groupName, elementName)
                    el.userData = [url: data.url]
                    onAction = favoriteMenuAction
                }
                menu.items.add(item)
            }
            menu.items.sort(true)  { a, b -> a.text <=> b.text }
            menuFavorites.items.add(menu)
        }
        menuFavorites.items.sort(true) { a, b -> a.text <=> b.text }
    }

    @Synchronized
    Tab createTab(String url, String groupName, String tabText, Tab owner = null) {
        def pane = new VBox()
        pane.setPadding(new Insets(5, 5, 5, 5))
        def tab = new Tab('[' + tabText + '] loading ...', pane)
        tab.closable = true
        tabPages.tabs.add(tab)
        tabPages.selectionModel.select(tab)

        tab.userData = [url: url, groupName: groupName, tabText: tabText, owner: owner, pages: (owner == null)?[] as List<Tab>:null]
        if (owner != null)
            ((owner.userData as Map<String, Object>).pages as List<Tab>).add(tab)

        if (owner == null) {
            tab.onCloseRequest  = new EventHandler<Event>() {
                public Tab usedTab

                @Override
                void handle(Event event)
                {
                    def dialog = new Alert(Alert.AlertType.CONFIRMATION)
                    (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
                    dialog.title = 'Confirm'
                    dialog.headerText = "Closing tab \"${tab.text}\""
                    dialog.contentText = 'You are sure?'
                    dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
                    def res = dialog.showAndWait()
                    if (res.get() != ButtonType.YES)
                        event.consume()
                }
            }.tap { usedTab = tab }
        }

        tab.onClosed = new EventHandler<Event>() {
            public Tab usedTab

            @Override
            void handle(Event event) {
                def ud = (usedTab.userData as Map<String, Object>)

                if (ud.owner == null) {
                    def pages = (ud.pages as List<Tab>).collect()
                    pages.each { tabPages.tabs.remove(it) }

                    def server = ConfigManager.config.localServers.findServerByUrl(url)
                    if (server != null) {
                        server.removeTab(usedTab)
                        Logs.Info "Closed $url"
                    }
                }
                else {
                    (((ud.owner as Tab).userData as Map<String, Object>).pages as List<Tab>).remove(usedTab)
                }
            }
        }.tap { usedTab = tab }

        return tab
    }

    WebView createWebView(Tab tab) {
        def ud = tab.userData as Map<String, Object>
        def url = ud.url as String
        def groupName = ud.groupName as String
        def tabText = ud.tabText as String
        def owner = ud.owner as Tab

        def res = new WebView()
        ud.webView = res

        res.engine.tap { webEng ->
            userDataDirectory = new File(ConfigManager.config.userDir.path + '/userdata')
            javaScriptEnabled = true
            userAgent = 'WebFx Browser 1.0'

            createPopupHandler = new Callback<PopupFeatures, WebEngine>() {
                @Override
                WebEngine call(PopupFeatures param) {

                    def newTab = createTab(url, groupName, tabText, owner ?: tab)
                    WebView newWebView = createWebView(newTab)
                    VBox.setVgrow(newWebView, Priority.ALWAYS)
                    (newTab.content as VBox).children.add(newWebView)

                    return newWebView.engine
                }
            }

            titleProperty().addListener(new ChangeListener<String>() {
                public String usedTabText

                @Override
                void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    if (newValue == null)
                        return

                    def str = "[$tabText]: " + (newValue?:oldValue?:'')
                    if (str.length() > 60) {
                        tab.tooltip = new Tooltip(str)
                        str = str.substring(0, 57) + ' ...'
                    }
                    tab.text = str
                }
            }.tap { usedTabText = tabText })

            loadWorker.stateProperty().addListener(new ChangeListener<Worker.State>() {
                public WebEngine engine
                public Tab curTab
                private URI currentURI
                private Boolean isLoadFile = false
                private Boolean isInit = true

                @Override
                void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                    switch (newValue) {
                        case Worker.State.SCHEDULED:
                            if (currentURI != null) {
                                ConfigManager.config.cookieStore.removeFromUri(currentURI, true) { u, c ->
                                    c.version > 0
                                }
                            }

                            def curUrl = engine.location
                            if (!isLoadFile && curUrl != null && curUrl.length() > 0) {
                                def fileName = Functions.CheckAttachmentFile(curUrl)
                                if (fileName != null) {
                                    isLoadFile = true
                                    Platform.runLater(new Runnable() {
                                        @Override
                                        void run() {
                                            try {
                                                Functions.LoadFile(curUrl, fileName, mainWindow)
                                            }
                                            finally {
                                                isLoadFile = false
                                                if (isInit)
                                                    tabPages.tabs.remove(curTab)
                                            }
                                        }
                                    })
                                }
                            }
                            break
                        case Worker.State.FAILED:
                            isInit = false
                            currentURI = null
                            engine.loadContent('Load error: ' + engine.loadWorker.exception.message, 'text/html')
                            break
                        case Worker.State.CANCELLED:
                            currentURI = null
                            break
                        case Worker.State.SUCCEEDED:
                            isInit = false
                            if (engine.location != null && engine.location.length() > 0 && Functions.IsValidUrl(engine.location)) {
                                def u = new URL(engine.location)
                                currentURI = new URI(u.protocol + '://' + u.authority)
                            }
                            else
                                currentURI = null
                            break
                    }
                }
            }.tap { engine = webEng; curTab = tab })
        }

        return res
    }

    void loadPage(String url, String groupName = null, String pageName = null) {
        def pageId = tabId(groupName, pageName)
        if (pageId != null) {
            def t = findTab(pageId)
            if (t != null) {
                tabPages.selectionModel.select(t)
                return
            }
        }

        if (!url.matches('(?i)^http(s)*[:][/][/].+'))
            url = 'https://' + url

        def tabText = pageName?:Functions.Url2TabText(url)
        def tab = createTab(url, groupName, tabText)
        tab.id = pageId
        Logs.Info "Opening $url ..."

        def openView = {
            WebView webView = createWebView(tab)
            VBox.setVgrow(webView, Priority.ALWAYS)
            (tab.content as VBox).children.add(webView)
            webView.engine.loadContent("Loading $url ...", 'text/html')

            Platform.runLater(new Runnable() {
                @Override
                void run() {
                    try {
                        webView.engine.load(url)
                    }
                    catch (Exception e) {
                        Logs.Severe("Load $url error: ${e.message}")
                        Logs.Dump(e, 'webengine', url, null)
                        throw e
                    }
                }
            })
        }

        if (ConfigManager.config.autoStartServers) {
            def server = ConfigManager.config.localServers.findServerByUrl(url)
            if (server != null)
                server.addTab(tab)
            if (server != null && server.isStop && !server.ping()) {
                def listItems = FXCollections.observableArrayList("Start server \"${server.name}\" at \"${server.uri}\" with command line:".toString())
                listItems.add(server.command)

                def buffer = new ConcurrentLinkedDeque<String>()

                def consoleList = new ListView<String>(listItems)
                VBox.setVgrow(consoleList, Priority.ALWAYS)

                (tab.content as VBox).children.add(consoleList)
                try {
                    server.start { code, line ->
                        buffer.addLast((code != null)?"Server \"${server.name}\" was stopped with code $code":line)
                    }
                }
                catch (Exception e) {
                    buffer.addLast(e.message)
                }

                new Executor(waitTime: 500).tap { exec ->
                    startBackground {
                        def needMonitoring = !server.isStop && !server.ping()
                        if (needMonitoring) {
                            if (!buffer.isEmpty()) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    void run() {
                                        String line
                                        while ((line = buffer.poll()) != null)
                                            listItems.add(line)
                                        consoleList.scrollTo(listItems.size() - 1)
                                        consoleList.selectionModel.selectLast()
                                    }
                                })
                            }
                        }
                        else {
                            exec.stopBackground()
                            server.detachProcessing()

                            if (!server.isStop) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    void run() {
                                        (tab.content as VBox).children.remove(consoleList)
                                        openView.call()
                                    }
                                })
                            }
                        }
                    }
                }
            }
            else
                openView.call()
        }
        else
            openView.call()
    }

    @FXML
    void resetSite() {
        def tab = currentTab
        if (tab == null || tab.id == null || (tab.userData as Map<String, Object>).url == null)
            return

        def ud = currentTab.userData as Map<String, Object>
        def url = ud.url as String

        ConfigManager.config.cookieStore.removeFromUrl(url)
        currentWebView.engine.load(url)
    }

    @FXML
    void prevPage() {
        currentWebView?.engine?.executeScript("history.back()")
    }

    @FXML
    void nextPage() {
        currentWebView?.engine?.executeScript("history.forward()")
    }

    @FXML
    void clearCookies() {
        currentTab?.tap { tab ->
            if ((userData as Map<String, Object>).url == null)
                return

            def dialog = new Alert(Alert.AlertType.CONFIRMATION)
            (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
            dialog.title = 'Confirm'
            dialog.headerText = "Clear all cookies from ${tab.text}"
            dialog.contentText = 'You are sure?'
            dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
            def res = dialog.showAndWait()
            if (res.get() == ButtonType.YES) {
                def ud = userData as Map<String, Object>
                def url = ud.url as String
                ConfigManager.config.cookieStore.removeFromUrl(url, false)
                currentWebView.engine.reload()
            }
        }
    }

    @FXML
    void closeSite() {
        //noinspection GroovyMissingReturnStatement
        currentTab?.tap { tab ->
            def ud = userData as Map<String, Object>
            if (ud.url == null && ud.owner != null) {
                tabPages.tabs.remove(tab)
                return
            }

            def dialog = new Alert(Alert.AlertType.CONFIRMATION)
            (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
            dialog.title = 'Confirm'
            dialog.headerText = "Closing tab ${tab.text}"
            dialog.contentText = 'You are sure?'
            dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
            def res = dialog.showAndWait()
            if (res.get() == ButtonType.YES)
                tabPages.tabs.remove(tab)
        }
    }

    public final EventHandler favoriteMenuAction = new EventHandler<ActionEvent>() {
        @Override
        void handle(ActionEvent event) {
            loadPage(event.source as MenuItem)
        }
    }

    @FXML
    void saveToFavorites() {
        def tab = currentTab
        if (tab == null || tab.id != null || (tab.userData as Map<String, Object>).url == null)
            return

        def dialog = new TextInputDialog()
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'Save site to favorites'
        dialog.headerText = 'Enter name in favorites (format: Group/Name)'
        while (true) {
            Optional<String> res = dialog.showAndWait()
            if (!res.present)
                return

            def name = res.get().trim()
            if (name.length() == 0) {
                dialog.headerText = 'Please enter a non-empty name'
                continue
            }
            def i = name.indexOf('/')
            if (i > 0 && i < name.length() - 1) {
                def groupName = name.substring(0, i).trim()
                name = name.substring(i + 1).trim()
                def groupMap = ConfigManager.config.favorites.get(groupName)
                if (groupMap == null) {
                    groupMap = [:] as Map<String, Map<String, Object>>
                    ConfigManager.config.favorites.put(groupName, groupMap)
                }
                groupMap.put(name, [url: (tab.userData as Map<String, Object>).url])
                tab.id = tabId(groupName, name)
                tab.text = '[' + name + ']' + ': ' + currentWebView.engine.title
                (tab.userData as Map<String, Object>).putAll([groupName: groupName, name: name])

                def groupMenu = menuFavorites.items.find { it.text.toLowerCase() == groupName.toLowerCase() } as Menu
                if (groupMenu == null) {
                    groupMenu = new Menu(groupName).tap { mnemonicParsing = false }
                    menuFavorites.items.add(groupMenu)
                    menuFavorites.items.sort(true)  { a, b -> a.text <=> b.text }
                }
                def item = new MenuItem(name).tap { el ->
                    el.mnemonicParsing = false
                    el.userData = [url: (tab.userData as Map<String, Object>).url]
                    el.id = menuId(groupName, name)
                    el.onAction = favoriteMenuAction
                }
                groupMenu.items.add(item)
                groupMenu.items.sort(true) { a, b -> a.text <=> b.text }
                ConfigManager.config.saveFavorites()

                break
            }

            dialog.headerText = 'The name must consist of a group, slash and name'
        }
    }

    MenuItem findMenuItem(String groupName, String name) {
        def menu = menuFavorites.items.find { it.text == groupName } as Menu
        if (menu == null)
            return null

        return menu.items.find { it.text == name }
    }

    @FXML
    void renameInFavorites() {
        def tab = currentTab
        if (tab == null || tab.id == null || (tab.userData as Map<String, Object>).url == null)
            return

        def tabData = tab.userData as Map<String, Object>
        def groupName = tabData.groupName as String
        def curName = tabData.name as String
        def item = findMenuItem(groupName, curName)
        if (item == null)
            return

        def dialog = new TextInputDialog(curName)
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'Rename in favorites'
        dialog.headerText = 'Enter new name in favorites'

        String newName
        while (true) {
            Optional<String> res = dialog.showAndWait()
            if (!res.present)
                return

            newName = res.get().trim()
            if (newName.length() > 0)
                break

            dialog.headerText = 'Please enter a non-empty name'
        }

        item.id = menuId(groupName, newName)
        item.text = newName

        def parent = item.parentMenu
        parent.items.sort(true) { a, b -> a.text <=> b.text }

        tab.id = tabId(groupName, newName)
        (tab.userData as Map<String, Object>).name = newName
        tab.text = '[' + newName + ']: ' + currentWebView.engine.title

        def groupMenu = ConfigManager.config.favorites.get(groupName)
        groupMenu.put(newName, groupMenu.remove(curName))
        ConfigManager.config.saveFavorites()
    }

    @FXML
    void removeFromFavorites() {
        def tab = currentTab
        if (tab == null || tab.id == null || (tab.userData as Map<String, Object>).url == null)
            return

        def tabData = tab.userData as Map<String, Object>
        def item = findMenuItem(tabData.groupName as String, tabData.name as String)
        if (item == null)
            return
        def parent = item.parentMenu

        def dialog = new Alert(Alert.AlertType.CONFIRMATION)
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'WebFx warning'
        dialog.headerText = "Removing site \"${parent.text}/${item.text}\" from favorites"
        dialog.contentText = 'You are sure?'
        dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
        def res = dialog.showAndWait()
        if (res.get() == ButtonType.YES) {
            closeSite()
            parent.items.remove(item)
            ConfigManager.config.favorites.get(parent.text)?.remove(item.text)

            if (parent.items.isEmpty()) {
                parent.parentMenu.items.remove(parent)
                ConfigManager.config.favorites.remove(parent.text)
            }

            ConfigManager.config.saveFavorites()
        }
    }

    @FXML
    void preferences() {

    }

    @FXML
    void about() {
        def dialog = new Alert(Alert.AlertType.INFORMATION,
                "Version ${ConfigManager.WebFxVersion} [${DateUtils.FormatDate(ConfigManager.config.WebFxBuildDate)}]\nAll Right Reserved by EasyData Company")
        (dialog.dialogPane.scene.window as Stage).icons.add(ConfigManager.config.mainIcon)
        dialog.title = 'About program'
        dialog.headerText = ConfigManager.config.titleMainWindow
        dialog.showAndWait()
    }
}