package org.tasks.preferences;

import com.todoroo.andlib.utility.Preferences;

public class TasksPreferenceManager {

    private static final String EXPERIMENTAL_FEATURES_ENABLED = "EXPERIMENTAL_FEATURES_ENABLED";

    private static final String DROPBOX_ENABLED = "DROPBOX_ENABLED";

    public boolean isExperimentalFeaturesEnabled() {
        return Preferences.getBoolean(EXPERIMENTAL_FEATURES_ENABLED, true);
    }

    public boolean isDropboxSyncEnabled() {
        return Preferences.getBoolean(DROPBOX_ENABLED, false);
    }

    public void setDropboxSyncEnabled(boolean enabled) {
        Preferences.setBoolean(DROPBOX_ENABLED, enabled);
    }
}
