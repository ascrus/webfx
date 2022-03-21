package ru.easydata.webfx.exception

class LocalServerAlreadyStopped extends Exception {
    LocalServerAlreadyStopped(String name, String url) {
        super("Server \"$name\" already stopped by url \"$url\"!")
    }
}