package Project;

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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Vector;

public class Elevator extends Agent {
    //Thread Behaviour to allow threading
    private ThreadedBehaviourFactory tbf = new ThreadedBehaviourFactory();
    private boolean moviment = true;// true for going up and false for going down
    private int currentFloor = 0;

    //information about building
    private int nFloors = 0;
    private int bottom = 0;
    private int top = 0;

    //capacity of elevator
    private int capacity = 1;

    //variables for time in ms
    private int timeOfTravel = 3000;//time between floors
    private int timeOfActivity = 2000;//time of the tasks to leave or enter the elevator

    //

    //Lists
    ArrayList<Task> tasks = new ArrayList<>();//List of taks waiting to be picked
    ArrayList<Task> currentTasks = new ArrayList<>();//List of tasks inside the elevator
    ArrayList<Task> tasksDone = new ArrayList<>();//List of tasks sucefully done
    ArrayList<AgentInformation> agentsFloors = new ArrayList<>();//List of others agents and his informations, name, currentfloor and moviment
    ArrayList<Integer> nextsFloors = new ArrayList<>();//List of target floors

    //
    boolean simulationEnded = false;//var to check if don´t have more tasks could die

    //var to check if all the elevators finish the tasks
    boolean finished=false;
    boolean informatinoFinishsended=false;
    int numberOfAgentsFinished = 0;
    //the var simulationEnded gonna bue used to check if the simulation hava already ended, if its true mean that elevators could stop


    //String to be shown in output
    private String removed = "";
    private String enter = "";
    private String done = "";
    private String waiting = "";
    private String still = "";

    protected void setup() {
        //Performatives created
        //Information of building
        int InfoBuilding_ordinal = 32;
        MessageTemplate InfoBuilding = MessageTemplate.MatchPerformative(InfoBuilding_ordinal);


        System.out.println("My name is " + getLocalName());
        Vector agents = new Vector();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Elevator");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        //behaviour to add other Elevator agents do vector of agents
        TickerBehaviour search = new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                // Update the list of nest agents
                DFAgentDescription nest = new DFAgentDescription();
                ServiceDescription nestSD = new ServiceDescription();
                nestSD.setType("Elevator");
                nest.addServices(nestSD);
                try {
                    DFAgentDescription[] result =
                            DFService.search(myAgent, nest);
                    agents.clear();
                    for (int i = 0; i < result.length; ++i) {
                        //check is needed to don´t allow to add to vector of agents himself
                        if (!result[i].getName().getLocalName().equals(getLocalName())) {
                            agents.addElement(result[i].getName());
                        }
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        };


        //behaviour to receive information about the buildind and store in correct variables
        Behaviour receiveInfoBuilding = new Behaviour() {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(InfoBuilding);
                if (msg != null) {
                    String content = msg.getContent();
                    String[] info = content.split(",");
                    if(info.length==3) {
                        nFloors = Integer.parseInt(info[0]);
                        bottom = Integer.parseInt(info[1]);
                        top = Integer.parseInt(info[2]);
                        System.out.println(getLocalName() + " have received the information about the floor. NFloors:" + nFloors + ", BottomFloor: " + bottom + ", TopFloor: " + top);
                    }else{
                        nFloors = Integer.parseInt(info[0]);
                        bottom = Integer.parseInt(info[1]);
                        top = Integer.parseInt(info[2]);
                        capacity = Integer.parseInt(info[3]);
                        System.out.println(getLocalName() + " have received the information about the floor. NFloors:" + nFloors + ", BottomFloor: " + bottom + ", TopFloor: " + top + ", Capacity: "+capacity);
                    }
                }
            }

            @Override
            public boolean done() {
                return nFloors!=0;
            }
        };


        //Behaviour to receive tasks and add them to array of tasks
        Behaviour receiveRequest = new Behaviour() {
            @Override
            public void action() {
                ACLMessage msgReceived = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msgReceived != null) {
                    Task task = new Task(0, 0, 0);
                    try {
                        task = (Task) msgReceived.getContentObject();
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                    System.out.println(getLocalName() + " have received task -> " + task);
                    tasks.add(task);
                }
            }

            @Override
            public boolean done() {
                return false;
            }
        };


        //Behaviour to kill agent if the simulator have ended and don´t have more tasks to do
        //change this to only die after all the agents have ended tasks
        Behaviour endSimulation = new Behaviour() {
            @Override
            public void action() {
                ACLMessage msgReceived = receive(MessageTemplate.MatchPerformative(ACLMessage.CANCEL));
                if (msgReceived != null) {
                    simulationEnded = true;
                }
            }

            @Override
            public boolean done() {
                return false;
            }
        };


        //Behaviour to be always checking others elevators positions
        Behaviour receiveInfoFloors = new Behaviour() {
            @Override
            public void action() {
                //create a template to only receive messages with performative INFORM_REF
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM_REF));
                if (msg != null) {
                    boolean add = false;//var to check if is needed to add the information or the info had been replaced
                    AgentInformation aInfo = new AgentInformation(getAID(), 0, true);//instance of AgenteInformation to be changed
                    try {
                        //change aInfo to the content received
                        aInfo = (AgentInformation) msg.getContentObject();
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                    //iterate in the list of information
                    //if the sender is in the list then replace information
                    for (AgentInformation a : agentsFloors) {
                        if (a.getName().equals(msg.getSender())) {
                            a.setFloor(aInfo.getFloor());
                            a.setMoviment(aInfo.getMoviment());
                            add = true;
                        }
                    }
                    //if not replace information, then the sender isn´t in the list, so add them
                    if (!add) agentsFloors.add(aInfo);
                }
            }

            @Override
            public boolean done() {
                return false;
            }
        };


