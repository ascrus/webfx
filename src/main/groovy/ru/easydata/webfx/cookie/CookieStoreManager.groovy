package ru.easydata.webfx.cookie

import getl.h2.H2Connection
import getl.h2.H2Table
import getl.jdbc.QueryDataset
import getl.jdbc.TableDataset
import getl.proc.Flow
import getl.utils.BoolUtils
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

class CookieStoreManager implements CookieStore {
    CookieStoreManager(String userDir) {
        con.connectDatabase = "$userDir/cookies"
        synchronized (table) {
            con.connected = true
            table.tap {
                if (!exists)
                    create()
            }
            tempTable.create()
        }
    }

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
    private final H2Table tempTable = new H2Table(connection: con, tableName: 'cookies_temp', type: TableDataset.localTemporaryTableType).tap {
        createOpts { not_persistent = true }
        field = table.field
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

    static Boolean IsTempCookie(HttpCookie cookie) {
        return (cookie.name[0] == '$' || !IsToken(cookie.name) || cookie.maxAge <= 0 || cookie.version > 0)
    }

    @Override
    void add(URI uri, HttpCookie cookie) {
        synchronized (table) {
            def destTable = (IsTempCookie(cookie))?tempTable:table
            new Flow().writeTo(dest: destTable, dest_operation: 'merge') { upd ->
                def row = Cookie2Row(uri, cookie)
                upd.call(row)
            }
        }
    }

    @Override
    List<HttpCookie> get(URI uri) {
        def res = [] as List<HttpCookie>
        def params = [host: uri.host, port: uri.port]
        synchronized (table) {
            table.eachRow(where: 'host = \'{host}\' AND port = {port}', queryParams: params) { row ->
                res.add(Row2Cookie(row))
            }
            tempTable.eachRow(where: 'host = \'{host}\' AND port = {port}', queryParams: params) { row ->
                res.add(Row2Cookie(row))
            }
        }
        return res
    }

    @Override
    List<HttpCookie> getCookies() {
        def res = [] as List<HttpCookie>
        synchronized (table) {
            table.eachRow { row ->
                res.add(Row2Cookie(row))
            }
            tempTable.eachRow { row ->
                res.add(Row2Cookie(row))
            }
        }
        return res
    }

    @Override
    List<URI> getURIs() {
        def res = [] as List<URI>
        synchronized (table) {
            new QueryDataset(connection: con,
                    query: 'SELECT DISTINCT host, port, secure FROM cookies UNION SELECT DISTINCT host, port, secure FROM cookies_temp').eachRow { row ->
                def url = ((BoolUtils.IsValue(row.secure))?'https':'http') + row.host + ':' + row.port
                res.add(new URI(url))
            }
        }
        return res
    }

    @Override
    boolean remove(URI uri, HttpCookie cookie) {
        Boolean res
        synchronized (table) {
            def destTable = (IsTempCookie(cookie))?tempTable:table
            res = (new Flow().writeTo(dest: destTable, dest_operation: 'delete') { del ->
                def row = Cookie2Row(uri, cookie)
                del.call(row)
            } > 0)
        }

        return res
    }

    void removeFromUrl(String url, Boolean onlyTemporary = true,
                       @ClosureParams(value = SimpleType, options = ['java.net.URI', 'java.net.HttpCookie'])
                               Closure<Boolean> cl = null) {
        removeFromUri(new URI(url), onlyTemporary, cl)
    }

    void removeFromUri(URI uri, Boolean onlyTemporary = true,
                @ClosureParams(value = SimpleType, options = ['java.net.URI', 'java.net.HttpCookie'])
                        Closure<Boolean> cl = null) {
        synchronized (table) {
            get(uri).each { cookie ->
                if (!onlyTemporary || IsTempCookie(cookie))
                    if (cl == null || cl.call(uri, cookie))
                        remove(uri, cookie)
            }
        }
    }

    @Override
    boolean removeAll() {
        synchronized (table) {
            table.truncate()
            tempTable.truncate()
        }

        return true
    }

    void close() {
        synchronized (table) {
            con.connected = false
        }
    }
}