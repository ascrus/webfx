//file:noinspection unused
package ru.easydata.webfx.controllers

import getl.proc.Executor
import getl.utils.Logs
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
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import javafx.stage.Stage
import ru.easydata.webfx.app.WebFxApplication
import ru.easydata.webfx.cookie.CookieStoreManager
import ru.easydata.webfx.utils.Functions

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.regex.Pattern

class MainController {
    MainController(WebFxApplication app) {
        this.application = app
    }

    WebFxApplication application

    @FXML
    Menu menuFavorites

    @FXML
    TabPane tabPages

    @FXML
    void quit() {
        def dialog = new Alert(Alert.AlertType.CONFIRMATION)
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
        dialog.title = 'WebFx Warning'
        dialog.headerText = 'Closing program'
        dialog.contentText = 'You are sure?'
        dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
        def res = dialog.showAndWait()
        if (res.get() == ButtonType.YES)
            application.mainWindow.close()
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
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
        dialog.title = 'Open new page'
        dialog.headerText = 'Enter https or http url (default https)'
        Optional<String> res = dialog.showAndWait()
        if (res.present)
            loadPage(res.get())
    }

    static String tabId(String groupName, String name) {
        if (groupName == null || name == null)
            return null

        return 'tab_' + String.valueOf((groupName + '/' + name).hashCode())
    }

