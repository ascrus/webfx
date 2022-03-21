package ru.easydata.webfx.exception

class LocalServerAlreadyRunning extends Exception {
    LocalServerAlreadyRunning(String name, String url) {
        super("Server \"$name\" by url \"$url\" already running!")
    }
}