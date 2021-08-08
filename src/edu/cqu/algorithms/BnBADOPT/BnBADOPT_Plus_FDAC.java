package edu.cqu.algorithms.BnBADOPT;

import lib.result.ResultWithPrivacy;
import lib.core.Message;
import lib.core.SyncMailer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BnBADOPT_Plus_FDAC extends RetentionFrame_FDAC {
    private final static int MSG_VALUE = 0;
    private final static int MSG_TERMINATE = 1;
    private final static int MSG_COST = 2;
    private final static int MSG_SYNC = 3;

    private final static int PHASE_SYNC = 1;
    private final static int PHASE_BACKTRACK = 2;
    private final static int PHASE_ACPREPROCESS = 3;
    private final static int PHASE_SEARCH = 4;

    private final static int MAXVALUE = 100000;
//    private final static int lastId = 24;
private   int lastAgentId;
    private int ID;
    private Map<Integer, int[]> lb;
    private Map<Integer, int[]> ub;
    private int TH;
    private int[] subtreeLB;
    private int[] subtreeUB;
    private int[] delta;
    private int domainLb;
    private int domainUb;

    private CpaId cpaId;

    private Map<Integer,Integer> cpa;
    private int valueIndex;
    private Map<Integer, ValueMsg> LastChValueMsg;
    private Map<Integer, ValueMsg> LastPChValueMsg;
    private CostMsg  LastParentCostMsg;
    boolean ThReq;
    private boolean context_change;
    private Map<Integer, Boolean> SendValueMsg;
    private Map<Integer,Integer> backCpa;
    boolean  CallBacktrack;
    private int OPTCost;

    private boolean terminateflag;
    public BnBADOPT_Plus_FDAC(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer,int Lastid) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
        lastAgentId=Lastid;
        lb = new HashMap<>();
        ub = new HashMap<>();
        subtreeLB = new int[domain.length];
        subtreeUB = new int[domain.length];
        delta = new int[domain.length];
        cpaId = new CpaId();
        cpa = new HashMap<>();
        backCpa = new HashMap<>();
        terminateflag=false;
    }

    private int haveStart = -1;

    @Override
    protected void start() {
        if (isRootAgent()) {
            for (int i = 1; i <= lastAgentId; ++i)
                sendMessage(new Message(id,i,MSG_SYNC,height));
        }
    }

    protected void bnbStart() {
        for (int pp : pseudoParents) {
            cpaId.cpa.put(pp, 0);
            cpaId.id.put(pp, 0);
        }
        if (!isRootAgent()) {
            cpaId.cpa.put(parent, 0);
            cpaId.id.put(parent, 0);
        }
        cpa.putAll(cpaId.cpa);
        ID = 0;
        for (int child : children) {
            for (int i = 0; i < domain.length; ++i) {
                initChild(child, i);
            }
        }
        LastChValueMsg=new HashMap<>();
        LastPChValueMsg=new HashMap<>();
        ThReq=true;
        context_change=true;
        SendValueMsg=new HashMap<>();
        for(int c:children) {
            SendValueMsg.put(c, false);
        }
        CalculateAP_cost();
        initSelf();
        backtrack();
    }

    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        switch (message.getType()) {
            case MSG_SYNC:{
                haveStart = PHASE_SYNC;
            }
            break;
            case PHASE_SEARCH:{
                haveStart = PHASE_SEARCH;
            }
            break;
            case MSG_VALUE:{
                CallBacktrack=true;
                int pp = message.getIdSender();
                ValueMsg valueMsg = (ValueMsg) message.getValue();
                messageSizeCount+=5;
                if(valueMsg.ub_ac<UB_AC){
                    UB_AC=valueMsg.ub_ac;
                    UPdateAC_LBUB=true;
                }
                if(valueMsg.lb_ac>LB_AC){
                    LB_AC=valueMsg.lb_ac;
                    UPdateAC_LBUB=true;
                }
                 Map<Integer,Integer> previousCpa=new HashMap<>(cpa);
                //todo merge
                if (cpaId.id.get(pp) <= valueMsg.ID) {
                    cpaId.id.put(pp, valueMsg.ID);
                    cpaId.cpa.put(pp, valueMsg.value);
                    cpa.put(pp, valueMsg.value);
                }
                if (!compatible(previousCpa)) {
                    CalculateAP_cost();
                    context_change=true;
                    for (int child : children) {
                        if (branchPseudoParents.get(child).contains(pp))
                            for (int i = 0; i < domain.length; ++i)
                                initChild(child, i);
                    }
                    initSelf();
                }
                if (pp == parent&&cpaId.id.get(pp)<=valueMsg.ID) {
                    TH = valueMsg.ub;
                }
            }
            break;
            case MSG_COST:{
                CallBacktrack=true;
                int c = message.getIdSender();
                CostMsg costMsg = (CostMsg) message.getValue();
                messageSizeCount+=4+costMsg.cpaId.cpa.size()*4;

                Map<Integer, Integer> cpa_p = new HashMap<>(cpa);
                SendValueMsg.put(c,costMsg.ThReq);
                if(lb_AC.get(c)<costMsg.LB_AC)lb_AC.put(c, costMsg.LB_AC);
                costMsg.cpaId.cpa.remove(c);
                costMsg.cpaId.id.remove(c);
                int value =  costMsg.cpaId.cpa.get(id);

                costMsg.cpaId.cpa.remove(id);
                costMsg.cpaId.id.remove(id);
                //todo merge
                for (int pp : costMsg.cpaId.cpa.keySet()) {
                    if (!cpaId.id.containsKey(pp) || cpaId.id.get(pp) < costMsg.cpaId.id.get(pp)) {
                        cpaId.cpa.put(pp, costMsg.cpaId.cpa.get(pp));
                        cpaId.id.put(pp, costMsg.cpaId.id.get(pp));
                        cpa.put(pp, costMsg.cpaId.cpa.get(pp));
                    }
                }
                //todo priorityMerge(pp, valueMsg.value, valueMsg.ID, cpa_p);
                if (!compatible(cpa_p)) {
                    context_change=true;
                    for (int child : children) {
                        for (int pp : branchPseudoParents.get(child)) {
                            if (cpa_p.containsKey(pp)) {
                                if (cpa.get(pp).intValue() != cpa_p.get(pp)) {
                                    for (int i = 0; i < domain.length; ++i)
                                        initChild(child, i);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (compatible(costMsg.cpaId.cpa)) {

                    lb.get(c)[value] = Integer.max(lb.get(c)[value], costMsg.lb);
                    ub.get(c)[value] = Integer.min(ub.get(c)[value], costMsg.ub);

                }
                if (!compatible(cpa_p))initSelf();
            }
            break;
            case MSG_TERMINATE:{
                CallBacktrack=true;
                terminateflag=true;

            }
            break;
        }
    }

    @Override
    public void runFinished() {
        super.runFinished();
        ResultWithPrivacy resultWithPrivacy = new ResultWithPrivacy();
        resultWithPrivacy.setAgentValues(id,0);
        resultWithPrivacy.setLeakedEntropy(100);
        resultWithPrivacy.setTotalEntropy(10000);
        if (isRootAgent()){
            resultWithPrivacy.setTotalCost(domainLb);
            resultWithPrivacy.setUb(OPTCost);
        }
        resultWithPrivacy.setMessageSizeCount(messageSizeCount);
        mailer.setResultCycle(id,resultWithPrivacy);
    }
    private boolean compatible(Map<Integer, Integer> cpa_p) {
        for (int id : cpa_p.keySet()) {
            if (cpa.get(id).intValue() != cpa_p.get(id))
                return false;
        }
        return true;
    }

    @Override
    public void allMessageDisposed() {
        super.allMessageDisposed();
        if (haveStart == PHASE_SYNC) {
           // bnbStart();
            for (int child : children) {
                for (int i = 0; i < domain.length; ++i) {
                    initChild(child, i);
                }
            }
            InitialAC();
            FDAC();
            haveStart = PHASE_ACPREPROCESS;
        }
        if (haveStart == PHASE_ACPREPROCESS) {
            if(isRootAgent())
                 {
                     boolean search=true;
                     for (int i = 1; i <= lastAgentId; ++i){
                        if(!QuiescenceAgent.contains(i)) search=false;
                     }
                     if(search) {
                         for (int i = 1; i <= lastAgentId; ++i)
                             sendMessage(new Message(id, i, PHASE_SEARCH, height));
                     }
                }
        }
        if (haveStart == PHASE_SEARCH) {
            bnbStart();
            haveStart = PHASE_BACKTRACK;
        }
        if (haveStart == PHASE_BACKTRACK) {
           if(CallBacktrack==true)
            backtrack();
        }
    }

    private void initChild(int child, int value) {
        if (!lb.containsKey(child))
            lb.put(child, new int[domain.length]);
        if (!ub.containsKey(child))
            ub.put(child, new int[domain.length]);
        //todo h(child, value)
        int initlb=0;
        lb.get(child)[value] = initlb;
        ub.get(child)[value] = MAXVALUE;
    }
    @Override
    protected void CheckDomainForDeletions(){
        UPdateAC_LBUB=false;
        Set<Integer> DeletValue=new HashSet<>();
        for(int i:neighbourDomains_AC.get(id)){
            if(UnaryConstraints[i]+LB_AC>UB_AC){
                DeletValue.add(i);
     }
            int lb_sum=0;
            for (int child : children){
                if(branchPseudoParents.get(child).size()==0)
                {
                    lb_sum+=lb.get(child)[i];
                }
            }
            if(lb_sum>UB_AC){
                DeletValue.add(i);
            }
        }
        if(DeletValue.size()>0){
            ExtendCosts=true;
            Dom.removeAll(DeletValue);
            neighbourDomains_AC.get(id).removeAll(DeletValue);
            if(neighbourDomains_AC.get(id).size()==0){
                End_ACPreprocess=true;
                for(int nId:neighbours) {
                    sendMessage(new Message(id,nId,MSG_ACSTOP,true));
                }
            }
            else {
                for(int nId:neighbours) {
                    sendMessage(new Message(id,nId,MSG_ACDEL,DeletValue));
                    BinaryProjection(nId,id);
                }
            }
        }
    }
    private void CalculateAP_cost()
    {
        for (int i = 0; i < domain.length; ++i) {
            if (isRootAgent())
                delta[i] = 0;
            else {
                delta[i] = constraintCosts.get(parent)[i][cpaId.cpa.get(parent)];
                ncccs++;
            }
            for (int ppInd : pseudoParents) {
                delta[i] += constraintCosts.get(ppInd)[i][cpaId.cpa.get(ppInd)];
                ncccs++;
            }
        }
    }
    private void initSelf() {
        ID++;
        TH = MAXVALUE;
        ThReq=true;
        domainUb=domainLb=Integer.MAX_VALUE;
        int tmpInd = -1;
        int tmp = Integer.MAX_VALUE;
        for(int j=0;j<Dom.size();j++) {
            int i = Dom.get(j);
            int tmpSum = delta[i];
            for (int child : children)
                tmpSum += lb.get(child)[i];
            if (tmpSum < tmp) {
                tmp = tmpSum;
                tmpInd = i;
            }
        }
        if(Dom.size()>0) {
            valueIndex = tmpInd;//
        }
    }
    private void UPdateLBUB()
    {
        int dmlb = Integer.MAX_VALUE;
        int dmub = Integer.MAX_VALUE;
        for(int j=0;j<Dom.size();j++)
        {
            int i = Dom.get(j);
            int tmpLb = delta[i];
            int tmpUb = delta[i];
            for (int child : children) {
                tmpLb += lb.get(child)[i];
                tmpUb += ub.get(child)[i];
            }
            subtreeLB[i] = tmpLb;
            subtreeUB[i] = tmpUb;
            dmlb = Integer.min(dmlb, tmpLb);
            dmub = Integer.min(dmub, tmpUb);
        }
        domainUb = dmub;
        domainLb = dmlb;
        if(isRootAgent()&&Dom.size()>0){
            if(domainUb<UB_AC){
                UB_AC=domainUb;
                UPdateAC_LBUB=true;
            }
            int lbac=0;
            for(int c:children)lbac+=lb_AC.get(c);
            lbac+= ProjectionToLB_AC;
            if(lbac>LB_AC) {
                LB_AC=lbac;
                UPdateAC_LBUB=true;
            }
        }
    }
    private void backtrack(){
        CallBacktrack=false;
        UPdateLBUB();
        CheckDomainForDeletions();
        UPdateLBUB();
        int tmpub=Integer.MAX_VALUE;
        int indexUb=0;
        for(int j=0;j<Dom.size();j++)
        {
            int i = Dom.get(j);
            if(domainLb==subtreeLB[i])
            {
                if(tmpub>subtreeUB[i])
                {
                    tmpub=subtreeUB[i];
                    indexUb=i;
                }
            }
        }
        if(Dom.size()>0) {
            if (subtreeLB[valueIndex] >= Integer.min(TH, domainUb) || !Dom.contains(valueIndex)) {  // deep first search

                if (indexUb != valueIndex) {
                    ID++;
                    valueIndex = indexUb;
                }
            }
        }
        UnaryProjection();
        if(ExtendCosts){
            ExtendCostsToAllParents();
            ExtendCosts=false;
        }
        if ((isRootAgent() && domainUb == domainLb)||terminateflag) {
            if(isRootAgent()) System.out.println("ub: " + domainLb + " " + domainUb);
            OPTCost=domainUb;
            for (int child : children)
                sendMessage(new Message(id, child, MSG_TERMINATE, null));
            stopProcess();
        }
        int sendUb = Integer.min(TH, domainUb) - delta[valueIndex];
            for (int child : children)
                sendUb -= lb.get(child)[valueIndex];

            for (int child : children) {
               if(LastChValueMsg.keySet().contains(child)&&LastChValueMsg.get(child).value==valueIndex&&!SendValueMsg.get(child)
               &&LastChValueMsg.get(child).ub==sendUb + lb.get(child)[valueIndex])  continue;
               else {
                   if (SendValueMsg.get(child) == true) SendValueMsg .put(child, false);
                   sendMessage(new Message(id, child, MSG_VALUE, new ValueMsg(valueIndex, ID, sendUb + lb.get(child)[valueIndex],UB_AC,LB_AC)));
                   LastChValueMsg.put(child, new ValueMsg(valueIndex, ID, sendUb + lb.get(child)[valueIndex],UB_AC,LB_AC));
               }
            }
            for (int pc : pseudoChildren) {
                if(LastPChValueMsg.keySet().contains(pc)&&LastPChValueMsg.get(pc).value==valueIndex)  continue;

                else {
                    sendMessage(new Message(id, pc, MSG_VALUE, new ValueMsg(valueIndex, ID, MAXVALUE,UB_AC,LB_AC)));
                    LastPChValueMsg.put(pc, new ValueMsg(valueIndex, ID, MAXVALUE,UB_AC,LB_AC));
                }
            }
            if (!isRootAgent()) {
                int pjttolb_ac=0;
                for(int c:children)pjttolb_ac+=lb_AC.get(c);
                pjttolb_ac+=ProjectionToLB_AC;
                CostMsg NewCostMsg=new  CostMsg(cpaId, domainLb, domainUb,ThReq,pjttolb_ac);
                if(LastParentCostMsg!=null&&CostMsgsEqual(LastParentCostMsg,NewCostMsg)&&!ThReq&&!context_change);
                else {
                    sendMessage(new Message(id, parent, MSG_COST, NewCostMsg));
                    LastParentCostMsg=new  CostMsg(cpaId, domainLb, domainUb,ThReq,pjttolb_ac);
                    if(context_change)context_change=false;
                    if(ThReq)ThReq=false;
                }
                if (domainUb == domainLb)
                    backCpa.putAll(cpaId.cpa);
            }
    }
    private boolean CostMsgsEqual(CostMsg FirstCostMsg,CostMsg SecondCostMsg)
    {
        if(FirstCostMsg.lb!=SecondCostMsg.lb||FirstCostMsg.ub!=SecondCostMsg.ub) return false;
        for (int id : FirstCostMsg.cpaId.cpa.keySet()) {
            if (FirstCostMsg.cpaId.cpa.get(id).intValue() != SecondCostMsg.cpaId.cpa.get(id))
                return false;
        }
        return true;
    }


    class ValueMsg{
        private int value;
        private int ID;
        private int ub;
        private int ub_ac;
        private int lb_ac;

        public ValueMsg(int value, int ID, int ub, int ub_ac, int lb_ac) {
            this.value = value;
            this.ID = ID;
            this.ub = ub;
            this.ub_ac = ub_ac;
            this.lb_ac = lb_ac;
        }
    }

    class CostMsg{
        private CpaId cpaId;
        private int lb;
        private int ub;
        boolean ThReq;
        private int LB_AC;

        public CostMsg(CpaId cpaId, int lb, int ub,boolean ThReq,int lb_ac) {
            this.cpaId = new CpaId(cpaId.cpa, cpaId.id);
            this.lb = lb;
            this.ub = ub;
            this.ThReq=ThReq;
            this.LB_AC=lb_ac;
        }
    }

    class CpaId{
        private Map<Integer, Integer> cpa;
        private Map<Integer, Integer> id;

        public CpaId() {
            cpa = new HashMap<>();
            id = new HashMap<>();
        }

        public CpaId(Map<Integer, Integer> cpa, Map<Integer, Integer> id) {
            this.cpa = new HashMap<>();
            this.cpa.putAll(cpa);
            this.id = new HashMap<>();
            this.id.putAll(id);
        }
    }
}
