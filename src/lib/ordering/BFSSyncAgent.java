package lib.ordering;

import lib.core.Message;
import lib.core.SyncAgent;
import lib.core.SyncMailer;

import java.util.*;

public abstract class BFSSyncAgent extends SyncAgent {

    private static final int MSG_LAYER = 0XFFFFD0;
    private static final int MSG_ACK = 0XFFFFD1;

    protected int parent;
    protected List<Integer> children;
    protected List<Integer> siblings;
    protected List<Integer> pseudoChildren;
    protected List<Integer> pseudoParent;
    protected int level;

    private Set<Integer> messageReceived;

    public BFSSyncAgent(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
        children = new LinkedList<>();
        siblings = new LinkedList<>();
        pseudoChildren = new LinkedList<>();
        pseudoParent = new LinkedList<>();
        messageReceived = new HashSet<>();
        level = Integer.MAX_VALUE;
    }

    @Override
    protected void initRun() {
        if (id == 1){
            level = 0;
            for (int neighbourId : neighbours){
                children.add(neighbourId);
            }
            for (int child : children){
                sendMessage(new Message(id,child,MSG_LAYER,level));
            }
        }
    }

    @Override
    public void disposeMessage(Message message) {
//        System.out.println(message);
        switch (message.getType()){
            case MSG_LAYER:
                messageReceived.add(message.getIdSender());
                //System.out.println(message);
                int receivedLevel = (int) message.getValue();
                if (receivedLevel + 1 < level){
                    level = receivedLevel + 1;
                    parent = message.getIdSender();
                    for (int neighbourId : neighbours){
                        if (neighbourId != parent){
                            children.add(neighbourId);
                        }
                    }
                    for (int child : children){
                        sendMessage(new Message(id,child,MSG_LAYER,level));
                    }
                    sendMessage(new Message(id,parent,MSG_ACK,true));
                }
                else {
                    if (receivedLevel == level) {
                        siblings.add(message.getIdSender());
                        children.remove((Object) message.getIdSender());
                    }
                    else if (receivedLevel < level){
                        pseudoParent.add(message.getIdSender());
                        sendMessage(new Message(id,message.getIdSender(),MSG_ACK,false));
                        children.remove((Object) message.getIdSender());
                    }

                }
                if (children.size() == 0 && messageReceived.size() == neighbours.length){
//                    assignPseudoChildren();
//                    System.out.println("id :" + id + " have received Layer from " + message.getIdSender());
                    System.out.println("id :" + id + " have finished tree creating!");
                    pseudoTreeCreated();
                }

                break;
            case MSG_ACK:
                messageReceived.add(message.getIdSender());
                boolean isParent = (boolean) message.getValue();
                if (!isParent){
                    if (!children.contains(message.getIdSender())){
                        throw new IllegalStateException("impossible");
                    }
                    children.remove((Object) message.getIdSender());
                }
                if (messageReceived.size() == neighbours.length){
                    assignPseudoChildren();
//                    System.out.println("id :" + id + " have received ACK from " + message.getIdSender());
                    System.out.println("id :" + id + " have finished tree creating!");
                    pseudoTreeCreated();
                }

        }
    }


    protected abstract void pseudoTreeCreated();

    private void assignPseudoChildren(){
        for (int neighbourId : neighbours){
            if (neighbourId != parent && !children.contains(neighbourId) && !pseudoParent.contains(neighbourId) && !siblings.contains(neighbourId)){
                pseudoChildren.add(neighbourId);
            }
        }
    }

    public String toDOTString(){
        StringBuilder stringBuilder = new StringBuilder();
        if (parent > 0){
            stringBuilder.append("X" + parent + "->X" + id + ";\n");
        }
        for (int pp : pseudoParent){
            stringBuilder.append("X" + pp + "->X" + id + " [style=dotted];\n");
        }
        for (int sib : siblings) {
            stringBuilder.append("X" + sib + "->X" + id + " [dir=none style=dotted color=red]; \n");
        }

        return stringBuilder.toString();
    }

}