    static String menuId(String groupName, String name) {
        return 'menu_' + String.valueOf((groupName + '/' + name).hashCode())
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
        application.favorites.each { groupName, groupElements ->
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

        def pattern = Pattern.compile('(?i)^(http.*://)(.+)')
        def matcher = pattern.matcher(url)
        if (!matcher.find()) {
            def alert = new Alert(Alert.AlertType.ERROR)
            alert.title = 'WebFx error'
            alert.headerText = 'Invalid url address'
            alert.contentText = url
            alert.showAndWait()
            return
        }
        def tabText = matcher.group(2).with {
            def i = it.indexOf('/')
            return (i != -1)?it.substring(0, i):it
        }

        def cookieManager = new CookieManager(new CookieStoreManager(application.userDir.path + '/userdata', url), CookiePolicy.ACCEPT_ORIGINAL_SERVER)

        def pane = new VBox()
        pane.setPadding(new Insets(5, 5, 5, 5))
        def tab = new Tab((pageName != null)?('[' + pageName + ']'):tabText, pane)
        tab.closable = true
        tab.id = pageId
        tab.userData = [url: url, groupName: groupName, name: pageName, cm: cookieManager]
        Logs.Info "Opening $url ..."
        tab.onCloseRequest  = new EventHandler<Event>() {
            @Override
            void handle(Event event)
            {
                def dialog = new Alert(Alert.AlertType.CONFIRMATION)
                (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
                dialog.title = 'Confirm'
                dialog.headerText = "Closing tab ${tab.text}"
                dialog.contentText = 'You are sure?'
                dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
                def res = dialog.showAndWait()
                if (res.get() != ButtonType.YES)
                    event.consume()
            }
        }
        tab.onClosed = new EventHandler<Event>() {
            @Override
            void handle(Event event) {
                def cm = (tab.userData as Map<String, Object>).cm as CookieManager
                if (cm != null)
                    (cm.cookieStore as CookieStoreManager).close()

                def wv = (tab.userData as Map<String, Object>).webView as WebView
                if (wv != null) {
                    wv.engine.loadContent("Closing ...", 'text/html')
                }

                def server = application.findServerByUrl(url)
                if (server != null) {
                    server.removeTab(tab)
                    Logs.Info "Closed $url"
                }
            }
        }
        tabPages.tabs.add(tab)
        tabPages.selectionModel.select(tab)

        def openView = {
            WebView webView
            synchronized (this) {
                CookieHandler.setDefault(cookieManager)
                webView = new WebView()
            }
            (tab.userData as Map<String, Object>).webView = webView
            VBox.setVgrow(webView, Priority.ALWAYS)
            pane.children.add(webView)

            webView.engine.tap { webEng ->
                userDataDirectory = new File(application.userDir.path + '/userdata')
                javaScriptEnabled = true
                userAgent = 'WebFx Browser 1.0'

                loadWorker.stateProperty().addListener(new ChangeListener<Worker.State>() {
                    private URI currentURI

                    @Override
                    void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                        switch (newValue) {
                            case Worker.State.SCHEDULED:
                                if (currentURI != null) {
                                    def cookies = cookieManager.cookieStore.get(currentURI)
                                    cookies.each {
                                        if (it.version > 0)
                                            cookieManager.cookieStore.remove(currentURI, it)
                                    }
                                }
                                break
                            case Worker.State.FAILED:
                                currentURI = null
                                webEng.loadContent('Load error: ' + webEng.loadWorker.exception.message, 'text/html')
                                break
                            case Worker.State.SUCCEEDED:
                                if (webEng.location != null && webEng.location.length() > 0) {
                                    def u = new URL(webEng.location)
                                    currentURI = new URI(u.protocol + '://' + u.authority)
                                }
                                break
                        }
                    }
                })

                titleProperty().addListener(new ChangeListener<String>() {
                    @Override
                    void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                        def str = '[' + (pageName ?: tabText) + ']: ' + newValue ?: oldValue ?: ''
                        if (str.length() > 40)
                            str = str.substring(0, 40) + ' ...'
                        tab.text = str
                    }
                })
            }
            webView.engine.loadContent("Loading $url ...", 'text/html')

            Platform.runLater(new Runnable() {
                @Override
                void run() {
                    sleep(100)
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

        if (application.autoStartServers) {
            def server = application.findServerByUrl(url)
            if (server != null)
                server.addTab(tab)
            if (server != null && server.isStop && !server.ping()) {
                def listItems = FXCollections.observableArrayList("Start server \"${server.name}\" at \"${server.uri}\" with command line:".toString())
                listItems.add(server.command)

                def buffer = new ConcurrentLinkedDeque<String>()

                def consoleList = new ListView<String>(listItems)
                VBox.setVgrow(consoleList, Priority.ALWAYS)

                pane.children.add(consoleList)
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
                                        pane.children.remove(consoleList)
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
    void refreshPage() {
        currentWebView?.engine?.reload()
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
        currentTab?.tap {
            def ud = userData as Map<String, Object>
            def url = ud.url as String
            def cm = ud.cm as CookieManager
            if (url != null && cm != null) {
                def sm = cm.cookieStore as CookieStoreManager
                def uri = new URI(url)
                sm.get(uri).each {cookie ->
                    sm.remove(uri, cookie)
                }
            }
            currentWebView.engine.reload()
        }
    }

    @FXML
    void closeSite() {
        def tab = currentTab
        if (tab != null) {
            def dialog = new Alert(Alert.AlertType.CONFIRMATION)
            (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
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
        if (tab == null)
            return
        if (tab.id != null)
            return

        def dialog = new TextInputDialog()
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
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
                def groupMap = application.favorites.get(groupName)
                if (groupMap == null) {
                    groupMap = [:] as Map<String, Map<String, Object>>
                    application.favorites.put(groupName, groupMap)
                }
                groupMap.put(name, [url: (tab.userData as Map<String, Object>).url])
                tab.id = tabId(groupName, name)
                tab.text = '[' + name + ']' + ': ' + tab.text
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
                application.saveConfigFavorites()

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
        if (tab == null)
            return
        if (tab.id == null)
            return

        def tabData = tab.userData as Map<String, Object>
        def groupName = tabData.groupName as String
        def curName = tabData.name as String
        def item = findMenuItem(groupName, curName)
        if (item == null)
            return

        def dialog = new TextInputDialog(curName)
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
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

        def groupMenu = application.favorites.get(groupName)
        groupMenu.put(newName, groupMenu.remove(curName))
        application.saveConfigFavorites()
    }

    @FXML
    void removeFromFavorites() {
        def tab = currentTab
        if (tab == null)
            return
        if (tab.id == null)
            return

        def tabData = tab.userData as Map<String, Object>
        def item = findMenuItem(tabData.groupName as String, tabData.name as String)
        if (item == null)
            return
        def parent = item.parentMenu

        def dialog = new Alert(Alert.AlertType.CONFIRMATION)
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
        dialog.title = 'WebFx warning'
        dialog.headerText = "Removing site \"${parent.text}/${item.text}\" from favorites"
        dialog.contentText = 'You are sure?'
        dialog.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
        def res = dialog.showAndWait()
        if (res.get() == ButtonType.YES) {
            closeSite()
            parent.items.remove(item)
            application.favorites.get(parent.text)?.remove(item.text)

            if (parent.items.isEmpty()) {
                parent.parentMenu.items.remove(parent)
                application.favorites.remove(parent.text)
            }

            application.saveConfigFavorites()
        }
    }

    @FXML
    void preferences() {

    }

    @FXML
    void about() {
        def dialog = new Alert(Alert.AlertType.INFORMATION, 'Version 1.0, (c) 2022 EasyData Company')
        (dialog.dialogPane.scene.window as Stage).icons.add(application.mainIcon)
        dialog.title = 'About program'
        dialog.headerText = application.titleMainWindow
        dialog.showAndWait()
    }
}