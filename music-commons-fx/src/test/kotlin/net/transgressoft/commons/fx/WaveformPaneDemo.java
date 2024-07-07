package net.transgressoft.commons.fx;

import net.transgressoft.commons.music.waveform.ScalableAudioWaveform;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;

public class WaveformPaneDemo extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        WaveformPane waveformPane = new WaveformPane();
        AnchorPane anchorPane = new AnchorPane(waveformPane);
        anchorPane.setPrefSize(500, 200);
        waveformPane.heightProperty().bind(anchorPane.heightProperty());
        waveformPane.widthProperty().bind(anchorPane.widthProperty());
        URI uri = getClass().getResource("/testfiles/testeable.flac").toURI();

        primaryStage.setScene(new Scene(anchorPane));
        primaryStage.show();
        waveformPane.drawWaveformAsync(new ScalableAudioWaveform(1, new File(uri).toPath()), Color.GREEN, Color.MAGENTA);
    }
}
