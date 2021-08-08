package edu.cqu.algorithms.BnBADOPT;


import algorithms.complete.BIP;
import lib.core.Message;
import lib.core.SyncMailer;
//import DFSSyncAgent;

import java.util.*;

public abstract class FDAC_BIP extends BIP {//abstract


    protected static final int MSG_ACSTOP = 0XFFFFA;
    protected static final int MSG_ACDEL = 0XFFFFB;
    protected static final int MSG_ACUCO = 0XFFFFC;
    protected static final int MSG_ACCPA = 0XFFFFD;
    protected static final int MSG_ACUB = 0XFFFFE;

    //TODO: to be sorted: from big to small

//    protected Set<Integer> pseudoChildren;

//    protected List<Map.Entry<Integer,Integer>> DomainTraverOrder;
  //  protected int CurrentIndex;
    protected int[] UnaryConstraints;
    protected Map<Integer, int[][]> constraintCosts_AC;
    protected Map<Integer, Set<Integer>> neighbourDomains_AC;
    protected int UB_AC;
    protected int LB_AC;
    protected Map<Integer, Integer> lb_AC;
    protected  int ProjectionToLB_AC;
    Set<Integer> ACDeletValue;
    private CPA cpa;
    private Map<Integer, CPA> branch;
    protected boolean End_ACPreprocess;
    protected boolean Quiescence;
    protected boolean ExtendCosts;
    protected boolean UPdateAC_LBUB;
    protected static Set<Integer> QuiescenceAgent=new HashSet<>();

//    protected int Cycle;

    public FDAC_BIP(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
    }
    protected abstract void SynchronousAlgorithmStart();//abstract
    @Override
    protected void AlgorithmStart() {
        branch=new HashMap<>();
        cpa=new CPA(new HashMap<>(),0);
        if (isLeafAgent()){
            sendMessage(new Message(id,parent,MSG_ACCPA,ChooseValue(cpa)));
        }
    }
    private CPA ChooseValue(CPA cpa){
        CPA Ncpa=new CPA(cpa.assignment,cpa.cpaCost);
        int optindex=0;
        int cpaCoat=0;
        int optCost=Integer.MAX_VALUE;
        for(int i=0;i<domain.length;i++) {
            int tmptCost=0;
            for (int nId : neighbours) {
                int minCost=Integer.MAX_VALUE;
                if(cpa.assignment.keySet().contains(nId)){
                    minCost=constraintCosts.get(nId)[i][cpa.assignment.get(nId)];
                    cpaCoat+=minCost;
                    ncccs++;
                }
                else {
                    for(int j=0;j<neighbourDomains.get(nId).length;j++){
                        minCost=Integer.min(minCost, constraintCosts.get(nId)[i][j]);
                        ncccs++;
                    }
                }
                tmptCost+=minCost;
            }
            if(tmptCost<optCost){
                optCost= tmptCost;
                optindex=i;
            }
        }
        Ncpa.assignment.put(id, optindex);
        Ncpa.cpaCost+=cpaCoat;
        return  Ncpa;
    }

