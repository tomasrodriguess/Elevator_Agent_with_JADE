package Project;

import jade.core.AID;
import jade.util.leap.Serializable;

public class AgentInformation implements Serializable {
    private AID name;
    private int floor;
    private boolean moviment;

    public AgentInformation(AID name, int floor, boolean moviment) {
        this.name = name;
        this.floor = floor;
        this.moviment = moviment;
    }
    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getFloor() {
        return floor;
    }

    public AID getName() {
        return name;
    }

    public boolean getMoviment() {
        return moviment;
    }

    public void setMoviment(boolean moviment) {
        this.moviment = moviment;
    }
}
