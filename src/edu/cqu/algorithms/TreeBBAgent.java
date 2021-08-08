package edu.cqu.algorithms;

import lib.core.Message;
import lib.core.SyncMailer;
import lib.ordering.DFSSyncAgent;
import lib.result.ResultWithPrivacy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TreeBBAgent extends DFSSyncAgent {

    private static final int MSG_TO_NEXT = 0;
    private static final int MSG_BACKTRACK = 1;
    private static final int MSG_TERMINATE = 3;

    private static final int INFINITY = Integer.MAX_VALUE;
    private static final int INFEASIBLE = Integer.MAX_VALUE - 1;

    // to be reset
    private Map<Integer,Integer> valueForBranch;
    private Map<Integer,int[]> optForBranch;
    private int currentCompleteIndex;
    private int localBound;
    private int[] localCosts;
    private int ub;
    private long messageSizeCount;
    private int receivedTimespan;

    public TreeBBAgent(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer,int Lastid) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
        valueForBranch = new HashMap<>();
        optForBranch = new HashMap<>();
        currentCompleteIndex = -1;
        localBound = INFINITY;
        localCosts = new int[domain.length];
        ub = INFINITY;
    }

    @Override
    protected void pseudoTreeCreated() {
        if (id == 1){
            for (int child : children){
                int[] opt = new int[domain.length];
                Arrays.fill(opt,INFINITY);
                Arrays.fill(localCosts,0);
                valueForBranch.put(child,0);
                optForBranch.put(child,opt);
                MessageContent messageContent = new MessageContent();
                messageContent.solution.put(id,0);
                sendMessage(new Message(id,child,MSG_TO_NEXT,messageContent));
            }
        }
    }

    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        switch (message.getType()){
            case MSG_TO_NEXT:{
                MessageContent messageContent = (MessageContent) message.getValue();
                messageSizeCount += messageContent.solution.size()*2+2;
                receivedTimespan = messageContent.timespan;
                optForBranch.clear();
                valueForBranch.clear();
                currentCompleteIndex = -1;
                localBound = INFINITY;
                Arrays.fill(localCosts,0);
                int mincost=Integer.MAX_VALUE;
                for (int i = 0; i < domain.length; i++){
                    localCosts[i]= constraintCosts.get(parent)[i][messageContent.solution.get(parent)];
                    ncccs++;
                    for (int pp : pseudoParents){
                        localCosts[i] += constraintCosts.get(pp)[i][messageContent.solution.get(pp)];
                        ncccs++;
                    }
                    if(mincost>localCosts[i])mincost=localCosts[i];

                }
                if (children.size() == 0){
                    MessageContent otherMessageContent = messageContent.clone();
                    otherMessageContent.bound = mincost;

                    sendMessage(new Message(id,parent,MSG_BACKTRACK,otherMessageContent));
                }
                else {
                    ub = messageContent.bound;
                    messageContent.solution.put(id,0);
                    int tmp_ub = ub;
                    tmp_ub -= localCosts[0];

                    for (int child : children){
                        int[] opt = new int[domain.length];
                        Arrays.fill(opt,INFINITY);
                        optForBranch.put(child,opt);
                        valueForBranch.put(child,0);
                        MessageContent tmpMessageContent = messageContent.clone();
                        tmpMessageContent.bound = tmp_ub;
                        sendMessage(new Message(id,child,MSG_TO_NEXT,tmpMessageContent));
                        hasSentNext = true;
                    }
                }
                break;
            }
            case MSG_BACKTRACK:{
                MessageContent messageContent = (MessageContent) message.getValue();
                messageSizeCount += messageContent.solution.size()*2+2;
                int value = valueForBranch.get(message.getIdSender());
                if(messageContent.bound==INFEASIBLE){
                    for (int child : children)optForBranch.get(child)[value]=INFEASIBLE;
                }
                else if(optForBranch.get(message.getIdSender())[value]!=INFEASIBLE)optForBranch.get(message.getIdSender())[value] = messageContent.bound;

                value++;
                while(!IsFEASIBLE(value)){
                    for (int child : children)optForBranch.get(child)[value]=INFEASIBLE;
                    value++;
                }
                updateLocalBound();
                while (value < domain.length){
                    boolean canBreak = false;

                    int bound = Integer.min(localBound, ub -localCosts[value]);
                    bound = boundForBranch(bound,value);
                    if (bound <= 0){
                        for (int child : children)optForBranch.get(child)[value]=INFEASIBLE;
                        //optForBranch.get(message.getIdSender())[value] = INFEASIBLE;
                        value++;
                    }
                    else {
                        messageContent.solution.put(id,value);
                        MessageContent tmpMessageContent = messageContent.clone();
                        tmpMessageContent.bound = bound;
                        sendMessage(new Message(id,message.getIdSender(),MSG_TO_NEXT,tmpMessageContent));
                        hasSentNext = true;
                        canBreak = true;
                        valueForBranch.put(message.getIdSender(),value);
                    }
                   // valueForBranch.put(message.getIdSender(),value);
                    if (canBreak){
                        break;
                    }
                }
                if (value == domain.length){
                    valueForBranch.put(message.getIdSender(),value);
                    for (int child : children){
                        if(valueForBranch.get(child)<domain.length) return;
                    }
                    int cost_star = INFINITY;
                    for (int i = 0; i < domain.length; i++){
                        int cost = 0;
                        for (int child : children){
                            int cost_opt = optForBranch.get(child)[i];
                            if (cost_opt == INFINITY){
                                return;
                            }
                            if (cost_opt == INFEASIBLE || cost == INFINITY){
                                cost = INFINITY;
                                break;
                            }
                            cost += cost_opt;
                        }
                        if (cost == INFINITY){
                            continue;
                        }
                        cost +=localCosts[i];
                        if (cost_star > cost){
                            cost_star = cost;
                        }
                    }
                    if (id == 1){
                        ub=cost_star;
                        System.out.println("optimal cost:" + cost_star);
                        for (int child : children){
                            sendMessage(new Message(id,child,MSG_TERMINATE,null));
                        }
                        stopProcess();
                    }
                    else {
                        int cnt=0;
                        for(int i=0;i<domain.length;i++) {
                           // int i = Dom.get(j);
                            for (int child : children){
                                if(optForBranch.get(child)[i]==INFEASIBLE) {
                                    cnt++;
                                    break;
                                }
                            }
                            //if(opt==true)
                        }
                        if(cnt==domain.length) cost_star=INFEASIBLE;
                        MessageContent tmpMessageContent = messageContent.clone();
                        tmpMessageContent.solution.remove(id);
                        tmpMessageContent.bound = cost_star;
                        if (level < 5) {
                            System.out.println(id + "->" + parent + " for " + tmpMessageContent.solution.get(parent) + ":" + cost_star + " in " + ts);
                        }
                        sendMessage(new Message(id,parent,MSG_BACKTRACK,tmpMessageContent));
                    }
                }
                break;
            }
            case MSG_TERMINATE:
                messageSizeCount++;
                for (int child : children){
                    sendMessage(new Message(id,child,MSG_TERMINATE,null));
                }
                stopProcess();
                break;
        }
    }

    private int boundForBranch(int bound,int value){
        for (int child : children){
            int cost_star = optForBranch.get(child)[value];
            if (cost_star == INFINITY){
                continue;
            }
            if (cost_star == INFEASIBLE){
                return -1;
            }
            bound -= cost_star;
        }
        return bound;
    }

    @Override
    public void allMessageDisposed() {
        super.allMessageDisposed();
        ts++;
        hasSentNext = false;
    }

    private boolean hasSentNext;

    private int ts = 100;
    private Boolean IsFEASIBLE(int value)
    {if(value==domain.length) return true;
        for (int child : children) {
            if (optForBranch.get(child)[value]==INFEASIBLE) return false;
        }
        return true;
    }
    private void updateLocalBound(){

        for (int i = 0; i < domain.length; i++){
            int bound = 0;
            for (int child : children){
                int cost_star = optForBranch.get(child)[i];
//                if (cost_star == INFINITY){
//                    return;
//                }
                if (cost_star == INFEASIBLE||cost_star == INFINITY){
                    bound = -1;
                    break;
                }
                bound += cost_star;
            }
            if (bound >= 0 && bound < localBound){
                localBound = bound;
            }
            currentCompleteIndex = i;
        }
    }

    private class MessageContent{
        Map<Integer,Integer> solution;
        int bound;
        int timespan;
        public MessageContent(){
            solution = new HashMap<>();
            bound = INFINITY;
        }

        public MessageContent clone(){
            MessageContent otherMessageContent = new MessageContent();
            for (int id : solution.keySet()){
                otherMessageContent.solution.put(id,solution.get(id));
            }
            otherMessageContent.bound = bound;
            otherMessageContent.timespan = timespan;
            return otherMessageContent;
        }
    }

    @Override
    public void runFinished() {
        super.runFinished();
        ResultWithPrivacy resultCycle = new ResultWithPrivacy();
        resultCycle.setAgentValues(id,1);
        if (isRootAgent())
            resultCycle.setUb(ub);
        resultCycle.setMessageSizeCount(messageSizeCount);
        mailer.setResultCycle(id,resultCycle);
    }
}