        //Behaviour to receive tasks from another elevator
        Behaviour receveiTaskFromAnother = new Behaviour() {
            @Override
            public void action() {
                //Receive mensage with performative Propose
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                Task task = null;
                if (msg != null) {
                    ACLMessage msgToSend = new ACLMessage();
                    msgToSend.addReceiver(msg.getSender());
                    System.out.println(getLocalName() + " -> Received task proposal from " + msg.getSender().getLocalName() + ".");
                    System.out.println(getLocalName() + " -> tasks size " + tasks.size());
                    //Change this to watch switchs between elevators
                    //if is set to be always true, then the agent will always accpet the proposal
                    if (currentTasks.size() + 1 < capacity) {
                    //if (true) {
                        try {
                            //receive task from agent
                            task = (Task) msg.getContentObject();
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        }
                        //add task to array of tasks
                        tasks.add(task);
                        //set performative
                        msgToSend.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        System.out.println(getLocalName() + " -> Accepted task from " + msg.getSender().getLocalName() + ".");
                        System.out.println(getLocalName() + " -> Task: " + task);
                        System.out.println(getLocalName() + " -> tasks size " + tasks.size());
                    } else {
                        //set performative
                        msgToSend.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        System.out.println(getLocalName() + " -> Rejected task from " + msg.getSender().getLocalName() + ".");
                    }
                    send(msgToSend);
                }
            }

            @Override
            public boolean done() {
                return false;
            }
        };

        //Behaviour to check if all agents had stopped
        //Agent only could die if all the agents don´t have more tasks to do
        Behaviour endElevator = new Behaviour() {
            @Override
            public void action() {
                //only end if the simulation had ended, the numbers of agents finished is bigger than the size of the vector,
                //don´t have more tasks and already sended the information about himself
                if (simulationEnded && numberOfAgentsFinished >= agents.size() && tasks.size() == 0 && currentTasks.size() == 0 && informatinoFinishsended) {
                    System.out.println(getLocalName() + " -> All the agents have finsished their tasks");
                    System.out.println(getLocalName() + " -> DIE");
                    doDelete();
                }
            }

            @Override
            public boolean done() {
                return false;
            }
        };


