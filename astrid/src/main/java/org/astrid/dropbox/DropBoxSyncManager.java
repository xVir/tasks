package org.astrid.dropbox;

import android.app.Activity;
import android.util.Log;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxDatastore;
import com.dropbox.sync.android.DbxException;
import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.DependencyInjectionService;

import org.astrid.preferences.AstridPreferenceManager;

public class DropBoxSyncManager {
    private static final String TAG = "DropBoxSyncManager";
    private static final String APP_KEY = "j2af4tdhxvm6ra9";
    private static final String APP_SECRET = "3g747lbtkfwq48s";

    private DbxAccountManager dbxAccountManager;
    private DbxDatastore datastore;

    @Autowired
    AstridPreferenceManager astridPreferenceManager;

    public DropBoxSyncManager() {
        DependencyInjectionService.getInstance().inject(this);
    }

    public void startLink(Activity activity) {
        initAccountManager(activity);
        if (hasLink()) {
            throw new RuntimeException("Account already linked");
        }
        dbxAccountManager.startLink(activity, DropBoxLinkActivity.REQUEST_LINK_TO_DBX);
    }

    public void resumeLink(Activity activity) {
        initAccountManager(activity);
        startSync();
    }

    public void clearLink() {
        if (dbxAccountManager != null && dbxAccountManager.hasLinkedAccount()) {
            dbxAccountManager.unlink();
            Log.d(TAG, "Dropbox account unlinked");
        }

        stopSync();

        astridPreferenceManager.setDropboxSyncEnabled(false);
    }

    public boolean hasLink() {
        return dbxAccountManager != null && dbxAccountManager.hasLinkedAccount();
    }

    public void linkSuccessful() {
        startSync();

        astridPreferenceManager.setDropboxSyncEnabled(true);
    }

    private void initAccountManager(Activity activity) {
        dbxAccountManager = DbxAccountManager.getInstance(activity.getApplicationContext(), APP_KEY, APP_SECRET);
    }

    private void startSync() {
        try {
            if (!dbxAccountManager.hasLinkedAccount()) {
                throw new RuntimeException("No account linked");
            }
            if (datastore != null && datastore.isOpen()) {
                throw new RuntimeException("Datastore already opened");
            }
            datastore = DbxDatastore.openDefault(dbxAccountManager.getLinkedAccount());
            datastore.addSyncStatusListener(mDatastoreListener);
            datastore.sync();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void stopSync() {
        if (datastore != null) {
            datastore.removeSyncStatusListener(mDatastoreListener);
            datastore.close();
        }
    }

    private DbxDatastore.SyncStatusListener mDatastoreListener = new DbxDatastore.SyncStatusListener() {
        @Override
        public void onDatastoreStatusChange(DbxDatastore ds) {
            Log.d(TAG, "SYNC STATUS: " + ds.getSyncStatus().toString());
            if (ds.getSyncStatus().hasIncoming) {
                try {
                    datastore.sync();
                } catch (DbxException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    };
}
