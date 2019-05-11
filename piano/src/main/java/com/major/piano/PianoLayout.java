package com.major.piano;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @desc: keyboard 界面
 * @author: Major
 * @since: 2019/5/11 10:58
 */
public class PianoLayout extends View {

    // List of formerly pressed keys
    private static Set<Integer> old_pressed;
    protected String octaves;
    protected String damper; // boolean preferred, but no boolean array resource possible

    // Preference variables
    protected String lowerOctavePosition;

    // a Paint object is needed to be able to draw anything
    private Paint pianoPaint;
    // view dimensions (fixed)
    private int pianoWidth, pianoHeight;
    // Path objects that define the shapes of the keys
    private Path symmetricWhiteKey, asymCFWhiteKey, asymEBWhiteKey, blackKey;
    // notes in the keyboard
    private int numberOfNotes;

    private int numberOfBlackKeys;
    private ArrayList<Integer> blackKeyNoteNumbers;
    // keys
    private ArrayList<Path> keys;
    // sounds
    private SoundPool pianoSounds;
    // sound identifications array, to associate a piano key with its sound
    private ArrayList<Integer> soundIds; // returned by sound pool load
    private ArrayList<Integer> playIds; // returned by sound pool play
    // objects needed to draw outside of onDraw
    private Bitmap pianoBitmap;
    private Canvas pianoCanvas;
    // list of keys that must be shown as pressed
    private ArrayList<Integer> justPressedKeys;

    public PianoLayout(Context context) {
        this(context, null);
    }

