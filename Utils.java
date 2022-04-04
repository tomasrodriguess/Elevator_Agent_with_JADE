package Project;

import java.util.Random;

public class Utils {
    public int generateInitialFloor(int top, int bottom){
        Random r=new Random();
        int floor=r.nextInt(top*2+1)+bottom;
        if (floor> top) return 0;
        return floor;
    }

    public int generateDestinFloor(int initial,int top,int bottom){
        int destin=initial;
        while (destin==initial){
            Random r = new Random();
            destin = r.nextInt(top+1)+bottom;
        }
        return destin;
    }
}
