package ru.easydata.webfx.controllers

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.stage.Stage

class DownloadController {
    public Stage window

    @FXML
    Label labelFileName

    @FXML
    Label labelFileSize

    @FXML
    ProgressBar progressBar

    @FXML
    Button buttonClose

    @FXML
    Button buttonCancel
}
