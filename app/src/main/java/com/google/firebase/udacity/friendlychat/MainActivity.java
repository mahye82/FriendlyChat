/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";

    /**
     * The default limit that the app should use for message length.
     * This can be modified by Remote Config.
     */
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    /**
     * The key used for a parameter which can be changed via Remote Config. The literal value of
     * this key must be "friendly_msg_length", since this is the name of the parameter key on the
     * on the Firebase Console's Remote Config.
     */
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    /**
     * An arbitrary request code to identify the request when the result is returned to the app
     * in onActivityResult(). This is used when we want FirebaseUI's sign-in screens to handle
     * authentication.
     */
    public static final int RC_SIGN_IN = 1;

    /**
     * An arbitrary request to identify the result of a startActivityForResult() call, when the
     * result is returned to this activity in onActivityResult(). This is used when we want to
     * use the system's default photo chooser.
     */
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    /**
     * The access point to the Firebase Realtime Database.
     */
    private FirebaseDatabase mFirebaseDatabase;

    /**
     * A reference to the "messages" portion of the database.
     */
    private DatabaseReference mMessagesDatabaseReference;

    /**
     * The entry point for all Firebase Authentication actions.
     */
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    /**
     * The entry point for all Firebase Storage actions.
     */
    private FirebaseStorage mFirebaseStorage;

    /**
     * A reference to the "chat_photos" portion of the storage.
     */
    private StorageReference mChatPhotosStorageReference;

    /**
     * A listener that will handle the callback behaviour when the children of any DatabaseReference
     * are altered. This listener will be attached to mMessagesDatabaseReference. Thus, this listens
     * to all changes of messages in the database.
     */
    private ChildEventListener mChildEventListener;

    /** The entry point for all Firebase Remote Config actions. */
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Initialize the Firebase components

        // Get an access point to the Firebase Realtime Database
        mFirebaseDatabase = FirebaseDatabase.getInstance();

        // Get an entry point to all Firebase Authentication actions
        mFirebaseAuth = FirebaseAuth.getInstance();

        // Get an entry point to all Firebase Storage actions
        mFirebaseStorage = FirebaseStorage.getInstance();

        // Get reference to a portion of the database called "messages"
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");

        // Get reference to a portion of the storage called "chat_photos"
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        // Get an entry point to Firebase Remote Config actions
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Create a new FriendlyMessage holding the text in the EditText, the current
                // username and the photo URL
                FriendlyMessage friendlyMessage =
                        new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                // Creates an auto-generated child location of messages, along with a key. Then we
                // attempt to add the FriendlyMessage as the value for this key in the database.
                mMessagesDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });

        // Create a listener to handle changes to the authentication state. The listener is attached
        // in onResume() and detached in onPause().
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in

                    onSignedInInitialize(user.getDisplayName());
                } else {
                    // User is signed out

                    onSignedOutCleanup();

                    // Create a list of authentication providers that we want FirebaseUI to handle
                    // the sign-in flow
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()
                    );

                    // Start FirebaseUI's authentication Activity for given providers.
                    // Pass in a flag, RC_SIGN_IN, so that when we get the result of this Activity
                    // in onActivityResult(), we know which Activity we're talking about.
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        // Create Remote Config Setting to enable developer mode.
        // Fetching configs from the server is normally limited to 5 requests per
        // hour. Enabling developer mode allows many more requests to be made per
        // hour, so developers can test different config values during development.
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        // Create a map of parameter keys for default values. These default config values are
        // what will be used by the app, unless they are changed via the Remote Config service
        // in the Firebase Console
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        fetchConfig();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sign_out_menu:
                // User pressed sign out option in menu
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // onActivityResult() is called BEFORE onResume(), so this will run before onResume()
        // attempts to add the AuthStateListener, which triggers a FirebaseUI sign-in screen.

        // If the user pressed the back button, we'll get RESULT_CANCELED. To prevent an endless
        // loop where onResume() just creates the sign-in screen again, we finish the Activity.
        if (requestCode == RC_SIGN_IN) {
            switch (resultCode) {
                case RESULT_OK:
                    Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
                    break;
                case RESULT_CANCELED:
                    Toast.makeText(this, "Sign-in canceled", Toast.LENGTH_SHORT).show();
                    finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER) {
            // Handling the result of the photo picker

            switch (resultCode) {
                case RESULT_OK:
                    // Get the URI of the selected image on the device
                    Uri selectedImageUri = data.getData();

                    // Get a reference to the "chat_photos" section of the Firebase Storage, then
                    // make a child which will be named after the last path segment of the URI.
                    // For example, if we have a URI like content://local_images/foo/4, the last
                    // path segment will be 4. This will be the filename we'll be saving our image
                    // as at this reference in the storage.
                    StorageReference photoRef =
                            mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());

                    // Asynchronously attempt to upload the file at the URI on the device to the
                    // StorageReference we just defined
                    UploadTask uploadTask = photoRef.putFile(selectedImageUri);

                    // Register observers to listen for when the download is done
                    uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            // When the image has successfully uploaded, get its download URL in the
                            // Firebase Storage
                            @SuppressWarnings("VisibleForTests") Uri downloadUrl =
                                    taskSnapshot.getDownloadUrl();

                            // Add the download URL in the Firebase Storage to the FriendlyMessage
                            // that we'll store in the database
                            if (downloadUrl != null) {
                                FriendlyMessage friendlyMessage =
                                        new FriendlyMessage(null, mUsername, downloadUrl.toString());
                                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                            }
                        }
                    });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // We need to be listening for changes in the sign-in state when the Activity is resumed
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // We don't need to be listening for changes in the sign-in state when the Activity is
        // paused
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        // Needs to be added here, so that when an Activity is destroyed in a way that has nothing
        // to do with signing-out, such as an app-rotation, the listener and adapter are cleaned up.
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    /**
     * Changes the display name in all sent messages to that of the newly signed-in user. Attaches
     * the listener reading messages from the Firebase Realtime Database.
     */
    private void onSignedInInitialize(String displayName) {
        // Ensures that all new messages sent attach the signed-in user's display name
        mUsername = displayName;
        // We should only start reading messages when signed-in
        attachDatabaseReadListener();
    }

    /**
     * Clears all messages currently displayed on screen, and detaches the listener reading messages
     * from the Firebase Realtime Database. Clears the name being used for sent messages and sets
     * it to ANONYMOUS.
     */
    private void onSignedOutCleanup() {
        // The user is no longer signed-in, so the name should be removed from the field storing
        // the
        mUsername = ANONYMOUS;

        // User who isn't signed in shouldn't see messages. No need to be reading messages when
        // signed in either.
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    /**
     * Creates and attaches a listener to the "messages" portion of the Firebase Realtime Database,
     * if one doesn't already exist.
     */
    private void attachDatabaseReadListener() {
        // If there's already a listener reading the database at child "messages", we don't need to
        // continue. We only need one listener at a time. Finish early.
        if (mChildEventListener != null) {
            return;
        }

        // Define the behaviour for the listener which will be attached to the "messages"
        // DatabaseReference
        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                // The value we get from DataSnapshot is an object holding the message from the
                // database.
                //
                // We can deserialize it to a FriendlyMessage object because the class
                // has the exact same fields found in the object returned from the database.
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);

                // Add the FriendlyMessage to the adapter to be displayed.
                mMessageAdapter.add(friendlyMessage);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };

        mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
    }

    /**
     * Detaches the listener attached to the "messages" node of the Firebase Realtime Database, if
     * one exists.
     */
    private void detachDatabaseReadListener() {
        // If there's no listener reading the database at child "messages", we don't need to
        // continue. Finish early.
        if (mChildEventListener == null) {
            return;
        }

        // Remove the listener reading the database at child "messages"
        mMessagesDatabaseReference.removeEventListener(mChildEventListener);
        mChildEventListener = null;
    }

    /**
     * Fetches the parameter values for the app from the Firebase project, as they were entered
     * into the Firebase Console. Handles both successful and failed fetch cases. Applies the
     * fetched parameter values (if any) to the app.
     */
    private void fetchConfig() {
        // If developer mode is enabled, we should reduce cacheExpiration to 0 so that each fetch
        // goes to the server immediately. This should not be used in release builds - production
        // builds should have a cache expiration of 1 hour. Fetching configs from the server is
        // normally limited to 5 requests per hour, thus we need to be careful about how many
        // configs we do in release builds. Developer mode allows many more configs, thus we can
        // fetch immediately.
        long cacheExpiration = 3600; // 1 hour in seconds
        boolean developerMode = mFirebaseRemoteConfig
                .getInfo()
                .getConfigSettings()
                .isDeveloperModeEnabled();

        if (developerMode) {
            cacheExpiration = 0;
        }

        // Attempt to fetch parameter values from the Firebase server. If the data in the cache was
        // last fetched more than 'cacheExpiration' seconds ago, a fetch from the Remote Config
        // Server will be attempted. Otherwise, this method will return the cached data.
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                // If the fetch succeeded,
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Copy the fetched config values (if any) to the FirebaseRemoteConfig, thus
                        // making these fetched config values available via FirebaseRemoteConfig
                        // get<type> methods, e.g., getLong(), getString().
                        mFirebaseRemoteConfig.activateFetched();

                        // Update the EditText length limit with
                        // the newly retrieved values from Remote Config.
                        applyRetrievedLengthLimit();
                    }
                })

                // If the fetch failed (for example, because the device is offline),
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // An error occurred when fetching the config.
                        Log.w(TAG, "Error fetching config", e);

                        // Update the EditText length limit with
                        // the newly retrieved values from Remote Config.
                        applyRetrievedLengthLimit();
                    }
                });

    }

    /**
     * Apply the retrieved length limit to EditText field. This result may be fresh from
     * the server or it may be from cached values.
     */
    private void applyRetrievedLengthLimit() {
        // Get whatever value is currently stored in the Firebase Remote Config. This could be a
        // recently fetched value if the fetch was successful. If it wasn't, it could be whatever
        // was cached as a result of a previous Remote Config operation.
        Long friendly_msg_length =
                mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);

        // Update the EditText's message length limit
        mMessageEditText.setFilters(new InputFilter[]{new
                InputFilter.LengthFilter(friendly_msg_length.intValue())});

        Log.d(TAG, FRIENDLY_MSG_LENGTH_KEY + " = " + friendly_msg_length);
    }
}
