/*
 * Copyright (c) 2014ff Thomas Feuster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tf.geotagger.main;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

/**
 * Starts GeoTagger: loads the main window from GeoTagger.fxml and restores its size and position.
 */
public class GeoTaggerManager extends Application {
    public static final String TITLE = "GeoTagger";

    private Stage myStage;
    private GeoTagger myGeoTagger;

    public static void main(String[] args) {
        launch(GeoTaggerManager.class, args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        myStage = primaryStage;

        final FXMLLoader fxmlLoader = new FXMLLoader(GeoTaggerManager.class.getResource("/GeoTagger.fxml"));
        final Parent root = fxmlLoader.load();
        myGeoTagger = fxmlLoader.getController();

        // restore size and position of the last session - same as GPXEditor
        Double width = GeoTaggerPreferences.RECENT_WINDOW_WIDTH.getAsType();
        Double height = GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.getAsType();
        final Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        Double left = GeoTaggerPreferences.RECENT_WINDOW_LEFT.getAsType();
        if (left < 0.0) {
            left = (screenBounds.getWidth() - width) / 2.0;
        }
        Double top = GeoTaggerPreferences.RECENT_WINDOW_TOP.getAsType();
        if (top < 0.0) {
            top = (screenBounds.getHeight() - height) / 2.0;
        }
        // the last position might be on a monitor that isn't there anymore
        if (Screen.getScreensForRectangle(left, top, width, height).isEmpty()) {
            width = GeoTaggerPreferences.RECENT_WINDOW_WIDTH.getDefaultAsType();
            height = GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.getDefaultAsType();
            left = (screenBounds.getWidth() - width) / 2.0;
            top = (screenBounds.getHeight() - height) / 2.0;
        }

        myStage.setScene(new Scene(root, width, height));
        myStage.setX(left);
        myStage.setY(top);
        myStage.setTitle(TITLE);
        new JMetro(Style.LIGHT).setScene(myStage.getScene());
        myStage.show();
        // started from another application (e.g. NetBeans) Windows doesn't bring the window to the front -
        // briefly "always on top" does, same trick as the splash screen of GPXEditor
        myStage.setAlwaysOnTop(true);
        myStage.setAlwaysOnTop(false);

        myGeoTagger.lateInitialize(myStage);
    }

    @Override
    public void stop() {
        if (myStage != null && !myStage.isMaximized() && !myStage.isIconified()) {
            GeoTaggerPreferences.RECENT_WINDOW_WIDTH.put(myStage.getScene().getWidth());
            GeoTaggerPreferences.RECENT_WINDOW_HEIGHT.put(myStage.getScene().getHeight());
            GeoTaggerPreferences.RECENT_WINDOW_LEFT.put(myStage.getX());
            GeoTaggerPreferences.RECENT_WINDOW_TOP.put(myStage.getY());
        }
        if (myGeoTagger != null) {
            myGeoTagger.stop();
        }
    }
}
