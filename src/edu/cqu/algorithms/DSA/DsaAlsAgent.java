package edu.cqu.algorithms.DSA;


import edu.cqu.core.Message;
import edu.cqu.core.SyncMailer;
import edu.cqu.framework.ALSAgent;
import edu.cqu.result.ResultAls;

import java.util.HashMap;
import java.util.Map;

public class DsaAlsAgent extends ALSAgent {

    public final static int TYPE_VALUE_MESSAGE = 4560;
    public final static int CYCLE_COUNT_END = 1000;
    public final static double P = 0.4;


    private Map<Integer,Integer> localView;
    private int localCost;

    public DsaAlsAgent(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer,CYCLE_COUNT_END);
        localView = new HashMap<>();

    }

    @Override
    protected void alsReady() {
        int value = (int)(Math.random()*domain.length);
        assignValueIndex (value);
        assignAlsValue(value);
        sendValueMessage(value);
    }


    private void sendValueMessage(int value) {
        for (int neighborId : neighbours) {
            Message msg = new Message(this.id,neighborId,TYPE_VALUE_MESSAGE,value);
            sendMessage(msg);
        }
    }

    @Override
    protected void decision() {
        localCost = calLocalCost(valueIndex);
        dsaWork();
    }

    private void dsaWork() {
        int minCost = Integer.MAX_VALUE;
        int minCostIndex = -1;
        int count = 0;
        for (int i = 0; i < domain.length; i++) {
            if (calLocalCost(i) < minCost){
                minCost = calLocalCost(i);
                minCostIndex = i;
            }
        }
        if (minCost < localCost && Math.random() < P){
            assignValueIndex(minCostIndex);
            sendValueMessage(minCostIndex);
        }
    }

    private int calLocalCost(int value) {
        int cost = 0;
        for (int neighborId : neighbours) {
            int ov = localView.containsKey(neighborId) ? localView.get(neighborId) : 0;
            cost += constraintCosts.get(neighborId)[value][ov];
        }
        return cost;
    }



    @Override
    public void disposeMessage(Message msg) {
        super.disposeMessage(msg);
        switch (msg.getType()){
            case TYPE_VALUE_MESSAGE:
                disposeValueMessage(msg);
                break;
        }
    }

    private void disposeValueMessage(Message msg) {
        localView.put(msg.getIdSender(),(int)(msg.getValue()));
        updateLocalView(msg.getIdSender(), (int)msg.getValue());
    }

    @Override
    public void runFinished() {
        ResultAls resultCycle = new ResultAls();
        resultCycle.setAgentValues(id,valueIndex);
        mailer.setResultCycle(id,resultCycle);
    }


}
