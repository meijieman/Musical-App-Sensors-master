package willburles.brookes.ac.musicalsensors;

public class Song {

    private String track;
    private float x;
    private float y;
    private float z;
    private float range;

    public Song(String track, float x, float y, float z, float range) {
        this.track = track;
        this.x = x;
        this.y = y;
        this.z = z;
        this.range = range;
    }

    public String getTrack() {
        return track;
    }

    public float[] getPos() {
        float[] pos = {x,y,z};
        return pos;
    }

    public float getRange() {
        return range;
    }
}
