package ru.easydata.webfx.cookie

import getl.h2.H2Connection
import getl.h2.H2Table
import getl.proc.Flow
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

class CookieStoreManager implements CookieStore {
    CookieStoreManager(String userDir) {
        store = new CookieManager().cookieStore

        con.connectDatabase = "$userDir/cookies"
        con.connected = true

        synchronized (table) {
            table.tap {
                if (!exists)
                    create()
            }
        }
    }

    private CookieStore store

    private final H2Connection con = new H2Connection(login: 'webfx', password: 'EASYDATA WEBFX BROWSER',
            connectProperty: [PAGE_SIZE: 16384, CIPHER: 'AES', DB_CLOSE_ON_EXIT: true], extensionForSqlScripts: true)
    private final H2Table table = new H2Table(connection: con, tableName: 'cookies').tap {
        field('host') { length = 250; isKey = true }
        field('port') { type = integerFieldType; isKey = true }
        field('path') { length = 512; isKey = true }
        field('name') { length = 512; isKey = true }
        field('value') { length = 1024 }
        field('domain') { length = 250; isNull = false }
        field('version') { type = integerFieldType; isNull = false }
        field('maxAge') { type = bigintFieldType; isNull = false }
        field('discard') { type = booleanFieldType; isNull = false }
        field('httpOnly') { type = booleanFieldType; isNull = false }
        field('secure') { type = booleanFieldType; isNull = false }
        field('portlist') { length = 250 }
        field('comment') { length = 1024 }
        field('commentURL') { length = 512 }
    }

    static Map<String, Object> Cookie2Row(URI uri, HttpCookie cookie) {
        def row = [:] as Map<String, Object>
        row.host = uri.host
        row.port = uri.port
        row.path = cookie.path
        row.name = cookie.name
        row.value = (cookie.value != null && cookie.value.length() > 0)?cookie.value:null
        row.maxage = cookie.maxAge
        row.version = cookie.version
        row.comment = cookie.comment
        row.commenturl = cookie.commentURL
        row.discard = cookie.discard
        row.httponly = cookie.httpOnly
        row.domain = cookie.domain
        row.portlist = cookie.portlist
        row.secure = cookie.secure

        return row
    }

    static HttpCookie Row2Cookie(Map<String, Object> row) {
        def cookie = new HttpCookie(row.name as String, row.value as String)
        cookie.maxAge = row.maxage as Long
        cookie.version = row.version as Integer
        cookie.comment = row.comment as String
        cookie.commentURL = row.commenturl as String
        cookie.discard = row.discard as Boolean
        cookie.httpOnly = row.httponly as Boolean
        cookie.domain = row.domain as String
        cookie.path = row.path as String
        cookie.portlist = row.portlist as String
        cookie.secure = row.secure as Boolean

        return cookie
    }

    private static final String incorrectNameChars = ',; '

    static boolean IsToken(String value) {
        int len = value.length()

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i)

            if (c < 0x20 || c >= 0x7f || incorrectNameChars.indexOf(String.valueOf(c)) != -1)
                return false
        }
        return true
    }

    @Override
    void add(URI uri, HttpCookie cookie) {
        store.add(uri, cookie)

        if (cookie.name[0] == '$' || !IsToken(cookie.name) || cookie.maxAge <= 0 || cookie.version > 0)
            return

        synchronized (table) {
            new Flow().writeTo(dest: table, dest_operation: 'merge') { upd ->
                def row = Cookie2Row(uri, cookie)
                upd.call(row)
            }
        }
    }

    @Override
    List<HttpCookie> get(URI uri) {
        return store.get(uri)
    }

    @Override
    List<HttpCookie> getCookies() {
        return store.cookies
    }

    @Override
    List<URI> getURIs() {
        return store.getURIs()
    }

    @Override
    boolean remove(URI uri, HttpCookie cookie) {
        def res = store.remove(uri, cookie)
        if (res && cookie.domain == uri.host) {
            synchronized (table) {
                res = (new Flow().writeTo(dest: table, dest_operation: 'delete') { del ->
                    def row = Cookie2Row(uri, cookie)
                    del.call(row)
                } > 0)
            }
        }

        return res
    }

    void removeFromUrl(String url,
                @ClosureParams(value = SimpleType, options = ['java.net.URI', 'java.net.HttpCookie'])
                        Closure<Boolean> cl = null) {
        def uri = new URI(url)
        get(uri).each { cookie ->
            if (cl == null || cl.call(uri, cookie))
                remove(uri, cookie)
        }
    }

    @Override
    boolean removeAll() {
        def res = store.removeAll()
        if (res)
            table.truncate()

        return res
    }

    void close() {
        con.connected = false
    }

    void reloadFromUrl(String sourceUrl) {
        def params = [:] as Map<String, Object>

        def sourceUri = new URI(sourceUrl)
        clearFromUri(sourceUri)
        if (sourceUrl != null) {
            get(sourceUri).each { cookie ->
                store.remove(sourceUri, cookie)
            }
            params.where = 'host = \'{host}\' AND port = {port}'
            params.queryParams = [host: sourceUri.host, port: sourceUri.port]
        }

        table.eachRow(params) { row ->
            def cookie = Row2Cookie(row)
            store.add(sourceUri, cookie)
        }
    }

    void clearFromUri(URI sourceUri,
                      @ClosureParams(value = SimpleType, options = ['java.net.URI', 'java.net.HttpCookie'])
                       Closure<Boolean> cl = null) {
        get(sourceUri).each { cookie ->
            if (cl == null || cl.call(sourceUri, cookie))
                store.remove(sourceUri, cookie)
        }
    }
}