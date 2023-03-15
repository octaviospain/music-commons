package net.transgressoft.commons.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.transgressoft.commons.music.waveform.AudioWaveform;
import net.transgressoft.commons.music.waveform.ScalableAudioWaveform;

import java.io.File;
import java.net.URI;

public class WaveformPaneDemo extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        URI uri = getClass().getResource("/testfiles/testeable.flac").toURI();
        AudioWaveform audioWaveform = new ScalableAudioWaveform(1, new File(uri).toPath());
        WaveformPane waveformPane = new WaveformPane(audioWaveform, 500, 200, Color.GREEN, Color.MAGENTA);
        AnchorPane anchorPane = new AnchorPane(waveformPane);

        waveformPane.heightProperty().bind(anchorPane.heightProperty());
        waveformPane.widthProperty().bind(anchorPane.widthProperty());
        anchorPane.setPrefSize(500, 200);
        primaryStage.setScene(new Scene(anchorPane));
        primaryStage.show();
    }
}
