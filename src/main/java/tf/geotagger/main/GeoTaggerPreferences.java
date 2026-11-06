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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import tf.helper.general.GeneralParser;
import tf.helper.general.IPreferencesStore;
import tf.helper.general.ObjectsHelper;
import tf.helper.general.RecentFiles;

/**
 * Preferences of GeoTagger, stored with java.util.prefs - same pattern as GPXEditorPreferences.
 */
public enum GeoTaggerPreferences implements IPreferencesStore {
    INSTANCE("instance", "", String.class),
    RECENT_WINDOW_WIDTH("recentWindowWidth", Double.toString(1400), Double.class),
    RECENT_WINDOW_HEIGHT("recentWindowHeight", Double.toString(900), Double.class),
    RECENT_WINDOW_LEFT("recentWindowLeft", Double.toString(-1), Double.class),
    RECENT_WINDOW_TOP("recentWindowTop", Double.toString(-1), Double.class),
    // list vs. preview
    RECENT_LEFT_DIVIDER_POS("recentLeftDividerPos", Double.toString(0.55), Double.class),
    // left side vs. right side
    RECENT_CENTRAL_DIVIDER_POS("recentCentralDividerPos", Double.toString(0.5), Double.class),
    // tabs map / image info vs. GPX section - new key since plan 4c: the old one split map and details
    RECENT_RIGHT_DIVIDER_POS("recentTabsDividerPos", Double.toString(0.65), Double.class),
    // track list vs. track points
    RECENT_TRACK_DIVIDER_POS("recentTrackDividerPos", Double.toString(0.45), Double.class),

    EXIFTOOL_PATH("exifToolPath", "", String.class),
    RECENT_FOLDER("recentFolder", System.getProperty("user.home"), String.class),
    INCLUDE_SUBFOLDERS("includeSubfolders", Boolean.toString(false), Boolean.class);

    // additional preferences not handled here as enums
    // tableview settings: ColumnOrder, ColumnWidth, ColumnVisibility, SortOrder - see tf.helper.javafx.TableViewPreferences

    private static final Preferences MYPREFERENCES = preferencesNode();

    // recently opened folders - like the recent files of GPXEditor
    private static final RecentFiles MYRECENTFOLDERS = new RecentFiles(INSTANCE, 10, true);

    private final String myPrefKey;
    private final String myDefaultValue;
    private final Class<?> myClass;

    GeoTaggerPreferences(final String key, final String defaultValue, final Class<?> classP) {
        myPrefKey = key;
        myDefaultValue = defaultValue;
        myClass = classP;
    }

    // tests use their own node (set by the Gradle test tasks) - the real settings stay untouched
    private static Preferences preferencesNode() {
        final String testNode = System.getProperty("geotagger.preferences");
        if (testNode != null && !testNode.isBlank()) {
            return Preferences.userRoot().node(testNode);
        }
        return Preferences.userNodeForPackage(GeoTaggerManager.class);
    }

    public static RecentFiles getRecentFolders() {
        return MYRECENTFOLDERS;
    }

    public String getAsString() {
        return get(myPrefKey, myDefaultValue);
    }

    public <T> T getAsType() {
        try {
            return ObjectsHelper.uncheckedCast(GeneralParser.parse(getAsString(), myClass));
        } catch (Exception ex) {
            return getDefaultAsType();
        }
    }

    public <T> void put(final T value) {
        put(myPrefKey, value.toString());
    }

    public <T> T getDefaultAsType() {
        return ObjectsHelper.uncheckedCast(GeneralParser.parse(myDefaultValue, myClass));
    }

    @Override
    public String get(final String key, final String defaultValue) {
        String result = defaultValue;
        try {
            result = MYPREFERENCES.get(key, defaultValue);
        } catch (SecurityException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    @Override
    public void put(final String key, final String value) {
        MYPREFERENCES.put(key, value);
    }

    @Override
    public void clear() {
        try {
            MYPREFERENCES.clear();
        } catch (BackingStoreException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void remove(final String key) {
        MYPREFERENCES.remove(key);
    }

    @Override
    public void exportPreferences(final OutputStream os) {
        try {
            MYPREFERENCES.exportSubtree(os);
        } catch (BackingStoreException | IOException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void importPreferences(final InputStream is) {
        try {
            Preferences.importPreferences(is);
        } catch (InvalidPreferencesFormatException | IOException ex) {
            Logger.getLogger(GeoTaggerPreferences.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
