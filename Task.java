package Project;

import java.io.Serializable;

public class Task implements Serializable {
    private int id;
    private int origin;
    private int destin;


    public Task(int id, int origin, int destin) {
        this.id = id;
        this.origin = origin;
        this.destin = destin;
    }

    public int getId() {
        return id;
    }

    public int getOrigin() {
        return origin;
    }

    public int getDestin() {
        return destin;
    }

    @Override
    public String toString() {
        return id+","+origin+","+destin;
    }
}
