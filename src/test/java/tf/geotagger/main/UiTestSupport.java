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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Helpers for the UI tests of GeoTagger - see docs/superpowers/plans/2026-11-05-plan-ui-test-geruest.md.
 */
final class UiTestSupport {
    // preferences node of the tests - set by the Gradle test tasks in build.gradle
    private static final String TEST_NODE = "tf/geotagger/test";

    private UiTestSupport() {
    }

    /**
     * Fresh preferences for each test - in the test node set by the Gradle test tasks, never the real one.
     *
     * @param exifTool ExifTool executable GeoTagger shall use
     * @throws org.opentest4j.TestAbortedException if the test node isn't set - the test is skipped then
     */
    static void preparePreferences(Path exifTool) {
        // started without the Gradle test tasks (e.g. another IDE runner) clear() would wipe the real settings
        Assumptions.assumeTrue(TEST_NODE.equals(System.getProperty("geotagger.preferences")),
                "UI tests need -Dgeotagger.preferences=" + TEST_NODE + " - run them via Gradle");
        GeoTaggerPreferences.INSTANCE.clear();
        GeoTaggerPreferences.EXIFTOOL_PATH.put(exifTool.toString());
    }

    /**
     * Starts GeoTagger in the stage of TestFX - must be called on the JavaFX thread, e.g. from {@code @Start}.
     *
     * @param stage stage provided by TestFX
     * @return the started application - call stop() in {@code @Stop}
     * @throws IOException if the main window can't be loaded
     */
    static GeoTaggerManager start(Stage stage) throws IOException {
        final GeoTaggerManager manager = new GeoTaggerManager();
        manager.start(stage);
        // the robot clicks screen positions - the window must stay in front while the test runs
        stage.setAlwaysOnTop(true);
        return manager;
    }

    /**
     * Waits until a folder is loaded and all resulting updates of the window are done.
     *
     * @param controller controller of the main window
     * @throws TimeoutException if loading takes more than 30 seconds
     */
    static void waitUntilIdle(GeoTagger controller) throws TimeoutException {
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !controller.isBusy());
        // the result is shown in an event handler on the JavaFX thread - wait until it has run
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Waits until the map page is loaded - markers and tracks given before are shown then.
     *
     * @param controller controller of the main window
     * @throws TimeoutException if the map isn't ready within 30 seconds
     */
    static void waitUntilMapReady(GeoTagger controller) throws TimeoutException {
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> controller.mapPane().isReady());
        WaitForAsyncUtils.waitForFxEvents();
    }
}
