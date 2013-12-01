package org.tasks.dropbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.DependencyInjectionService;

public class DropBoxLinkActivity extends Activity {

    private static final String LINK_IN_PROGRESS = "LINK_IN_PROGRESS";

    public static final int REQUEST_LINK_TO_DBX = 0;

    @Autowired
    DropBoxSyncManager dropboxSyncManager;

    public DropBoxLinkActivity() {
        DependencyInjectionService.getInstance().inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.getBoolean(LINK_IN_PROGRESS, false)) {
            return;
        }
        if (!dropboxSyncManager.hasLink()) {
            dropboxSyncManager.startLink(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(LINK_IN_PROGRESS, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LINK_TO_DBX) {
            if (resultCode == RESULT_OK) {
                dropboxSyncManager.linkSuccessful();
                setResult(RESULT_OK);
            } else {
                setResult(RESULT_CANCELED);
            }
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
