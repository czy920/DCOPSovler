package edu.cqu.algorithms.BnBADOPT;

import lib.core.Message;
import lib.core.SyncMailer;

import java.util.*;

public abstract class RetentionFrame_FDAC extends FDAC {

    private static final int MSG_BRANCH_PSEUDO_PARENTS = 0XFFFF9;
    protected static final int INFINITE = Integer.MAX_VALUE;

    protected int[] highCost;
    protected Map<Integer,OptElement[]> opt;
    protected Map<Integer,Integer> cpa;
    protected Map<Integer,Integer> previousCpa;
    protected Map<Integer,Set<Integer>> branchPseudoParents;
    protected Map<Integer, LinkedList<Integer>> exploreValue;
    protected  Set<Integer> SCP;
    protected long messageSizeCount;
    public RetentionFrame_FDAC(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
        opt = new HashMap<>();
        cpa = new HashMap<>();
        previousCpa = new HashMap<>();
        branchPseudoParents = new HashMap<>();
        exploreValue = new HashMap<>();
        highCost = new int[domain.length];
  //      pseudoChildren = new HashSet<>();
        SCP= new HashSet<>();
    }

    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        switch (message.getType()) {
            case MSG_BRANCH_PSEUDO_PARENTS: {
                Set<Integer> receivedPesudoParents = (Set) message.getValue();
                messageSizeCount+=receivedPesudoParents.size();
                if (receivedPesudoParents.contains(id)) {
                    receivedPesudoParents.remove(id);
                }
                branchPseudoParents.put(message.getIdSender(), receivedPesudoParents);
                if (branchPseudoParents.size() == children.size()) {
                    if (!isRootAgent()) {
                        SCP= new HashSet<>(pseudoParents);
                        for (Set<Integer> subPP : branchPseudoParents.values()) {
                            SCP.addAll(subPP);
                        }
                        SCP.add(parent);
                        sendMessage(new Message(id, parent, MSG_BRANCH_PSEUDO_PARENTS, SCP));
                    }
                    start();
                }
                break;
            }
        }
    }

    protected abstract void start();


    @Override
    protected void AlgorithmStart() {
//        for (int neighbourId : neighbours)
//            if (!children.contains(neighbourId) && parent != neighbourId && !pseudoParents.contains(neighbourId))
//                pseudoChildren.add(neighbourId);
        for (int child : children){
            OptElement[] optRow = new OptElement[domain.length];
            for (int i = 0; i < domain.length; ++i){
                optRow[i] = new OptElement();
            }
            opt.put(child,optRow);
        }
        if (isLeafAgent()){
            sendMessage(new Message(id,parent,MSG_BRANCH_PSEUDO_PARENTS,new HashSet<>(pseudoParents)));
            start();
        }
    }

    protected class OptElement{
        int costStar;
        int sendUb;
        public OptElement(int costStar, int sendUb) {
            this.costStar = costStar;
            this.sendUb = sendUb;
        }
        public OptElement() {
            this.costStar = INFINITE;
            this.sendUb = -1;
        }

        @Override
        public String toString() {
            return "cost:" + costStar + " ub:" + sendUb;
        }
    }
}
