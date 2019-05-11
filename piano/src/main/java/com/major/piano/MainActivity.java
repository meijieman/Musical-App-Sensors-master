package com.major.piano;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import java.util.Collections;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

    private PianoLayout pianoView;

    protected String orientation;
    // Preference data interface
    static SharedPreferences sharedPreferences;
    // Flags to detect key presses
    private boolean upPressed;
    private boolean downPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load preferences. The following:
//		sharedPreferences = getSharedPreferences("Piano", Activity.MODE_PRIVATE); // if the file doesn't exist it'll be created when retrieving an editor and commiting changes
        // ...didn't call the on change listener. So:
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        // Save preferences into variables

        orientation = sharedPreferences.getString("pref_orient",
                this.getString(R.string.pref_orient_default_value));
        // Make volume button always control just the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Flags initialization
        upPressed = false;
        downPressed = false;
        // Show the view in full screen mode without title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Set preferred orientation
        if (orientation.equals(this.getString(R.string.pref_orient_landscape_value))) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if (orientation.equals(this.getString(R.string.pref_orient_portrait_value))) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        // Set view
        setContentView(R.layout.activity_main);

        pianoView = findViewById(R.id.pv_main);

        Scoresheet scoresheet = findViewById(R.id.s_main);
        scoresheet.setTrack("4C4 4D4 4E4 4F4 4G4 4A4 4B4");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pianoView.destroy();
    }

    // Initialize options menu contents
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Not checking item argument because there's only one option in the menu
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        return true;
    }

    // Implement the method that is called when a shared preference is changed, added or removed
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_damper")) {
            String damper = sharedPreferences.getString(key,
                    this.getString(R.string.pref_damper_default_value));
            pianoView.setDamper(damper);
        }
        if (key.equals("pref_rows")) {
            // See if the preference was really changed or just the dialog shown
            String lowerOctavePosition = pianoView.getLowerOctavePosition();
            String pos = sharedPreferences.getString(key, this.getString(R.string.pref_rows_default_value));
            if (!(lowerOctavePosition.equals(pos))) {
                // Update variable
                pianoView.setLowerOctavePosition(pos);
                // Swap rows
                for (int i = 0; i < (pianoView.getNumberOfNotes() / 2); i++) {
                    Collections.swap(pianoView.getKeys(), i, i + 12);
                }
            }
        }
        if (key.equals("pref_octaves")) {
            // See if the preference was really changed or just the dialog shown
            String str = sharedPreferences.getString(key, this.getString(R.string.pref_octaves_default_value));
            String octaves = pianoView.getOctaves();
            if (!(octaves.equals(str))) {
                // Update variable
                octaves = str;
                // Release old sounds and clear their identifications
                for (int id : pianoView.getSoundIds()) {
                    pianoView.getPianoSounds().unload(id);
                }
                pianoView.getSoundIds().clear();
                // Load new sounds and save their identifications
                for (int i = 0; i < pianoView.getNumberOfNotes(); i++) {
                    int resourceId;
                    if (octaves.equals(this.getString(R.string.pref_octaves_34_value))) {
                        resourceId = this.getApplicationContext().getResources().
                                getIdentifier("note"
                                                + i,
                                        "raw", this.getApplicationContext().getPackageName());
                        pianoView.getSoundIds().add(pianoView.getPianoSounds().load(
                                this.getApplicationContext(), resourceId, 1));
                    }
                    if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_45_value))) {
                        resourceId = this.getApplicationContext().getResources().
                                getIdentifier("note"
                                                + Integer.toString(i + 12),
                                        "raw", this.getApplicationContext().getPackageName());
                        pianoView.getSoundIds().add(pianoView.getPianoSounds().load(
                                this.getApplicationContext(), resourceId, 1));
                    }
                    if (octaves.equals(MainActivity.this.getString(R.string.pref_octaves_35_value))) {
                        resourceId = this.getApplicationContext().getResources().
                                getIdentifier("note"
                                                + Integer.toString(i + (i / 12) * 12),
                                        "raw", this.getApplicationContext().getPackageName());
                        pianoView.getSoundIds().add(pianoView.getPianoSounds().load(
                                this.getApplicationContext(), resourceId, 1));
                    }
                }
            }
        }
        if (key.equals("pref_orient")) {
            // Update variable
            orientation = sharedPreferences.getString(key,
                    this.getString(R.string.pref_orient_default_value));
            // Set preferred screen orientation
            if (orientation.equals(this.getString(R.string.pref_orient_landscape_value))) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
            if (orientation.equals(this.getString(R.string.pref_orient_portrait_value))) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    // Respond to special key press combination when there is no hardware menu key to show the menu
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Set flags according to key presses and releases
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                upPressed = true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                upPressed = false;
            }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                downPressed = true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                downPressed = false;
            }
        }
        // Show the options menu when both volume keys are pressed and there is no hardware menu key
        if ((upPressed == true) && (downPressed == true)
// hasPermanentMenuKey is only available from API 14
//				&&
//				!(ViewConfiguration.get(this.getApplicationContext()).hasPermanentMenuKey())
        ) {
            // reset flags
            upPressed = false;
            downPressed = false;
            // show the menu
            this.openOptionsMenu();
            // return
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}