package ru.easydata.webfx.exception

class LocalServerAlreadyRegister extends Exception {
    LocalServerAlreadyRegister(String name, String url) {
        super("Server \"$name\" already registered by url \"$url\"!")
    }
}
