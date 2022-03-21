package ru.easydata.webfx.exception

class LocalServerUnknown extends Exception {
    LocalServerUnknown(String name) {
        super("Server \"$name\" was not registered!")
    }
}
