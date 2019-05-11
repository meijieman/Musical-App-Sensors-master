package com.major.piano;

/**
 * Created by Will on 02/12/2016.
 * Note 指单个音符
 */

public class Note {

    private int length; // 音符 八分音符，六分音符，四分音符，三分音符，二分音符，全音符
    private int octave; //
    private String note; // 音符 CDEFGAB
    private double freq; // 单位HZ，该音对应的频率，国际标准音 A-la-440HZ

    public Note(int length, String note, int octave) {
        this.length = length;
        this.note = note;
        this.octave = octave;
        if (note.equals("C") | note.equals("B#")) {
            freq = 261.63;
        } else if (note.equals("C#") | note.equals("Db")) {
            freq = 277.18;
        } else if (note.equals("D")) {
            freq = 293.66;
        } else if (note.equals("D#") | note.equals("Eb")) {
            freq = 311.13;
        } else if (note.equals("E") | note.equals("Fb")) {
            freq = 329.63;
        } else if (note.equals("F") | note.equals("E#")) {
            freq = 349.23;
        } else if (note.equals("F#") | note.equals("Gb")) {
            freq = 369.99;
        } else if (note.equals("G")) {
            freq = 392.00;
        } else if (note.equals("G#") | note.equals("Ab")) {
            freq = 415.30;
        } else if (note.equals("A")) {
            freq = 440.00;
        } else if (note.equals("A#") | note.equals("Bb")) {
            freq = 466.16;
        } else if (note.equals("B") | note.equals("Cb")) {
            freq = 493.88;
        } else freq = 0;
        if (octave > 4) {
            octave = octave - 4;
            double mult = Math.pow(2, octave);
            freq = freq * mult;
        } else if (octave < 4) {
            octave = 4 - octave;
            double divi = Math.pow(2, octave);
            freq = freq / divi;
        }

    }

    public int getLength() {
        return length;
    }

    public String getNote() {
        return note;
    }

    public int getOctave() {
        return octave;
    }

    public double getFreq() {
        return freq;
    }
}
