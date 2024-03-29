/*
 * Copyright (C) 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.cloudnotes.ui;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.example.android.cloudnotes.R;
import com.example.android.cloudnotes.provider.NotesProvider;
import com.example.android.cloudnotes.service.DriveSyncService;
import com.example.android.cloudnotes.ui.NoteListFragment.NoteEventsCallback;
import com.example.android.cloudnotes.utils.UiUtils;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class HomeActivity extends Activity implements NoteEventsCallback {

    // extra for the above action
    public static final String EXTRA_NOTE_ID = "noteId";

    // key for adding NoteEditFragment to this Activity
    private static final String NOTE_EDIT_TAG = "Edit";

    // key used for saving instance state
    public static final String KEY_SYNCING = "SYNCING";

    // key identifying our shared prefs file
    public static final String KEY_PREFS = "PREFS";

    /* Keys for local broadcasts */
    public static final String LB_REQUEST_ACCOUNT = "REQUEST_ACCOUNT";
    public static final String LB_AUTH_APP = "AUTH_APP";
    public static final String EXTRA_AUTH_APP_INTENT = "AUTH_APP_INTENT";
    public static final String LB_SYNC_COMPLETE = "SYNC_COMPLETE";

    /* Activity Request Codes */
    private static final int CHOOSE_ACCOUNT = 0;
    private static final int AUTH_APP = 1;

    private static boolean mTwoPaneView;

    private MenuItem mSyncMenuItem;

    private boolean mIsSyncing = false;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LB_REQUEST_ACCOUNT.equals(action)) {
                setSyncingState(false);
                chooseAccount();
            } else if (LB_AUTH_APP.equals(action)) {
                setSyncingState(false);
                Intent authAppIntent = intent.getParcelableExtra(EXTRA_AUTH_APP_INTENT);
                startActivityForResult(authAppIntent, AUTH_APP);
            } else if (LB_SYNC_COMPLETE.equals(action)) {
                setSyncingState(false);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        mTwoPaneView = UiUtils.isHoneycombTablet(this);
        if (NoteEditFragment.ACTION_VIEW_NOTE.equals(getIntent().getAction())) {
            viewNote(getIntent());
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_SYNCING)) {
            mIsSyncing = savedInstanceState.getBoolean(KEY_SYNCING);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NoteEditFragment.ACTION_VIEW_NOTE.equals(intent.getAction())) {
            viewNote(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LB_REQUEST_ACCOUNT));
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LB_AUTH_APP));
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(LB_SYNC_COMPLETE));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home, menu);
        mSyncMenuItem = menu.findItem(R.id.ab_sync);
        mSyncMenuItem
                .setVisible(GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS);
        // set correct state on config changes
        setSyncingState(mIsSyncing);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_SYNCING, mIsSyncing);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ab_add_note:
                if (mTwoPaneView) {
                    showNote(null);
                    NoteListFragment list = (NoteListFragment) getFragmentManager()
                            .findFragmentById(R.id.list);
                    list.clearActivation();
                    return true;
                } else {
                    startActivity(new Intent(NoteEditFragment.ACTION_CREATE_NOTE));
                }
                break;
            case R.id.ab_sync:
                startDriveSync();
                break;
            case R.id.ab_settings:
                // TODO add settings
                break;
            default:
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private void startDriveSync() {
        setSyncingState(true);
        startService(new Intent(this, DriveSyncService.class));
    }

    private void setSyncingState(boolean syncing) {
        if (syncing) {
            mSyncMenuItem.setActionView(R.layout.actionview_indeterminate_progress);
        } else {
            mSyncMenuItem.setActionView(null);
        }
        mIsSyncing = syncing;
    }

    private void chooseAccount() {
        startActivityForResult(AccountPicker.newChooseAccountIntent(null, null, new String[] {
            GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE
        }, false, null, null, null, null), CHOOSE_ACCOUNT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CHOOSE_ACCOUNT:
                if (resultCode == RESULT_OK && data != null
                        && data.hasExtra(AccountManager.KEY_ACCOUNT_NAME)) {
                    final String syncAccount = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (!TextUtils.isEmpty(syncAccount)) {
                        SharedPreferences.Editor prefs = getSharedPreferences(KEY_PREFS,
                                MODE_PRIVATE).edit();
                        prefs.putString(AccountManager.KEY_ACCOUNT_NAME, syncAccount);
                        prefs.commit();

                        // resume syncing
                        startDriveSync();
                    }
                }
                break;
            case AUTH_APP:
                if (resultCode == RESULT_OK) {
                    // resume syncing
                    startDriveSync();
                }
                break;
            default:
                break;
        }
    }

    private void viewNote(Intent launchIntent) {
        final long noteId = launchIntent.getLongExtra(EXTRA_NOTE_ID, -1);
        showNote(ContentUris.withAppendedId(NotesProvider.CONTENT_URI, noteId));
    }

    /**
     * This method controls both fragments, instructing them to display a
     * certain note.
     * 
     * @param noteUri The {@link Uri} of the note to show. To create a new note,
     *            pass {@code null}.
     */
    private void showNote(final Uri noteUri) {

        if (mTwoPaneView) {
            // check if the NoteEditFragment has been added
            FragmentManager fm = getFragmentManager();
            NoteEditFragment edit = (NoteEditFragment) fm.findFragmentByTag(NOTE_EDIT_TAG);
            final boolean editNoteAdded = (edit != null);

            if (editNoteAdded) {
                if (edit.mCurrentNote != null && edit.mCurrentNote.equals(noteUri)) {
                    // clicked on the currently selected note
                    return;
                }

                NoteEditFragment editFrag = (NoteEditFragment) fm.findFragmentByTag(NOTE_EDIT_TAG);
                if (noteUri != null) {
                    // load an existing note
                    editFrag.loadNote(noteUri);
                    NoteListFragment list = (NoteListFragment) fm.findFragmentById(R.id.list);
                    list.setActivatedNote(Long.valueOf(noteUri.getLastPathSegment()));
                } else {
                    // creating a new note - clear the form & list
                    // activation
                    if (editNoteAdded) {
                        editFrag.clear();
                    }
                    NoteListFragment list = (NoteListFragment) fm.findFragmentById(R.id.list);
                    list.clearActivation();
                }
            } else {
                // add the NoteEditFragment to the container
                FragmentTransaction ft = fm.beginTransaction();
                edit = new NoteEditFragment();
                ft.add(R.id.note_detail_container, edit, NOTE_EDIT_TAG);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.commit();
                edit.loadNote(noteUri);
            }
        } else {
            startActivity(new Intent(NoteEditFragment.ACTION_VIEW_NOTE, noteUri));
        }
    }

    /**
     * Callback from child fragment
     */
    public void onNoteSelected(Uri noteUri) {
        showNote(noteUri);
    }

    /**
     * Callback from child fragment
     */
    public void onNoteDeleted() {
        // remove the NoteEditFragment after a deletion
        FragmentManager fm = getFragmentManager();
        NoteEditFragment edit = (NoteEditFragment) fm.findFragmentByTag(NOTE_EDIT_TAG);
        if (edit != null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(edit);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        }
    }

    @Override
    public void onNoteCreated(Uri noteUri) {
        NoteListFragment list = (NoteListFragment) getFragmentManager().findFragmentById(R.id.list);
        list.setActivatedNoteAfterLoad(Long.valueOf(noteUri.getLastPathSegment()));
    }

}
