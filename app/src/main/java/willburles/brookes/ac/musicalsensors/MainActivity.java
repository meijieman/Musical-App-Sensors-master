package willburles.brookes.ac.musicalsensors;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private final int SAMPLE_RATE = 8000;
    EditText audioInput;
    TextView sensorOut;
    SeekBar sensorRange;
    Button sensorButton;
    Button playButton;
    ScoreSheet scoresheet;
    AudioTrack audioTrack;
    ArrayList<Song> songList;
    private Handler handler = new Handler();
    private String regExp = "^(([1-4|6|8][A-G][#?|b?]?[1-8])+[ ]{0,1})+$";
    private Toast invalidInput;
    private SensorManager mgr;
    private Sensor orientation;
    private float x;
    private float y;
    private float z;
    private float range;

    private String anote = "4A4";
    private String bday = "2D4 2D4 4E4 4D4 4G4 8F#4";
    private String sweep = "1G#5 2Fb5 3E#5 4Db5 4C#5 6Bb4 8A#4 1Gb4 2F#4 3Eb4 4D#4 6Cb4";
    private String offSweep = "1G#3 2Fb3 3E#6 4Db6 4C#5 6Bb4 8A#4 1Gb4 2F#4 3Eb4 4D#4 6Cb4";
    private String semisweep = "8G5 8F5 8E5 8D5 8C5 6B4 8A4 1G4 8F4 8E4 8D4 6C4";
    private String twinkle = "2Eb4 2Eb4 2Bb4 2Bb4 2C5 2C5 4Bb4";

    private SensorEventListener mSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.values[0] != x | sensorEvent.values[1] != y | sensorEvent.values[2] != z) {
                x = sensorEvent.values[0];
                y = sensorEvent.values[1];
                z = sensorEvent.values[2];
                drawValues();
                sensorTrigger();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        audioInput = findViewById(R.id.text_area);
        playButton = findViewById(R.id.button_play);
        sensorOut = findViewById(R.id.sensors_text);
        sensorRange = findViewById(R.id.sensor_range);
        sensorButton = findViewById(R.id.sensor_button);
        scoresheet = findViewById(R.id.scoresheet_area);
        invalidInput = Toast.makeText(getBaseContext(), getString(R.string.invalid_input_toast), Toast.LENGTH_LONG);
        songList = new ArrayList<>();
        range = (float) 0.625;
        sensorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String input = bday;//audioInput.getText().toString();
                if (input.matches(regExp)) {
                    Song newSong = new Song(input, x, y, z, range);
                    songList.add(newSong);
                    Toast.makeText(getBaseContext(), getString(R.string.song_assigned_toast), Toast.LENGTH_SHORT).show();
                } else {
                    scoresheet.setTrack(" ");
                    scoresheet.invalidate();
                    invalidInput.show();
                }


            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String input = audioInput.getText().toString();
                if (input.matches(regExp)) {
                    playTrack(input);
                } else {
                    scoresheet.setTrack(" ");
                    scoresheet.invalidate();
                    invalidInput.show();
                }
            }
        });
        sensorRange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                range = i;
                range = range / 80;
                drawValues();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        orientation = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private ArrayList<Note> songParse(String input) {
        String[] notesStr = input.split(" ");
        ArrayList<Note> notes = new ArrayList<>();
        for (String noteIn : notesStr) {
            int length = Integer.parseInt(noteIn.substring(0, 1));
            String note;
            int octave;
            if (noteIn.length() == 4) {
                note = noteIn.substring(1, 3);
                octave = Integer.parseInt(noteIn.substring(3));
            } else {
                note = noteIn.substring(1, 2);
                octave = Integer.parseInt(noteIn.substring(2));
            }
            Log.d("audio", "The parser is parsing: " + noteIn + " " + note + " " + length);
            notes.add(new Note(length, note, octave));
        }
        return notes;
    }

    private void fillAndPlayBuffer(ArrayList<Note> notes) {
        ArrayList<byte[]> track = new ArrayList<>();
        int bufferSize = SAMPLE_RATE / 4;
        for (Note note : notes) {
            int length = note.getLength();
            double freq = note.getFreq();
            byte[] buffer = new byte[bufferSize * length];
            for (int i = 0; i < buffer.length / 2; i++) {
                double val = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / freq));
                short normVal = (short) ((val * 32767));
                buffer[2 * i] = (byte) (normVal & 0x00ff);
                buffer[2 * i + 1] = (byte) ((normVal & 0xff00) >>> 8);
            }
            track.add(buffer);
            Log.d("audio", "The freq is : " + freq);
        }
        int bufferLength = 0;
        for (byte[] note : track) {
            bufferLength = bufferLength + note.length;
        }
        byte[] audioBuffer = new byte[bufferLength];
        int i = 0;
        for (byte[] note : track) {
            for (byte pulse : note) {
                audioBuffer[i] = pulse;
                i++;
            }
        }
        if (audioTrack != null) {
            audioTrack.pause();
            audioTrack.flush();
        }
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, audioBuffer.length, AudioTrack.MODE_STATIC);
        audioTrack.write(audioBuffer, 0, audioBuffer.length);
        audioTrack.play();
    }

    protected void playTrack(String song) {
        scoresheet.setTrack(song);
        scoresheet.invalidate();
        final ArrayList<Note> notes = songParse(song);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        fillAndPlayBuffer(notes);
                    }
                });
            }
        });
        thread.start();
    }

    public void sensorTrigger() {
        for (Song song : songList) {
            Log.d("audio", "The sensor is sensing: " + song.getTrack());
            float songRange = song.getRange() / 2;
            float lowerX = x - songRange;
            float upperX = x + songRange;
            float lowerY = y - songRange;
            float upperY = y + songRange;
            float lowerZ = z - songRange;
            float upperZ = z + songRange;
            float songX = song.getPos()[0];
            float songY = song.getPos()[1];
            float songZ = song.getPos()[2];
            if (songX > lowerX && songX < upperX &&
                    songY > lowerY && songY < upperY &&
                    songZ > lowerZ && songZ < upperZ) {
                playTrack(song.getTrack());
            }
        }
    }

    public void drawValues() {
        String xStr = String.format("%.3f", x);
        String yStr = String.format("%.3f", y);
        String zStr = String.format("%.2f", z);
        String rangeStr = String.format("%.3f", range);
        String info = getString(R.string.info_x) + "   " + xStr +
                getString(R.string.info_y) + "   " + yStr +
                getString(R.string.info_z) + "   " + zStr +
                getString(R.string.info_range) + "   " + rangeStr;
        sensorOut.setText(info);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mgr.unregisterListener(mSensorEventListener, orientation);
        JSONArray array = new JSONArray();
        try {
            for (Song song : songList) {
                JSONObject songObj = new JSONObject();
                songObj.put("track", song.getTrack());
                songObj.put("x", song.getPos()[0]);
                songObj.put("y", song.getPos()[1]);
                songObj.put("z", song.getPos()[2]);
                songObj.put("range", song.getRange());
                array.put(songObj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences songs = getSharedPreferences("SavedSongs", 0);
        SharedPreferences.Editor editor = songs.edit();
        editor.putString("songs", array.toString()).apply();
        Log.d("audio", "JSON Object saved: " + songs.getString("songs", " "));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mgr.registerListener(mSensorEventListener, orientation, SensorManager.SENSOR_DELAY_NORMAL);
        SharedPreferences songs = getSharedPreferences("SavedSongs", 0);
        Log.d("audio", "JSON Object retrieved: " + songs.getString("songs", " "));
        try {
            JSONArray array = new JSONArray(songs.getString("songs", " "));
            Log.d("audio", "JSON Object retrieved: " + songs.getString("songs", " "));
            songList = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject songObj = array.getJSONObject(i);
                songList.add(new Song(
                        songObj.getString("track"),
                        (float) songObj.getDouble("x"),
                        (float) songObj.getDouble("y"),
                        (float) songObj.getDouble("z"),
                        (float) songObj.getDouble("range")
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        sensorTrigger();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_clear) {
            songList = new ArrayList<>();
        }
        return super.onOptionsItemSelected(item);
    }
}
