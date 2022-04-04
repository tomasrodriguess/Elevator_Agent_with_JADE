package Project;

import jade.Boot;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

public class Simulator extends Agent {
    //create object utils, used to generate floors
    Utils utils = new Utils();

    //Simulation parameters
    private int nFloors = 7; //number of floors
    private int bottom = -1; //bottom flor
    private int top = bottom + nFloors - 1;//don´t change this
    private int timeofIteration = 1000; // time between each task sended
    private int taskId = 0; //used in loop and for add ids to each task
    private static int nElevators = 2;//Number of elevators for simulation
    private int nummberOfGenerations = 6; //Number of taks in simulation
    private static int[] capacityOfElevator=new int[]{2,2};//each positon of the array represent the maximal capacity of the elevators to be created
                                                            // the length of the array need to be equal to nElevators


    //
    private boolean sendInfo = false;//var to stop sending info about the building
    private int nextElevatorID = 0;//var used in loop

    protected void setup() {
        //Performatives
        //Information of building
        int InfoBuilding_ordinal = 32;

        System.out.println("Hello, I´m " + getLocalName());
        Vector agents = new Vector();
        ParallelBehaviour pb = new ParallelBehaviour();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Simulator");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        //search for agents of type Elevator
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                // Update the list of agents
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("Elevator");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result =
                            DFService.search(myAgent, template);
                    agents.clear();
                    for (int i = 0; i < result.length; ++i) {
                        agents.addElement(result[i].getName());
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });


        //Behaviour to send the info of the building for agents
        //this behaviour only send the info when all the agents are registered
        //once the info had been sent, this behaviour stop
        addBehaviour(new Behaviour(this) {
            @Override
            public void action() {
                if (agents.size() == nElevators) {
                    if (capacityOfElevator.length != 0) {
                        int i=0;
                        for (Object a : agents) {
                            ACLMessage msg = new ACLMessage(InfoBuilding_ordinal);
                            //sending info in format -> nfloors,bottom,top
                            String content = nFloors + "," + bottom + "," + top + "," + capacityOfElevator[i];
                            msg.setContent(content);
                            msg.addReceiver((AID) a);
                            send(msg);
                            i++;
                        }
                        sendInfo = true;
                    }else{
                        ACLMessage msg = new ACLMessage(InfoBuilding_ordinal);
                        //sending info in format -> nfloors,bottom,top
                        String content = nFloors + "," + bottom + "," + top;
                        msg.setContent(content);
                        for (Object a : agents) {
                            msg.addReceiver((AID) a);
                        }
                        send(msg);
                        sendInfo = true;
                    }

                }
            }

            @Override
            public boolean done() {
                return sendInfo;
            }

        });


        //Behaviour responsable to generate tasks and send them to the agents
        addBehaviour(new TickerBehaviour(this, timeofIteration) {
            @Override
            public void onTick() {
                int initial = utils.generateInitialFloor(top, bottom);
                Task t = new Task(taskId + 1, initial, utils.generateDestinFloor(initial, top, bottom));
                System.out.println("Sending task-> " + t + " to " + ((AID) agents.get(nextElevatorID)));
                //create message with performative of REQUEST
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                try {
                    msg.setContentObject(t);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //add receiver the agent in agents vector in position nextElevatorID
                msg.addReceiver((AID) agents.get(nextElevatorID));
                send(msg);
                //when all the taks had been created and sended, then the behaviour will send a message with performative FINISH and die
                if (taskId + 1 == nummberOfGenerations) {
                    ACLMessage finish = new ACLMessage(ACLMessage.CANCEL);
                    for (Object a : agents) {
                        finish.addReceiver((AID) a);
                    }
                    send(finish);
                    doDelete();
                    System.out.println("Simulation ended");
                }
                //If the next id for the elevator ir bigger than agents size, then switch the id to 0 to make a loop
                if (nextElevatorID + 1 == agents.size()) {
                    nextElevatorID = 0;
                } else {
                    nextElevatorID++;
                }
                taskId++;
            }

        });

    }



    public static void main(String[] args) {
        if(nElevators==capacityOfElevator.length || capacityOfElevator.length==0){
            String[] services = { "-agents", "Simulator:Project.Simulator;" };
            for (int i = 0; i < nElevators; i++) {
                services[1] = services[1].concat("Elevator" + (i + 1) + ":Project.Elevator;");
            }
            Boot.main(services);
        }else{
            System.out.println("Error on creation of elevators capacity");
        }
    }
}
