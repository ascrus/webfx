package ru.easydata.webfx.exception

class InvalidParameter extends Exception {
    InvalidParameter(String name, String paramName) {
        super("Parameter \"$paramName\" of server \"$name\" must be greater than zero!")
    }
}