    public PianoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        SL.i("init 开始");
        init(context);
        SL.i("init 结束");
    }

    private void init(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        lowerOctavePosition = sharedPreferences.getString("pref_rows",
                context.getString(R.string.pref_rows_default_value));
        damper = sharedPreferences.getString("pref_damper",
                context.getString(R.string.pref_damper_default_value));
        octaves = sharedPreferences.getString("pref_octaves",
                context.getString(R.string.pref_octaves_default_value));

        // Initialization
        pianoPaint = new Paint();
        pianoPaint.setStrokeWidth(2.0f); // stroke width used when Style is Stroke or StrokeAndFill, in pixels?
        symmetricWhiteKey = new Path();
        asymCFWhiteKey = new Path();
        asymEBWhiteKey = new Path();
        blackKey = new Path();
        keys = new ArrayList<>();
        numberOfNotes = 24; // two octaves
        numberOfBlackKeys = 10; // two octaves
        blackKeyNoteNumbers = new ArrayList<>();
        old_pressed = new HashSet<>();
        justPressedKeys = new ArrayList<>();
        pianoSounds = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
        soundIds = new ArrayList<>();
        playIds = new ArrayList<>();
        for (int i = 0; i < numberOfNotes; i++) {
            int resourceId;
            // Load the sound of each note, saving the identifications
            // (audio files saved as res/raw/note0.ogg etc.)
            if (octaves != null && octaves.equals(getContext().getString(R.string.pref_octaves_34_value))) {
                resourceId = context.getResources().getIdentifier("note"
                                + Integer.toString(i),
                        "raw", context.getPackageName());
                soundIds.add(pianoSounds.load(context, resourceId, 1));
            }
            if (octaves != null && octaves.equals(getContext().getString(R.string.pref_octaves_45_value))) {
                resourceId = context.getResources().getIdentifier("note"
                                + Integer.toString(i + 12),
                        "raw", context.getPackageName());
                soundIds.add(pianoSounds.load(context, resourceId, 1));
            }
            if (octaves != null && octaves.equals(getContext().getString(R.string.pref_octaves_35_value))) {
                resourceId = context.getResources().getIdentifier("note"
                                + Integer.toString(i + (i / 12) * 12),
                        "raw", context.getPackageName());
                soundIds.add(pianoSounds.load(context, resourceId, 1));
            }
            playIds.add(null);
            // Create key objects
            keys.add(new Path());
            // Record the note numbers of the black keys
            switch (i % 12) {
                case 1:
                case 3:
                case 6:
                case 8:
                case 10:
                    blackKeyNoteNumbers.add(Integer.valueOf(i));
                    break;
                default:
                    break;
            }
        }
        pianoCanvas = new Canvas();
    }

    // Draw on canvas, from bitmap
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // draw the keyboard on the bitmap
        drawOnBitmap();
        // draw the bitmap to the real canvas c
        canvas.drawBitmap(pianoBitmap, 0, 0, null);
    }

    // React when the user touches, stops touching, or touches in a new way,
    // the piano keyboard
    public boolean onTouchEvent(MotionEvent event) {
        // React only to some actions, ignoring the others
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        if (!(
                actionCode == MotionEvent.ACTION_DOWN ||
                        actionCode == MotionEvent.ACTION_POINTER_DOWN ||
                        actionCode == MotionEvent.ACTION_UP ||
                        actionCode == MotionEvent.ACTION_POINTER_UP ||
                        actionCode == MotionEvent.ACTION_MOVE)) {
            return false;
        }
        // Use of maps to keep track of:
        //       all affected keys:  pressed_map
        //       pressed keys:       new_pressed
        Set<Integer> new_pressed = new HashSet<Integer>();
        HashMap<Integer, Float> pressed_map = new HashMap<Integer, Float>();
        // For every pointer, see which key the point belongs to
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
            // Get coordinates of the point for the current pointer
            int x = (int) event.getX(pointerIndex);
            int y = (int) event.getY(pointerIndex);
            int keyWasFound = 0; // flag to do something with a key
            int noteFound = 0; // variable to store the note number of the affected key
            // Check black keys first, because white keys' bounding rectangles overlap with
            // black keys
            int blackKeyFound = 0; // flag to skip white keys check
            // Check black keys
            for (int i = 0; i < numberOfBlackKeys; i++) {
                RectF bounds = new RectF();
                keys.get(blackKeyNoteNumbers.get(i)).computeBounds(bounds, true);
                if (bounds.contains((float) x, (float) y)) {
                    blackKeyFound = 1;
                    keyWasFound = 1;
                    noteFound = blackKeyNoteNumbers.get(i);
                }
            }
            // Check white keys, if necessary
            for (int i = 0; (i < numberOfNotes) && (blackKeyFound == 0); i++) {
                if (blackKeyNoteNumbers.contains(i)) {
                    continue; // skip black keys -already checked
                }
                RectF bounds = new RectF();
                keys.get(i).computeBounds(bounds, true);
                if (bounds.contains((float) x, (float) y)) {
                    keyWasFound = 1;
                    noteFound = i;
                }
            }
            // Save found key
            if (keyWasFound == 1) {
                // save note number and pressure in all affected keys map
                if (pressed_map.containsKey(noteFound)) {
                    pressed_map.put(noteFound,
                            Math.max(event.getPressure(pointerIndex),
                                    pressed_map.get(noteFound)));
                } else {
                    pressed_map.put(noteFound, event.getPressure(pointerIndex));
                }
                // if appropriate, save note number in pressed keys map
                if ((pointerIndex != event.getActionIndex() || (
                        actionCode != MotionEvent.ACTION_UP &&
                                actionCode != MotionEvent.ACTION_POINTER_UP))) {
                    new_pressed.add(noteFound);
                }
            }
        }
        // Map of newly pressed keys (pressed keys that weren't already pressed)
        Set<Integer> just_pressed = new HashSet<Integer>(new_pressed);
        just_pressed.removeAll(old_pressed);
        // Play the sound of each newly pressed key
        Iterator<Integer> it = just_pressed.iterator();
        justPressedKeys.clear(); // empty the list of just pressed keys (used to draw them)
        while (it.hasNext()) {
            int i = it.next();
            justPressedKeys.add(i); // add the key (note number) to the list so that it can be shown as pressed
            try {
                playIds.set(i, pianoSounds.play(soundIds.get(i), 1.0f, 1.0f, 1, 0, 1.0f));
            } catch (Exception e) {
                Log.e("onTouchEvent", "Key " + i + " not playable!");
            }
        }
        // Stop the sound of released keys
        if (damper.equals(
                getContext().getString(R.string.pref_damper_dampen_value))) {
            Set<Integer> just_released = new HashSet<Integer>(old_pressed);
            just_released.removeAll(new_pressed);
            it = just_released.iterator();
            while (it.hasNext()) {
                int i = it.next();
                pianoSounds.stop(playIds.get(i));
            }
        }
        // Update map of pressed keys
        old_pressed = new_pressed;
        // Force a call to onDraw() to give visual feedback to the user
        this.invalidate();
        return true;
    }

    // Create shapes
    private void createShapes() {
        // Define the shapes
        // ___
        // |  |
        // |  |_
        // |    |
        // |____|
        //
        asymCFWhiteKey.moveTo(0.0f, 0.0f);
        asymCFWhiteKey.lineTo((float) pianoWidth * 17 / 168, 0.0f);
        asymCFWhiteKey.lineTo((float) pianoWidth * 17 / 168, (float) pianoHeight / 4);
        asymCFWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 4);
        asymCFWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
        asymCFWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
        asymCFWhiteKey.lineTo(0.0f, 0.0f);
        //    __
        //   |  |
        //  _|  |
        // |    |
        // |____|
        //
        asymEBWhiteKey.moveTo((float) pianoWidth / 24, 0.0f);
        asymEBWhiteKey.lineTo((float) pianoWidth / 7, 0.0f);
        asymEBWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
        asymEBWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
        asymEBWhiteKey.lineTo(0.0f, (float) pianoHeight / 4);
        asymEBWhiteKey.lineTo((float) pianoWidth / 24, (float) pianoHeight / 4);
        asymEBWhiteKey.lineTo((float) pianoWidth / 24, 0.0f);
        asymEBWhiteKey.offset((float) pianoWidth * 2 / 7, 0.0f);
        //  __
        // |  |
        // |  |
        // |__|
        //
        blackKey.addRect(0.0f, 0.0f, (float) pianoWidth / 12, (float) pianoHeight / 4, Path.Direction.CW);
        blackKey.offset((float) pianoWidth * 17 / 168, 0.0f);
        //
        //    __
        //   |  |
        //  _|  |_
        // |      |
        // |______|
        //
        symmetricWhiteKey.moveTo((float) pianoWidth / 24, 0.0f);
        symmetricWhiteKey.lineTo((float) pianoWidth * 17 / 168, 0.0f);
        symmetricWhiteKey.lineTo((float) pianoWidth * 17 / 168, (float) pianoHeight / 4);
        symmetricWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 4);
        symmetricWhiteKey.lineTo((float) pianoWidth / 7, (float) pianoHeight / 2);
        symmetricWhiteKey.lineTo(0.0f, (float) pianoHeight / 2);
        symmetricWhiteKey.lineTo(0.0f, (float) pianoHeight / 4);
        symmetricWhiteKey.lineTo((float) pianoWidth / 24, (float) pianoHeight / 4);
        symmetricWhiteKey.lineTo((float) pianoWidth / 24, 0.0f);
        symmetricWhiteKey.offset((float) pianoWidth / 7, 0.0f);
        // Save the shapes as keys
        for (int i = 0; i < (numberOfNotes / 2); i++) {
            if (blackKeyNoteNumbers.contains(i)) {
                // Black keys
                switch (i) {
                    case 3: // D# = Eb
                    case 8: // G# = Ab
                    case 10: // A# = Bb
                        blackKey.offset((float) pianoWidth / 7, 0.0f);
                        break;
                    case 6: // F# = Gb
                        blackKey.offset((float) pianoWidth * 2 / 7, 0.0f);
                        break;
                    default:
                        break;
                }
                keys.get(i).set(blackKey);
                blackKey.offset(0.0f, (float) pianoHeight / 2);
                keys.get(i + 12).set(blackKey);
                blackKey.offset(0.0f, (float) -pianoHeight / 2);
            } else {
                // White keys
                if ((i == 0) || (i == 5)) {
                    // CF key
                    if (i == 5) { // F
                        asymCFWhiteKey.offset((float) pianoWidth * 3 / 7, 0.0f);
                    }
                    keys.get(i).set(asymCFWhiteKey);
                    asymCFWhiteKey.offset(0.0f, (float) pianoHeight / 2);
                    keys.get(i + 12).set(asymCFWhiteKey);
                    asymCFWhiteKey.offset(0.0f, (float) -pianoHeight / 2);
                }
                if ((i == 2) || (i == 7) || (i == 9)) {
                    // symmetric key
                    switch (i) {
                        case 7: // G
                            symmetricWhiteKey.offset((float) pianoWidth * 3 / 7, 0.0f);
                            break;
                        case 9: // A
                            symmetricWhiteKey.offset((float) pianoWidth / 7, 0.0f);
                            break;
                        default:
                            break;
                    }
                    keys.get(i).set(symmetricWhiteKey);
                    symmetricWhiteKey.offset(0.0f, (float) pianoHeight / 2);
                    keys.get(i + 12).set(symmetricWhiteKey);
                    symmetricWhiteKey.offset(0.0f, (float) -pianoHeight / 2);
                }
                if ((i == 4) || (i == 11)) {
                    // EB key
                    if (i == 11) { // B
                        asymEBWhiteKey.offset((float) pianoWidth * 4 / 7, 0.0f);
                    }
                    keys.get(i).set(asymEBWhiteKey);
                    asymEBWhiteKey.offset(0.0f, (float) pianoHeight / 2);
                    keys.get(i + 12).set(asymEBWhiteKey);
                    asymEBWhiteKey.offset(0.0f, (float) -pianoHeight / 2);
                }
            }
        }
        // Exchange rows depending on preference
        if (lowerOctavePosition.equals(
                getContext().getString(
                        R.string.pref_rows_higher_octave_in_upper_row_value))) {
            // Swap rows
            for (int i = 0; i < (numberOfNotes / 2); i++) {
                Collections.swap(keys, i, i + 12);
            }
        }
    }

    // Draw on bitmap
    protected void drawOnBitmap() {
        // Erase the canvas
        pianoCanvas.drawColor(Color.WHITE);
        // Draw the keys
        for (int i = 0; i < numberOfNotes; i++) {
            if (blackKeyNoteNumbers.contains(i)) {
                // Black keys
                pianoPaint.setStyle(Paint.Style.FILL); // all filled; ignore all stroke-related settings in the paint
                if (justPressedKeys.contains(i)) {
                    pianoPaint.setColor(Color.LTGRAY);
                } else {
                    pianoPaint.setColor(Color.BLACK);
                }
            } else {
                // White keys
                if (justPressedKeys.contains(i)) {
                    pianoPaint.setStyle(Paint.Style.FILL); // all filled; ignore all stroke-related settings in the paint
                    pianoPaint.setColor(Color.DKGRAY);
                } else {
                    pianoPaint.setStyle(Paint.Style.STROKE); // stroked
                    pianoPaint.setColor(Color.BLACK);
                }
            }
            pianoCanvas.drawPath(keys.get(i), pianoPaint);
        }
    }

    // Free resources
    public void destroy() {
        if (pianoBitmap != null) {
            pianoBitmap.recycle(); // mark the bitmap as dead
        }
        if (pianoSounds != null) {
            pianoSounds.release(); // release the sound pool resources
        }
    }

    // Deal with view size changes
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (pianoBitmap != null) {
            pianoBitmap.recycle(); // mark the bitmap as dead
        }
        pianoWidth = w;
        pianoHeight = h;
        pianoBitmap = Bitmap.createBitmap(pianoWidth, pianoHeight, Bitmap.Config.ARGB_8888);
        pianoCanvas.setBitmap(pianoBitmap);
        this.createShapes();
        this.drawOnBitmap();
        // Inform the user about the key combination to access the menu
        // (because the keyboard takes up the whole screen)
        String text = "Vol" + "\u2191" + "+" + "Vol" + "\u2193" + " " + "\u2192" + " menu";
//        Toast.makeText(getContext().getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    public void setLowerOctavePosition(String lowerOctavePosition) {
        this.lowerOctavePosition = lowerOctavePosition;
    }

    public void setDamper(String damper) {
        this.damper = damper;
    }

    public String getLowerOctavePosition() {
        return lowerOctavePosition;
    }

    public int getNumberOfNotes() {
        return numberOfNotes;
    }

    public List<Path> getKeys() {
        return keys;
    }

    public String getOctaves() {
        return octaves;
    }

    public List<Integer> getSoundIds() {
        return soundIds;
    }

    public SoundPool getPianoSounds() {
        return pianoSounds;
    }
}
