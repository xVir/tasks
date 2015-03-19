/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.todoroo.andlib.data.Callback;
import com.todoroo.andlib.sql.QueryTemplate;
import com.todoroo.andlib.utility.AndroidUtilities;
import com.todoroo.astrid.actfm.TagSettingsActivity;
import com.todoroo.astrid.actfm.TagSettingsActivityTablet;
import com.todoroo.astrid.actfm.TagViewFragment;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.FilterListItem;
import com.todoroo.astrid.core.BuiltInFilterExposer;
import com.todoroo.astrid.core.CustomFilterActivity;
import com.todoroo.astrid.core.DeleteFilterActivity;
import com.todoroo.astrid.core.SavedFilter;
import com.todoroo.astrid.dao.TagDataDao;
import com.todoroo.astrid.data.RemoteModel;
import com.todoroo.astrid.data.StoreObject;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.gtasks.GtasksListFragment;
import com.todoroo.astrid.gtasks.GtasksPreferenceService;
import com.todoroo.astrid.tags.TagFilterExposer;
import com.todoroo.astrid.utility.Flags;
import com.todoroo.astrid.voice.ApiAiAssistant;
import com.todoroo.astrid.voice.VoiceInputAssistant;

import org.tasks.R;
import org.tasks.preferences.ActivityPreferences;
import org.tasks.ui.NavigationDrawerFragment;

import javax.inject.Inject;

import static com.todoroo.astrid.voice.VoiceInputAssistant.voiceInputAvailable;
import static org.tasks.ui.NavigationDrawerFragment.OnFilterItemClickedListener;

public class TaskListActivity extends AstridActivity implements OnPageChangeListener, OnFilterItemClickedListener {

    @Inject TagDataDao tagDataDao;
    @Inject ActivityPreferences preferences;
    @Inject GtasksPreferenceService gtasksPreferenceService;
    @Inject VoiceInputAssistant voiceInputAssistant;

    @Inject
    ApiAiAssistant apiAiAssistant;

    private NavigationDrawerFragment navigationDrawer;

    public static final String TOKEN_SWITCH_TO_FILTER = "newListCreated"; //$NON-NLS-1$

    /** For indicating the new list screen should be launched at fragment setup time */
    public static final String TOKEN_CREATE_NEW_LIST = "createNewList"; //$NON-NLS-1$
    public static final String TOKEN_CREATE_NEW_LIST_MEMBERS = "newListMembers"; //$NON-NLS-1$
    public static final String TOKEN_CREATE_NEW_LIST_NAME = "newListName"; //$NON-NLS-1$

    public static final String OPEN_TASK = "openTask"; //$NON-NLS-1$

    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences.applyTheme();

        setContentView(R.layout.task_list_wrapper);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        navigationDrawer = getNavigationDrawerFragment();
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        navigationDrawer.setUp(drawerLayout);