    protected void InitialAC()
    {
        LB_AC=0;
        ProjectionToLB_AC=0;
        End_ACPreprocess=false;
        ExtendCosts=false;
        Quiescence=true;
        UPdateAC_LBUB=true;

        ACDeletValue=new HashSet<>();

        UnaryConstraints=new int[domain.length];
        neighbourDomains_AC=new HashMap<>();
        constraintCosts_AC=new HashMap<>();
        lb_AC=new HashMap<>();

        for(int nId:neighbours) {
            Set<Integer> ndomain=new HashSet<>();
            for(int j=0;j<neighbourDomains.get(nId).length;j++)ndomain.add(j);
            neighbourDomains_AC.put(nId, ndomain);

            int [][] constraint=new int[domain.length][neighbourDomains.get(nId).length];
            for(int i=0;i<domain.length;i++)
                for(int j=0;j<neighbourDomains.get(nId).length;j++) {
                    constraint[i][j] = constraintCosts.get(nId)[i][j];
                    ncccs++;
                }
            constraintCosts_AC.put(nId, constraint);
        }
        for(int c:children){
            lb_AC.put(c, 0) ;
        }
        Set<Integer> ndomain=new HashSet<>();
        for(int j=0;j<domain.length;j++)ndomain.add(j);
        neighbourDomains_AC.put(id, ndomain);
    }
    protected void AC()
    {
        Set<Integer> AllChildren=new HashSet<>(pseudoChildren);
        AllChildren.addAll(children);
        Set<Integer> AllParents=new HashSet<>(pseudoParents);
        AllParents.add(parent);
        for(int nId:neighbours) {//

            if(AllParents.contains(nId)){//nId<id
                BinaryProjection(id,nId);
                BinaryProjection(nId,id);
            }
            else
            {
                BinaryProjection(nId,id);
                BinaryProjection(id,nId);
            }
        }
        CheckDomainForDeletions();
        UnaryProjection();
    }
    protected void FDAC(){
        AC();
        ExtendCostsToAllParents();
    }
    protected void ExtendCostsToAllParents()
    {
        Set<Integer> AllChildren=new HashSet<>(pseudoChildren);
        AllChildren.addAll(children);
        Set<Integer> AllParents=new HashSet<>(pseudoParents);
        AllParents.add(parent);
        for(int nId:neighbours) {//

            if (AllParents.contains(nId)) {//nId<id
                ExtendCostsToNeighbor(nId);
            }
        }
    }
    protected void ExtendCostsToNeighbor(int id1){
        int [][] constraint;
        constraint = constraintCosts_AC.get(id1);
        int [] P=new int [neighbourDomains.get(id1).length];
        for(int i:neighbourDomains_AC.get(id1))
        {
            int tmp=Integer.MAX_VALUE;
            for(int j:neighbourDomains_AC.get(id)){
                tmp=Integer.min(tmp, constraint[j][i]+UnaryConstraints[j]);
                ncccs++;
            }
            P[i]=tmp;
        }
        int [] E=new int [domain.length];
        boolean extend=false;
        for(int i:neighbourDomains_AC.get(id))
        {
            int tmp=0;//Integer.MIN_VALUE
            for(int j:neighbourDomains_AC.get(id1)){
                tmp=Integer.max(tmp, P[j]-constraint[i][j]);
                ncccs++;
            }
            E[i]=tmp;
            if(tmp>0)extend=true;
        }
        if(extend){
            UnaryExtention(id1,E);
            BinaryProjection(id1,id);
            sendMessage(new Message(id,id1,MSG_ACUCO,E));
        }

    }
    protected void UnaryExtention(int id1,int[] E){
        int [][] constraint;
        constraint = constraintCosts_AC.get(id1);
        for(int i:neighbourDomains_AC.get(id))
        {
            if(E[i]!=0)
            for(int j:neighbourDomains_AC.get(id1)){
                constraint[i][j]=constraint[i][j]+E[i];
                ncccs++;
            }
            UnaryConstraints[i]=UnaryConstraints[i]-E[i];
        }
    }
    protected void ProcessUnaryCost(int sender,int[] E){
        int [][] constraint;
        constraint = constraintCosts_AC.get(sender);
       for(int i:neighbourDomains_AC.get(sender))
        {
            for(int j:neighbourDomains_AC.get(id)){
                constraint[j][i]=constraint[j][i]+E[i];
            }
        }
        BinaryProjection(id,sender);
    }
    protected void BinaryProjection(int id1,int id2)
    {
        int [][] constraint;
        int[] tiaoshi=new int[domain.length];
        if(id1==id) {
            constraint = constraintCosts_AC.get(id2);
            for(int i:neighbourDomains_AC.get(id1))
            {
                int tmpcost=Integer.MAX_VALUE;
                for(int j:neighbourDomains_AC.get(id2)){
                    tmpcost=Integer.min(tmpcost, constraint[i][j]);
                    ncccs++;
                }
                if(tmpcost!=0)
                for(int j:neighbourDomains_AC.get(id2)){
                    constraint[i][j]-=tmpcost;
                    ncccs++;
                }
                if(neighbourDomains_AC.get(id2).size()>0){
                    UnaryConstraints[i]+=tmpcost;
                    tiaoshi[i]=tmpcost;
                    if(tmpcost!=0) {
                        ExtendCosts = true;
                        UPdateAC_LBUB = true;
                    }
                }
            }
        }
        else if(id2==id){
            constraint=constraintCosts_AC.get(id1);

            for(int i:neighbourDomains_AC.get(id1))
            {
                int tmpcost=Integer.MAX_VALUE;
                for(int j:neighbourDomains_AC.get(id2)){
                    tmpcost=Integer.min(tmpcost, constraint[j][i]);
                    ncccs++;
                }
                if(tmpcost!=0)
                for(int j:neighbourDomains_AC.get(id2)){
                    constraint[j][i]-=tmpcost;
                    ncccs++;
                }
                if(neighbourDomains_AC.get(id2).size()>0){
                    tiaoshi[i]=tmpcost;
                }
            }
        }
    }
    protected void UnaryProjection()
    {
        int tmpcost=Integer.MAX_VALUE;
        for(int i:neighbourDomains_AC.get(id)){
            tmpcost=Integer.min(tmpcost, UnaryConstraints[i]);
        }
        if(neighbourDomains_AC.get(id).size()>0)ProjectionToLB_AC+=tmpcost;

        for(int i:neighbourDomains_AC.get(id)){
            UnaryConstraints[i]-=tmpcost;
        }
    }
//    protected String ArraytoDOTString(int [] a){
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append("[");
//        for (int i : a){
//            stringBuilder.append(" " + i  );
//        }
//        stringBuilder.append("]");
//        return stringBuilder.toString();
//    }
//    protected boolean FDACisPrint()
//    {
//        return false;
//    }
    protected abstract void CheckDomainForDeletions();
 //   protected abstract void appendTSInf(String str);
    protected void ProcessDelet(int sender,Set<Integer> DeletValue)
    {
        neighbourDomains_AC.get(sender).removeAll(DeletValue);
        BinaryProjection(id,sender);
    }
    protected void ProcessStop(int sender,boolean stop)
    {
        End_ACPreprocess=true;
        if(stop) {
            for(int nId:neighbours) {
                if(nId!=sender) sendMessage(new Message(id,nId,MSG_ACSTOP,true));
            }
        }
    }
    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        if(End_ACPreprocess) return;
        switch (message.getType()) {
            case MSG_ACCPA:{
                Quiescence=false;
                CPA Recpa = (CPA) message.getValue();
                int sender = message.getIdSender();
                branch.put(sender, Recpa);
                if (branch.size() == children.size()){
                    for(int cid:branch.keySet()){
                        cpa.assignment.putAll(branch.get(cid).assignment);
                        cpa.cpaCost+=branch.get(cid).cpaCost;
                    }
                    if(!isRootAgent())sendMessage(new Message(id,parent,MSG_ACCPA,ChooseValue(cpa)));
                    else {
                         UB_AC=ChooseValue(cpa).cpaCost;
                        for(int cid:children)
                        sendMessage(new Message(id, cid, MSG_ACUB,UB_AC ));
                        SynchronousAlgorithmStart();
                    }
                }
                break;
            }
            case MSG_ACUB:{
                Quiescence=false;
                UB_AC = (int) message.getValue();
               for(int cid:children){
                   sendMessage(new Message(id,cid,MSG_ACUB,UB_AC));
               }
                SynchronousAlgorithmStart();
                break;
            }
            case MSG_ACSTOP:{
                Quiescence=false;
                int sender = message.getIdSender();
                boolean stop = (boolean) message.getValue();
                ProcessStop(sender,stop);
                break;
            }
            case MSG_ACDEL:{
                Quiescence=false;
                int sender = message.getIdSender();
                Set<Integer> DeletValue =new HashSet<>((Set<Integer>)message.getValue());
                ProcessDelet(sender,DeletValue);
                break;
            }
            case MSG_ACUCO: {
                Quiescence = false;
                int sender = message.getIdSender();
                int [] E=(int [])(message.getValue());
               ProcessUnaryCost(sender,E.clone());
                break;
            }
        }
    }
    @Override
    public void allMessageDisposed() {
        super.allMessageDisposed();
        if(Quiescence){
            if(!QuiescenceAgent.contains(id))
            QuiescenceAgent.add(id);}
        else {
            QuiescenceAgent.remove(id);
            Quiescence=true;
        }

    }

    private class CPA{
        Map<Integer,Integer> assignment;
        int cpaCost;

        public CPA(Map<Integer, Integer> assignment, int cpaCost) {
            this.assignment = new HashMap<>(assignment);
            this.cpaCost = cpaCost;
        }
    }
}
