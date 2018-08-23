package edu.cqu.algorithms.adpop;

import edu.cqu.core.Message;
import edu.cqu.core.SyncMailer;
import edu.cqu.ordering.DFSSyncAgent;
import edu.cqu.result.ResultCycle;
import edu.cqu.result.annotations.NotRecordCostInCycle;


import java.util.*;

@NotRecordCostInCycle
public class ADPOP extends DFSSyncAgent {

    private static final int MSG_LEVEL = 0;
    private static final int MSG_UTIL = 1;
    private static final int MSG_VALUE = 2;

    private static final int K = 3;

    private TreeMap<Integer,Integer> levelView;
    private Map<Integer,Integer> highView;
    private Map<Integer,NDData> localUtil;
    private NDData maxUtil;
    private NDData minUtil;
//    private NDData maxJoin;
//    private NDData minJoin;
    private Set<Util> childrenUtil;
    public ADPOP(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
        childrenUtil = new HashSet<>();
    }

    @Override
    protected void pseudoTreeCreated() {
        if (isRootAgent()){
            highView = new HashMap<>();
            Map<Integer,Integer> map = new HashMap<>();
            map.put(level,id);
            for (int c : children){
                sendMessage(new Message(id,c,MSG_LEVEL,map));
            }
        }
        else {
            localUtil = new HashMap<>();
            localUtil.put(parent,new NDData(constraintCosts.get(parent),id,parent));
            for (int pp : pseudoParents){
                localUtil.put(pp,new NDData(constraintCosts.get(pp),id,pp));
            }
        }
    }

    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        switch (message.getType()){
            case MSG_LEVEL:
                levelView = new TreeMap<>((Map<Integer,Integer>) message.getValue());
                if (!isLeafAgent()) {
                    Map<Integer, Integer> map = new HashMap<>(levelView);
                    map.put(level, id);
                    for (int c : children){
                        sendMessage(new Message(id,c,MSG_LEVEL,map));
                    }
                }
                else {
                    sendUtilMessage();
                }
                break;
            case MSG_UTIL:
                Util receivedUtil = (Util) message.getValue();
                childrenUtil.add(receivedUtil);
                if (childrenUtil.size() == children.size()){
//                    maxJoin = maxUtil.copy();
//                    minJoin = minUtil.copy();
                    if (!isRootAgent()) {
                        sendUtilMessage();
                    }
                    else {
                        decision();
                    }
                }
                break;
            case MSG_VALUE:
                highView = new HashMap<>((Map) message.getValue());
                decision();
                break;
        }
    }

    private void sendUtilMessage(){
        Set<Integer> removedDim = new HashSet<>();
        Set<Integer> allDim = new HashSet<>(pseudoParents);
        allDim.add(parent);
        Set<Integer> childrenDim = new HashSet<>();
        for (Util util : childrenUtil){
            allDim.addAll(util.minJoin.orderedId);
            childrenDim.addAll(util.minJoin.orderedId);
        }
        allDim.remove(id);
        int dimCount = allDim.size() - K;
        for (int le : levelView.navigableKeySet()){
            int sep = levelView.get(le);
            if (allDim.contains(sep)){
                if (dimCount-- <= 0){
                    break;
                }
                removedDim.add(sep);
            }
        }

        Set<Integer> localDim = new HashSet<>();
        Set<Integer> joinDim = new HashSet<>();//from children
        Set<Integer> bothDim = new HashSet<>();
        for (int id : removedDim){
            if (localUtil.containsKey(id)){
                if (childrenDim.contains(id)){
                    bothDim.add(id);
                }
                else {
                    localDim.add(id);
                }
            }
            else {
                joinDim.add(id);
            }
        }
        Set<Util> mergedData = new HashSet<>();
        for (int dim : joinDim){
            for (Util data : childrenUtil){
                if (!mergedData.contains(data)) {
                    if (data.minJoin.containsDim(dim)) {
                        if (minUtil == null) {
                            minUtil = data.minJoin.copy();
                            maxUtil = data.maxJoin.copy();
                        } else {
                            minUtil.merge(data.minJoin);
                            maxUtil.merge(data.maxJoin);
                        }
                        mergedData.add(data);
                    }
                }
            }
            maxUtil = maxUtil.max(dim);
            minUtil = minUtil.min(dim);
        }

        for (int dim : localDim){
            NDData min = localUtil.get(dim).min(dim);
            NDData max = localUtil.get(dim).max(dim);
            if (minUtil == null){
                maxUtil = max;
                minUtil = min;
            }
            else {
                maxUtil.merge(max);
                minUtil.merge(min);
            }
        }

        for (int dim : bothDim){
            if (minUtil == null){
                minUtil = localUtil.get(dim).copy();
                maxUtil = localUtil.get(dim).copy();
            }
            else {
                minUtil.merge(localUtil.get(dim));
                maxUtil.merge(localUtil.get(dim));
            }
            for (Util data : childrenUtil){
                if (!mergedData.contains(data) && data.minJoin.containsDim(dim)){
                    minUtil.merge(data.minJoin);
                    maxUtil.merge(data.maxJoin);
                    mergedData.add(data);
                }
            }
            maxUtil = maxUtil.max(dim);
            minUtil = minUtil.min(dim);
        }
        for (Util data : childrenUtil){
            if (!mergedData.contains(data)){
                if (minUtil == null){
                    minUtil = data.minJoin.copy();
                    maxUtil = data.maxJoin.copy();
                }
                else {
                    minUtil.merge(data.minJoin);
                    maxUtil.merge(data.maxJoin);
                }
                mergedData.add(data);
            }
        }
        if (mergedData.size() != childrenUtil.size()){
            throw new IllegalStateException();
        }
        for (int dim : localUtil.keySet()){
            if (!removedDim.contains(dim)){
                if (minUtil == null){
                    maxUtil = localUtil.get(dim).copy();
                    minUtil = localUtil.get(dim).copy();
                }
                else {
                    maxUtil.merge(localUtil.get(dim));
                    minUtil.merge(localUtil.get(dim));
                }
            }
        }
        sendMessage(new Message(id,parent,MSG_UTIL,new Util(maxUtil.min(id), minUtil.min(id))));
}

    private void decision(){
        int minCost = Integer.MAX_VALUE;
        Map<Integer,Integer> assign = new HashMap<>(highView);
        for (int i = 0; i < domain.length; i++){
            assign.put(id,i);
            int cost = 0;
            for (Util data : childrenUtil){
                //cost += data.minJoin.getValue(assign);
                cost += data.maxJoin.getValue(assign);
            }
            for (int pp : pseudoParents){
                cost += constraintCosts.get(pp)[i][highView.get(pp)];
            }
            if (!isRootAgent()){
                cost += constraintCosts.get(parent)[i][highView.get(parent)];
            }
            if (minCost > cost){
                minCost = cost;
                valueIndex = i;
            }
        }
        Map<Integer,Integer> map = new HashMap<>(highView);
        map.put(id,valueIndex);
        for (int c : children) {
            sendMessage(new Message(id, c, MSG_VALUE, map));
        }
        stopProcess();
    }

    @Override
    public void runFinished() {
        super.runFinished();

        ResultCycle resultCycle = new ResultCycle();
        resultCycle.setAgentValues(id,valueIndex);
        int cost = 0;
        for (int pp : pseudoParents){
            cost += constraintCosts.get(pp)[valueIndex][highView.get(pp)];
        }
        if (!isRootAgent()){
            cost += constraintCosts.get(parent)[valueIndex][highView.get(parent)];
        }
        resultCycle.setTotalCost(cost);
        mailer.setResultCycle(id,resultCycle);
    }

    private class Util{
        NDData maxJoin;
        NDData minJoin;

        public Util(NDData maxJoin, NDData minJoin) {
            this.maxJoin = maxJoin;
            this.minJoin = minJoin;
        }
    }
}
