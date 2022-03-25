package ru.easydata.webfx.servers

import getl.utils.Logs
import groovy.transform.InheritConstructors
import ru.easydata.webfx.config.ConfigManager
import ru.easydata.webfx.exception.LocalServerAlreadyRegister
import ru.easydata.webfx.exception.LocalServerUnknown
import ru.easydata.webfx.utils.Functions

@InheritConstructors
class ListServers extends LinkedList<LocalServer> {
    LocalServer findServerByName(String name) {
        if (name == null)
            throw new NullPointerException("Null name!")

        name = name.trim().toLowerCase()
        return find { it.name.toLowerCase() == name }
    }

    LocalServer findServerByUri(URI uri) {
        if (uri == null)
            throw new NullPointerException("Null uri!")

        return find { (it.uri == uri) }
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

        add(new LocalServer( name, url, directory, command, commandTimeout, shutdownService, shutdownTimeout, encode, ConfigManager.config.autoStopServers))
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

        remove(server)
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
}