        //Behaviour to receive messages about the ending of another agents
        Behaviour receiveFinishInformation= new Behaviour() {
            @Override
            public void action() {
                ACLMessage msg= receive(MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
                if(msg!=null){
                    numberOfAgentsFinished++;
                    System.out.println(getLocalName()+" -> receive info that "+msg.getSender().getLocalName()+" had finished.");
                }

            }

            @Override
            public boolean done() {
                return false;
            }
        };


        //Behaviour responsible for move the elevator
        Behaviour doTasks = new Behaviour() {
            @Override
            public void action() {
                try {
                    //call function move
                    move();
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public boolean done() {
                return false;
            }


            //function used to sleep in travel
            private void sleepToMove(int floor) {
                System.out.println(getLocalName() + " -> Going to " + floor);
                try {
                    Thread.sleep(timeOfTravel);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


            //function used to sleep when someone have entered or leaved the elevator
            private void sleepToExits(int floor) {
                System.out.println(getLocalName() + " -> Let people enter and exit on " + floor);
                try {
                    Thread.sleep(timeOfActivity);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


            //function used to show the information on screen
            private void printInformation() {
                waiting = getLocalName() + " -> Waiting for elevator ->";
                for (Task t : tasks) {
                    waiting += ("; Task:" + t);
                }

                still = getLocalName() + " -> Still in elevator ->";
                for (Task t : currentTasks) {
                    still += ("; Task:" + t);
                }

                done = getLocalName() + " -> Finished tasks ->";
                for (Task t : tasksDone) {
                    done += ("; Task:" + t);
                }

                System.out.println(removed);
                System.out.println(enter);
                System.out.println(still);
                System.out.println(waiting);
                System.out.println(done);
            }


            //each time the elevator chande the floor this function is called
            //send info to the others agents presents in DF
            private void informAboutFloor() throws IOException {
                System.out.println(getLocalName() + " -> Send info about the floor to agents");
                //Set performative as INFORM_REF
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM_REF);
                //Set content the object AgentInformation
                Object content = new AgentInformation(getAID(), currentFloor, moviment);
                msg.setContentObject((Serializable) content);
                //add all agents as receivers
                for (int i = 0; i < agents.size(); i++) {
                    msg.addReceiver((AID) agents.get(i));
                }
                send(msg);
            }

            //Function called when the elevator reached maximal capacity
            //will check what is the nearest elevator the task and try to change the task to him
            private void tryToChangeTask(Task task) throws IOException {
                if (agentsFloors.isEmpty()) {
                    System.out.println(getLocalName() + " -> Don´t exist more elevators to transfer taks");
                } else {
                    int originFloor = task.getOrigin();
                    //check what is the elevator nearest
                    AgentInformation bestAgent = agentsFloors.get(0);
                    for (AgentInformation agent : agentsFloors) {
                        if (originFloor - agent.getFloor() < originFloor - bestAgent.getFloor() && agent.getMoviment() == true) {
                            bestAgent = agent;
                        } else if (agent.getFloor() - originFloor < bestAgent.getFloor() - originFloor && agent.getMoviment() == false) {
                            bestAgent = agent;
                        }
                    }

                    //create message
                    ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
                    msg.setContentObject(task);
                    msg.addReceiver(bestAgent.getName());
                    send(msg);

                    //var to check if have received the response
                    boolean received = false;
                    //while received is false will execute the loop
                    while (!received) {
                        //create two messages to wait for
                        ACLMessage msgReceviedAccepted = receive(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
                        ACLMessage msgReceviedRejected = receive(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));

                        //if message received with performative ACCEPT_PROPOSAL came from the right agent
                        if (msgReceviedAccepted != null && msgReceviedAccepted.getSender().equals(bestAgent.getName())) {
                            received = true;
                            tasks.remove(task);
                            System.out.println(getLocalName() + " -> Sended " + task + " to " + bestAgent.getName().getLocalName());

                            //if message received with performative REJECTED_PROPOSAL came from the right agent
                        } else if (msgReceviedRejected != null && msgReceviedRejected.getSender().equals(bestAgent.getName())) {
                            received = true;
                            System.out.println(getLocalName() + " -> Refused " + task + " from " + bestAgent.getName().getLocalName());
                        }
                    }
                }
            }

            //Sending to other agents if tha agent don´t more tasks
            private void sendFinishInformationToAgents() {
                ACLMessage msg=new ACLMessage(ACLMessage.REFUSE);
                for(int i=0;i<agents.size();i++){
                    msg.addReceiver((AID) agents.get(i));
                }
                send(msg);
                informatinoFinishsended=true;
                System.out.println(getLocalName()+" -> Sent to other agents information about not having more tasks to do");
            }

            private void move() throws InterruptedException, IOException {
                //Check if simulation ended already and don´t more tasks
                if (simulationEnded && tasks.size() == 0 && currentTasks.size() == 0 && finished && !informatinoFinishsended) {
                    sendFinishInformationToAgents();
                }
                if(informatinoFinishsended && currentTasks.size() == 0 && tasks.size() == 0){
                    return;
                }
                if (currentTasks.size() == 0 && tasks.size() == 0) {
                    //System.out.println(getLocalName() + " -> Don´t have any tasks.");
                    //do nothing
                    finished=true;
                    //check if don´t have tasks inside but have some tasks to do
                } else if (currentTasks.size() == 0 && tasks.size() != 0) {
                    finished=false;
                    System.out.println("-----------------------------------");
                    System.out.println("  " + getLocalName() + "  CURRENT FLOOR -> " + currentFloor + "    ");
                    System.out.println("-----------------------------------");
                    //Get the nearest task
                    Task next = tasks.get(0);
                    for (Task t : tasks) {
                        if (Math.abs(t.getOrigin() - currentFloor) < Math.abs(next.getOrigin() - currentFloor))
                            next = t;
                    }
                    //move to floor of the task
                    int nextfloor = next.getOrigin();
                    //add the floor to array of targets
                    nextsFloors.add(next.getDestin());
                    System.out.println(getLocalName() + " -> Have find a task in " + nextfloor + " floor.");
                    //sleep the time to move to the task
                    Thread.sleep(Math.abs((nextfloor - currentFloor) * timeOfTravel));
                    System.out.println(getLocalName() + " -> Moving to " + nextfloor + " floor.");
                    //Set currentfloor
                    currentFloor = nextfloor;
                    informAboutFloor();
                    //add task to current task to dont let size be 0, and change value of moviment
                    currentTasks.add(next);
                    tasks.remove(next);
                    System.out.println(getLocalName() + " -> Now have target " + nextsFloors.get(0) + " floor.");
                    if (next.getDestin() > currentFloor) moviment = true;
                    else moviment = false;
                    removed = getLocalName() + " -> no one exited";
                    enter = (getLocalName() + " -> Entered on elevator->; Task:" + next);
                    printInformation();
                } else {
                    finished=false;
                    System.out.println("-----------------------------------");
                    System.out.println("  " + getLocalName() + "  CURRENT FLOOR -> " + currentFloor + "    ");
                    System.out.println("-----------------------------------");
                    // id reached the target floor then remove floors os targets
                    if (nextsFloors.get(0) == currentFloor) {
                        nextsFloors.remove(0);
                    }

                    //Check witch tasks leave the elevator in current floor
                    //if they leave, then add tasks to toRemove array and remove them after that
                    ArrayList<Task> toRemove = new ArrayList<>();
                    //this var is used if someone have leaved or entered the elevator
                    boolean haveDoneSomething = false;
                    removed = getLocalName() + " -> Have exited the elevator in " + currentFloor + " floor -> ";
                    for (Task t : currentTasks) {
                        if (t.getDestin() == currentFloor) {
                            toRemove.add(t);
                            haveDoneSomething = true;
                            removed += ("; Task:" + t);
                        }
                    }
                    for (Task t : toRemove) {
                        tasksDone.add(t);
                        currentTasks.remove(t);
                    }

                    enter = (getLocalName() + " -> Entered on elevator ->");

                    if (!nextsFloors.isEmpty())
                        System.out.println(getLocalName() + " -> Target is " + nextsFloors.get(0) + ".");

                    //only do that if have a task inside
                    if (currentTasks.size() != 0) {
                        for (Task t : tasks) {
                            //check if the task waiting enter the elevator in this floor
                            if (t.getOrigin() == currentFloor) {
                                //if the elevator is full
                                if (currentTasks.size() == capacity) {
                                    System.out.println(getLocalName() + " -> Achieved the maximal capacity, and the task " + t + " will have to wait.");
                                    System.out.println(getLocalName() + " -> Starting comunication with others Elevators to switch task: " + t);
                                    tryToChangeTask(t);
                                    //break the loop because don´t have space for more
                                    break;
                                    //if you want to find another elevator for all the tasks waiting use the continue() instead the break()
                                    //continue;
                                }
                                haveDoneSomething = true;
                                //add task to currentTasks->task enter the elevator
                                currentTasks.add(t);
                                enter += ("; Task:" + t);
                                //if is going up
                                if (moviment == true) {
                                    //if the destination is bigger than the current distination then subsitute
                                    if (nextsFloors.get(0) < t.getDestin()) {
                                        System.out.println(getLocalName() + " -> Changed the Destination floor from " + nextsFloors.get(0) + " to " + t.getDestin() + ".");
                                        nextsFloors.add(0, t.getDestin());
                                    } else {
                                        //check if the elevator have another destin after arrived to the higer, if have then substitute, if dont have then add
                                        if (nextsFloors.size() != 2) {
                                            nextsFloors.add(t.getDestin());
                                            //check if when the elvator goes down the destination is less lower, if it is then subsitute
                                        } else if (t.getDestin() < nextsFloors.get(1)) {
                                            System.out.println(getLocalName() + " -> After going to " + nextsFloors.get(0) + " the elevator is going to the " + t.getDestin() + ".");
                                            nextsFloors.add(1, t.getDestin());
                                        }
                                    }
                                    //if is going down
                                } else {
                                    //if the destination is lower than the current destination then substitute
                                    if (nextsFloors.get(0) > t.getDestin()) {
                                        System.out.println(getLocalName() + " -> Changed the Destination floor from " + nextsFloors.get(0) + " to " + t.getDestin() + ".");
                                        nextsFloors.add(0, t.getDestin());
                                    } else {
                                        //check if the elevator have another destin after arrived to the lower, if have then substitute, if dont have then add
                                        if (nextsFloors.size() != 2) {
                                            nextsFloors.add(t.getDestin());
                                            //check if when the elevator goes up the destination is bigger, if it is then subsitute
                                        } else if (t.getDestin() > nextsFloors.get(1)) {
                                            System.out.println(getLocalName() + " -> After going to " + nextsFloors.get(0) + " the elevator is going to the " + t.getDestin() + ".");
                                            nextsFloors.add(1, t.getDestin());
                                        }
                                    }
                                }

                            }
                        }
                        //if the task enter the elevator then remove them from array of waiting taks
                        for (Task t : currentTasks) {
                            tasks.remove(t);
                        }
                    }
                    printInformation();

                    //sleep
                    if (haveDoneSomething) {
                        sleepToExits(currentFloor);
                    }

                    //change the floor of elevator
                    if (currentTasks.size() != 0) {
                        if (nextsFloors.get(0) - currentFloor > 0) {
                            moviment = true;
                            currentFloor++;
                        } else {
                            moviment = false;
                            currentFloor--;
                        }
                        //send info about the floor to the others elevators
                        informAboutFloor();

                        //sleep
                        sleepToMove(currentFloor);
                    }
                }
            }
        };


        //wrap behaviours to thread
        tbf.wrap(search);
        tbf.wrap(receiveRequest);
        tbf.wrap(receiveInfoBuilding);
        tbf.wrap(doTasks);
        tbf.wrap(endSimulation);
        tbf.wrap(receveiTaskFromAnother);
        tbf.wrap(receiveInfoFloors);
        tbf.wrap(receiveFinishInformation);
        tbf.wrap(endElevator);

        //add behaviours
        addBehaviour(search);
        addBehaviour(receiveRequest);
        addBehaviour(receiveInfoBuilding);
        addBehaviour(doTasks);
        addBehaviour(endSimulation);
        addBehaviour(receiveInfoFloors);
        addBehaviour(receveiTaskFromAnother);
        addBehaviour(receiveFinishInformation);
        addBehaviour(endElevator);

    }
}
