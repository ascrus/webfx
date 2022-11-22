//file:noinspection unused
package ru.easydata.webfx.servers

import getl.proc.Executor
import getl.utils.FileUtils
import getl.utils.Logs
import getl.utils.WebUtils
import groovy.transform.Synchronized
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import javafx.scene.control.Tab
import ru.easydata.webfx.config.ConfigManager
import ru.easydata.webfx.exception.InvalidParameter
import ru.easydata.webfx.exception.LocalServerAlreadyRunning
import ru.easydata.webfx.exception.LocalServerAlreadyStopped
import ru.easydata.webfx.utils.Functions

class LocalServer {
    LocalServer(String name, String url, String directory, String command, Integer commandTimeout,
                String shutdownService, Integer shutdownTimeout, String encode, Boolean autoStopServers) {
        if (name == null || name.length() == 0)
            throw new NullPointerException("Null name!")

        if (url == null || url.length() == 0)
            throw new NullPointerException("Null url!")

        if (command == null || command.length() == 0)
            throw new NullPointerException("Null command!")

        if (commandTimeout <= 0)
            throw new InvalidParameter(name, 'startTimeout')

        if (shutdownTimeout <= 0)
            throw new InvalidParameter(name, 'stopTimeout')

        if (autoStopServers == null)
            throw new NullPointerException("Null autoStopServers!")

        this.name = name
        this.uri = Functions.Url2UriWithRoot(url)

        this.command = command
        this.commandTimeout = commandTimeout
        this.shutdownService = shutdownService
        this.shutdownTimeout = shutdownTimeout

        this.cmdArgs = FileUtils.ParseArguments(FileUtils.TransformFilePath(this.command))
        this.directory = directory?:FileUtils.PathFromFile(FileUtils.ConvertToUnixPath(this.cmdArgs[0]), true)

        this.encode = encode?:'utf-8'
        this.autoStopServers = autoStopServers
    }

    /** Local server name */
    private  String name
    /** Local server name */
    String getName() { name }

    /** Local server URI */
    private  URI uri
    /** Local server URI */
    URI getUri() { uri }

    /** Work directory */
    private  String directory
    /** Work directory */
    String getDirectory() { directory }

    /** Start command */
    private  String command
    /** Start command */
    String getCommand() { command }

    /** Start timeout */
    private Integer commandTimeout
    /** Start timeout */
    Integer getCommandTimeout() { commandTimeout }

    /** Shutdown service */
    private  String shutdownService
    /** Shutdown service */
    String getShutdownService() { shutdownService }

    /** Shutdown timeout */
    private Integer shutdownTimeout
    /** Shutdown timeout */
    Integer getShutdownTimeout() { shutdownTimeout }

    /** Console encode */
    private  String encode
    /** Console encode */
    String getEncode() { encode }

    /** Auto stop server on close url tab */
    private Boolean autoStopServers
    /** Auto stop server on close url tab */
    Boolean getAutoStopServers() { autoStopServers }

    private final List<Tab> usedTabs = Collections.synchronizedList(new LinkedList<Tab>())

    /** Process tracking thread */
    private final Executor exec = new Executor(waitTime: 500)

    /** Thread stop sign */
    private Boolean isStop = true
    /** Thread stop sign */
    Boolean getIsStop() { isStop }

    /** Console read stream */
    private BufferedReader consoleReader

    /** Error reading stream */
    private BufferedReader errorReader

    /** Running OS process */
    private Process process

    /** Process console output processing code */
    private Closure processing

    /** Process startup arguments */
    private List<String> cmdArgs

    /** Process work handling code */
    private final Closure executeCode = {
        if (isStop)
            return

        readLines()

        if (!process.alive) {
            consoleReader.close()
            errorReader.close()

            try {
                synchronized (exec) {
                    if (processing != null)
                        processing.call(process.exitValue(), null)
                }
            }
            finally {
                isStop = true
                exec.stopBackground()
                synchronized (exec) {
                    processing = null
                }
            }
        }
    }

    /** Read text from console and errors */
    private void readLines() {
        String consoleLine = null
        while (consoleReader.ready() && (consoleLine = consoleReader.readLine()) != null) {
            synchronized (exec) {
                if (processing != null)
                    processing.call(null, consoleLine)
            }
        }

        String errorLine = null
        while (errorReader.ready() && (errorLine = errorReader.readLine()) != null) {
            synchronized (exec) {
                if (processing != null)
                    processing.call(null, errorLine)
            }
        }
    }

    /** Stop process execution */
    Integer stop() {
        Logs.Info("Shutdowning local server $name with $uri ...")
        if (shutdownService != null) {
            try {
                def con = WebUtils.CreateConnection(url: uri.toString(), service: shutdownService, connectTimeout: shutdownTimeout, requestMethod: 'POST')
                def res = con.responseCode
                if (res == HttpURLConnection.HTTP_OK) {
                    def cur = 0
                    def max = (shutdownTimeout != null) ? shutdownTimeout * 1000 : Integer.MAX_VALUE
                    Boolean isPing
                    while ((isPing = ping()) && cur < max) {
                        Thread.sleep(250)
                        cur += 250
                    }
                    if (!isPing && !process.alive) {
                        Logs.Info("Server $name with $uri was shutdowned")
                        return 0
                    }
                }
            }
            catch (Exception e) {
                Logs.Severe "Shutdown local server \"$name\" has error: ${e.message}"
            }
        }

        if (!process.alive)
            throw new LocalServerAlreadyStopped(name, uri.toString())

        process.destroy()
        def res = process.waitFor()
        Logs.Info("Server $name with $uri was shutdowned")
        return res
    }

    @Synchronized
    void start(@ClosureParams(value = SimpleType, options = ['java.lang.Integer', 'java.lang.String']) Closure consoleProcessing) {
        if (consoleProcessing == null)
            throw new NullPointerException("Null console processing code!")

        if (!isStop)
            throw new LocalServerAlreadyRunning(name, uri.toString())

        synchronized (exec) {
            processing = consoleProcessing
        }

        Logs.Info("Starting server $name with $uri ...")

        def pb = new ProcessBuilder(cmdArgs)
        pb.directory(new File(directory))
        process = pb.start()
        isStop = false

        def is = process.getInputStream()
        consoleReader = new BufferedReader(new InputStreamReader(is, encode))

        def es = process.getErrorStream()
        errorReader = new BufferedReader(new InputStreamReader(es, encode))

        if (!process.alive) {
            consoleReader.close()
            errorReader.close()
            try {
                readLines()
                synchronized (exec) {
                    if (processing != null)
                        processing.call(process.exitValue(), null)
                }
            }
            finally {
                isStop = true
                synchronized (exec) {
                    processing = null
                }
            }
            return
        }

        exec.startBackground(executeCode)
        Logs.Info("Server $name with $uri was started")
    }

    void detachProcessing() {
        synchronized (exec) {
            processing = null
        }
    }

    Boolean ping() {
        return Functions.PingHost(uri.host, uri.port, 250)
    }

    @Synchronized
    void addTab(Tab tab) {
        if (usedTabs.indexOf(tab) == -1)
            usedTabs.add(tab)
    }

    @Synchronized
    void removeTab(Tab tab) {
        if (usedTabs.indexOf(tab) != -1) {
            usedTabs.remove(tab)
            if (usedTabs.isEmpty() && ConfigManager.config.autoStopServers && !isStop)
                stop()
        }
    }

    @Override
    String toString() {
        return "$name [$uri]"
    }
}