        initializeFragments();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            extras = (Bundle) extras.clone();
        }

        if (extras == null) {
            extras = new Bundle();
        }

        Filter savedFilter = getIntent().getParcelableExtra(TaskListFragment.TOKEN_FILTER);
        if (savedFilter == null) {
            savedFilter = getDefaultFilter();
            extras.putAll(configureIntentAndExtrasWithFilter(getIntent(), savedFilter));
        }

        extras.putParcelable(TaskListFragment.TOKEN_FILTER, savedFilter);

        setupTasklistFragmentWithFilter(savedFilter, extras);

        if (savedFilter != null) {
            setListsTitle(savedFilter.title);
        }

        Callback<Task> addTask = new Callback<Task>() {
            @Override
            public void apply(Task task) {
                getTaskListFragment().quickAddBar.quickAddTask(task);
            }
        };

        apiAiAssistant.setAddTaskCallback(addTask);

    }

    public NavigationDrawerFragment getNavigationDrawerFragment() {
        return (NavigationDrawerFragment) getSupportFragmentManager()
                .findFragmentById(NavigationDrawerFragment.FRAGMENT_NAVIGATION_DRAWER);
    }

    @Override
    protected void onResume() {
        super.onResume();

        getTaskListFragment().setSyncOngoing(gtasksPreferenceService.isOngoing());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isDrawerOpen()) {
            return super.onCreateOptionsMenu(menu);
        }

        getMenuInflater().inflate(R.menu.task_list_activity, menu);
        TaskListFragment tlf = getTaskListFragment();
        if (tlf instanceof GtasksListFragment) {
            menu.findItem(R.id.menu_clear_completed).setVisible(true);
            menu.findItem(R.id.menu_sort).setVisible(false);
        }
        menu.findItem(R.id.menu_voice_add).setVisible(voiceInputAvailable(this));
        final MenuItem item = menu.findItem(R.id.menu_search);
        final SearchView actionView = (SearchView) MenuItemCompat.getActionView(item);
        actionView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                query = query.trim();
                String title = getString(R.string.FLA_search_filter, query);
                Filter savedFilter = new Filter(title, title,
                        new QueryTemplate().where(Task.TITLE.like(
                                "%" + //$NON-NLS-1$
                                        query + "%")), //$NON-NLS-1$
                        null);
                onFilterItemClicked(savedFilter);
                MenuItemCompat.collapseActionView(item);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                return false;
            }
        });
        return true;
    }

    protected Filter getDefaultFilter() {
        return BuiltInFilterExposer.getMyTasksFilter(getResources());
    }

    protected void initializeFragments() {
        View editFragment = findViewById(R.id.taskedit_fragment_container);

        if(editFragment != null) {
            fragmentLayout = LAYOUT_DOUBLE;
        } else {
            fragmentLayout = LAYOUT_SINGLE;
        }
    }

    @Override
    public boolean onFilterItemClicked(FilterListItem item) {
        TaskEditFragment.removeExtrasFromIntent(getIntent());
        TaskEditFragment tef = getTaskEditFragment();
        if (tef != null) {
            onBackPressed();
        }

        return super.onFilterItemClicked(item);
    }

    @Override
    public void setupActivityFragment(TagData tagData) {
        super.setupActivityFragment(tagData);

        if (fragmentLayout == LAYOUT_DOUBLE) {
            View container = findViewById(R.id.taskedit_fragment_container);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (!Flags.checkAndClear(Flags.TLA_DISMISSED_FROM_TASK_EDIT)) {
            TaskEditFragment tea = getTaskEditFragment();
            if (tea != null) {
                onBackPressed();
            }
        }

        if (getIntent().hasExtra(TOKEN_SWITCH_TO_FILTER)) {
            Filter newList = getIntent().getParcelableExtra(TOKEN_SWITCH_TO_FILTER);
            getIntent().removeExtra(TOKEN_SWITCH_TO_FILTER);
            onFilterItemClicked(newList);
//        } else {
//            navigationDrawer.restoreLastSelected();
        }

        if (getIntent().hasExtra(OPEN_TASK)) {
            long id = getIntent().getLongExtra(OPEN_TASK, 0);
            if (id > 0) {
                onTaskListItemClicked(id);
            } else {
                TaskListFragment tlf = getTaskListFragment();
                if (tlf != null) {
                    tlf.quickAddBar.quickAddTask(); //$NON-NLS-1$
                }
            }
            if (fragmentLayout == LAYOUT_SINGLE) {
                getIntent().removeExtra(OPEN_TASK);
            }
        }

        if (getIntent().getBooleanExtra(TOKEN_CREATE_NEW_LIST, false)) {
            newListFromLaunch();
        }
    }

    private void newListFromLaunch() {
        Intent thisIntent = getIntent();
        Intent newTagIntent = newTagDialog();
        newTagIntent.putExtra(TagSettingsActivity.TOKEN_AUTOPOPULATE_MEMBERS, thisIntent.getStringExtra(TOKEN_CREATE_NEW_LIST_MEMBERS));
        newTagIntent.putExtra(TagSettingsActivity.TOKEN_AUTOPOPULATE_NAME, thisIntent.getStringExtra(TOKEN_CREATE_NEW_LIST_NAME));
        thisIntent.removeExtra(TOKEN_CREATE_NEW_LIST_MEMBERS);
        thisIntent.removeExtra(TOKEN_CREATE_NEW_LIST_NAME);
        startActivityForResult(newTagIntent, NavigationDrawerFragment.REQUEST_NEW_LIST);
    }

    /**
     * Create new tag data
     */
    private Intent newTagDialog() {
        Class<?> settingsComponent = preferences.useTabletLayout() ? TagSettingsActivityTablet.class : TagSettingsActivity.class;
        return new Intent(this, settingsComponent);
    }

    @Override
    public void onTaskListItemClicked(long taskId) {
        if (fragmentLayout != LAYOUT_SINGLE) {
            getIntent().putExtra(OPEN_TASK, taskId);
        }
        super.onTaskListItemClicked(taskId);
    }

    public void setListsTitle(String title) {
        getSupportActionBar().setTitle(title);
    }

    public void setSelectedItem(Filter item) {
        getSupportActionBar().setTitle(item.title);
    }

    @Override
    public void onPageSelected(int position) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset,
            int positionOffsetPixels) { /* Nothing */ }

    @Override
    public void onPageScrollStateChanged(int state) { /* Nothing */ }

    @Override
    public void onBackPressed() {
        if (navigationDrawer.isDrawerOpen()) {
            navigationDrawer.closeMenu();
            return;
        }

        // manage task edit visibility
        View taskeditFragmentContainer = findViewById(R.id.taskedit_fragment_container);
        if(taskeditFragmentContainer != null && taskeditFragmentContainer.getVisibility() == View.VISIBLE) {
            Flags.set(Flags.TLA_DISMISSED_FROM_TASK_EDIT);
            onPostResume();
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        AndroidUtilities.callOverridePendingTransition(this, R.anim.slide_right_in, R.anim.slide_right_out);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Callback<String> quickAddTask = new Callback<String>() {
            @Override
            public void apply(String title) {
                getTaskListFragment().quickAddBar.quickAddTask(title);
            }
        };

        if ((requestCode == NavigationDrawerFragment.REQUEST_NEW_LIST ||
                requestCode == TaskListFragment.ACTIVITY_REQUEST_NEW_FILTER) &&
                resultCode == Activity.RESULT_OK) {
            if(data == null) {
                return;
            }

            Filter newList = data.getParcelableExtra(TagSettingsActivity.TOKEN_NEW_FILTER);
            if (newList != null) {
                getIntent().putExtra(TOKEN_SWITCH_TO_FILTER, newList); // Handle in onPostResume()
                NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
                if (navigationDrawer != null) {
                    navigationDrawer.clear();
                }
            }
        } else if (requestCode == TaskListFragment.ACTIVITY_EDIT_TASK && resultCode != Activity.RESULT_CANCELED) {
            // Handle switch to assigned filter when it comes from TaskEditActivity finishing
            // For cases when we're in a multi-frame layout, the TaskEditFragment will notify us here directly
            TaskListFragment tlf = getTaskListFragment();
            if (tlf != null) {
                if (data != null) {
                    if (data.getParcelableExtra(TaskEditFragment.TOKEN_NEW_REPEATING_TASK) != null) {
                        Task repeating = data.getParcelableExtra(TaskEditFragment.TOKEN_NEW_REPEATING_TASK);
                        dateChangedAlerts.showRepeatChangedDialog(this, repeating);
                    }
                    if (data.getBooleanExtra(TaskEditFragment.TOKEN_TAGS_CHANGED, false)) {
                        tagsChanged(true);
                    }
                }
                tlf.refresh();
            }
        } else if (requestCode == NavigationDrawerFragment.REQUEST_CUSTOM_INTENT && resultCode == RESULT_OK && data != null) {
            // Tag renamed or deleted
            String action = data.getAction();
            String uuid = data.getStringExtra(TagViewFragment.EXTRA_TAG_UUID);
            TaskListFragment tlf = getTaskListFragment();

            if (AstridApiConstants.BROADCAST_EVENT_TAG_DELETED.equals(action)) {
                NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
                if (tlf != null) {
                    TagData tagData = tlf.getActiveTagData();
                    String activeUuid = RemoteModel.NO_UUID;
                    if (tagData != null) {
                        activeUuid = tagData.getUuid();
                    }

                    if (activeUuid.equals(uuid)) {
                        getIntent().putExtra(TOKEN_SWITCH_TO_FILTER, BuiltInFilterExposer.getMyTasksFilter(getResources())); // Handle in onPostResume()
                        navigationDrawer.clear(); // Should auto refresh
                    } else {
                        tlf.refresh();
                    }
                }

                if (navigationDrawer != null) {
                    navigationDrawer.refresh();
                }
            } else if (AstridApiConstants.BROADCAST_EVENT_TAG_RENAMED.equals(action)) {
                if (tlf != null) {
                    TagData td = tlf.getActiveTagData();
                    if (td != null && td.getUuid().equals(uuid)) {
                        td = tagDataDao.fetch(uuid, TagData.PROPERTIES);
                        if (td != null) {
                            Filter filter = TagFilterExposer.filterFromTagData(this, td);
                            getIntent().putExtra(TOKEN_SWITCH_TO_FILTER, filter);
                        }
                    } else {
                        tlf.refresh();
                    }
                }

                NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
                if (navigationDrawer != null) {
                    navigationDrawer.refresh();
                }
            } else if (AstridApiConstants.BROADCAST_EVENT_FILTER_DELETED.equals(action)) {
                StoreObject storeObject = (StoreObject) data.getExtras().get(DeleteFilterActivity.TOKEN_STORE_OBJECT);
                Filter filter = SavedFilter.load(storeObject);
                if (tlf.getFilter().equals(filter)) {
                    getIntent().putExtra(TOKEN_SWITCH_TO_FILTER, BuiltInFilterExposer.getMyTasksFilter(getResources())); // Handle in onPostResume()
                }
                NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
                if (navigationDrawer != null) {
                    navigationDrawer.refresh();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void tagsChanged() {
        tagsChanged(false);
    }

    private void tagsChanged(boolean onActivityResult) {
        NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
        if (navigationDrawer != null) {
            if (onActivityResult) {
                navigationDrawer.clear();
            } else {
                navigationDrawer.refresh();
            }
        }
    }

    protected void refreshTaskList() {
        TaskListFragment tlf = getTaskListFragment();
        if (tlf != null) {
            tlf.refresh();
        }
    }

    public void refreshFilterCount() {
        NavigationDrawerFragment navigationDrawer = getNavigationDrawerFragment();
        if (navigationDrawer != null) {
            navigationDrawer.refreshFilterCount();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TaskListFragment tlf = getTaskListFragment();
        switch(item.getItemId()) {
            case R.id.menu_voice_add:
                apiAiAssistant.startRecognition();
                return true;
            case R.id.menu_sort:
                AlertDialog dialog = SortSelectionActivity.createDialog(
                        this, tlf.hasDraggableOption(), preferences, tlf, tlf.getSortFlags(), tlf.getSort());
                dialog.show();
                return true;
            case R.id.menu_new_filter:
                Intent intent = new Intent(this, CustomFilterActivity.class);
                startActivityForResult(intent, TaskListFragment.ACTIVITY_REQUEST_NEW_FILTER);
                return true;
            case R.id.menu_new_list:
                startActivityForResult(newTagDialog(), NavigationDrawerFragment.REQUEST_NEW_LIST);
                if (!preferences.useTabletLayout()) {
                    AndroidUtilities.callOverridePendingTransition(this, R.anim.slide_left_in, R.anim.slide_left_out);
                }
                return true;
            case R.id.menu_support:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://abaker.github.io/tasks/")));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            TaskEditFragment tef = getTaskEditFragment();
            if (tef != null && tef.onKeyDown(keyCode)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean isDrawerOpen() {
        return navigationDrawer.isDrawerOpen();
    }
}
