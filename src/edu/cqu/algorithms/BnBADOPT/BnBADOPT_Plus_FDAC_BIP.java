package edu.cqu.algorithms.BnBADOPT;

import lib.result.ResultWithPrivacy;
import lib.core.Message;
import lib.core.SyncMailer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BnBADOPT_Plus_FDAC_BIP extends RetentionFrame_FDAC_BIP {
    private final static int MSG_VALUE = 0;
    private final static int MSG_TERMINATE = 1;
    private final static int MSG_COST = 2;
    private final static int MSG_SYNC = 3;

    private final static int PHASE_SYNC = 1;
    private final static int PHASE_BACKTRACK = 2;
    private final static int PHASE_ACPREPROCESS = 3;
    private final static int PHASE_SEARCH = 4;

    private final static int MAXVALUE = 100000;
 //   private final static int lastId = 24;
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
  //  private int valueIndex;
    private Map<Integer, ValueMsg> LastChValueMsg;
    private Map<Integer, ValueMsg> LastPChValueMsg;
    private CostMsg  LastParentCostMsg;
    boolean ThReq;
    private boolean context_change;
    private Map<Integer, Boolean> SendValueMsg;
    private Map<Integer,Integer> backCpa;
    boolean  CallBacktrack;
    private boolean SendCost;
    private int OPTCost;

    private boolean terminateflag;

 //   private  StringBuilder TSInf ;
    public BnBADOPT_Plus_FDAC_BIP(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer,int Lastid) {
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
   //     TSInf = new StringBuilder();
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
        cpa.putAll(cpaId.cpa); // for retention
        ID = 0;
        SendValueMsg=new HashMap<>();

        SendCost=false;
        for (int child : children) {
            for (int i = 0; i < domain.length; ++i) {
                initChild(child, i);
            }
            SendValueMsg.put(child, false);
        }
        LastChValueMsg=new HashMap<>();
        LastPChValueMsg=new HashMap<>();
        ThReq=true;
        context_change=true;
        CalculateAP_cost();
        getDeletedValues(new HashMap<>(cpa),delta);
        getDomain();
        initSelf();
        backtrack();
    }
    private void Acceptability_Testing_forlb(int child, int value) {
        if (!DeleteValueToAC[value].get(child).containsAll(lbbackPrune[value].get(child)))
        {
            initChildlb(child, value);
            ReqCost[value].put(child,true);
        }
    }
    private void Acceptability_Testing_forub(int child, int value) {
        if (!ubbackPrune[value].get(child).containsAll(DeleteValueToAC[value].get(child)))
            initChildub(child, value);
        ReqCost[value].put(child,true);
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
                messageSizeCount+=5+valueMsg.DelValFromP.size();
                Map<Integer,Integer> previousCpa=new HashMap<>(cpa);
                if(valueMsg.ub_ac<UB_AC){
                    UB_AC=valueMsg.ub_ac;
                    UPdateAC_LBUB=true;
                }
                if(valueMsg.lb_ac>LB_AC){
                    LB_AC=valueMsg.lb_ac;
                    UPdateAC_LBUB=true;
                }
                //todo merge
                boolean  Pprune_change=false;
                if (cpaId.id.get(pp) <= valueMsg.ID) {
                    cpaId.id.put(pp, valueMsg.ID);
                    cpaId.cpa.put(pp, valueMsg.value);
                    cpa.put(pp, valueMsg.value);
                    if (pp == parent) {
                        if(!DV_P.equals((Set<Integer>) valueMsg.DelValFromP)) {
                            Pprune_change = true;
                            DV_P.clear();
                            DV_P.addAll(valueMsg.DelValFromP);
                        }
                        SendCost=valueMsg.CostReq;//
                    }
                }
                if (!compatible(previousCpa)) {

                    CalculateAP_cost();
                    getDeletedValues(new HashMap<>(cpa),delta);
                    getDomain();
                    Pprune_change=false;
                    context_change=true;
                    for (int child : children) {
                        if (branchPseudoParents.get(child).contains(pp)) {
                            for (int i = 0; i < domain.length; ++i){
                                initChild(child, i);}
                        }
                        else {
                            for (int i = 0; i < domain.length; ++i) {
                                Acceptability_Testing_forlb(child,i);
                                Acceptability_Testing_forub(child,i);
                            }
                        }
                    }
                    initSelf();
                }
                if(Pprune_change){
                        getDomain();
                }
                if (pp == parent&&cpaId.id.get(pp)<=valueMsg.ID) {
                    TH = valueMsg.ub;
                }
            }
            break;
            case MSG_COST:{

                int c = message.getIdSender();
                CostMsg costMsg = (CostMsg) message.getValue();
                messageSizeCount+=costMsg.lbbackPrune.size()+costMsg.ubbackPrune.size()+4+costMsg.cpaId.cpa.size()*4;

                CallBacktrack=true;
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
                        boolean compatible=true;
                        for (int pp : branchPseudoParents.get(child)) {
                            if (cpa_p.containsKey(pp)) {
                                if (cpa.get(pp).intValue() != cpa_p.get(pp)) {
                                    for (int i = 0; i < domain.length; ++i) {
                                        initChild(child, i);
                                    }
                                    compatible=false;
                                    break;
                                }
                            }
                        }
                        if(compatible){
                            for (int i = 0; i < domain.length; ++i) {
                                Acceptability_Testing_forlb(child,i);
                                Acceptability_Testing_forub(child,i);
                            }
                        }
                    }
                }
                if (compatible(costMsg.cpaId.cpa)) {
                    UpdataChildlb(c,value,costMsg);
                    UpdataChildub(c,value,costMsg);

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
    private void UpdataChildlb(int child, int value,CostMsg costMsg)
    {
        if(!DeleteValueToAC[value].get(child).containsAll(costMsg.lbbackPrune))
        {
            ReqCost[value].put(child,true);
        }
        else {
            lb.get(child)[value] = Integer.max(lb.get(child)[value], costMsg.lb);
            if(costMsg.push) lb.get(child)[value] = MAXVALUE;
            if(lb.get(child)[value]==costMsg.lb||costMsg.push)
                lbbackPrune[value].put(child, new HashSet<>(costMsg.lbbackPrune));

        }
    }
    private void UpdataChildub(int child, int value,CostMsg costMsg)
    {
        if(!costMsg.ubbackPrune.containsAll(DeleteValueToAC[value].get(child)))
        {
            ReqCost[value].put(child,true);
        }
        else {
            ub.get(child)[value] = Integer.min(ub.get(child)[value], costMsg.ub);
            if(costMsg.push)ub.get(child)[value] = MAXVALUE;
            if(ub.get(child)[value]==costMsg.ub||costMsg.push)
                ubbackPrune[value].put(child, new HashSet<>(costMsg.ubbackPrune));
        }
    }
    private void initChild(int child, int value) {
        if (!lb.containsKey(child))
            lb.put(child, new int[domain.length]);
        if (!ub.containsKey(child))
            ub.put(child, new int[domain.length]);
        //todo h(child, value)
        initChildlb(child,value);
        initChildub(child,value);
    }

    private void initChildlb(int child, int value) {
        int initlb=0;
        lb.get(child)[value] = initlb;
//        CostReq[value].put(child,true);
//        lbbackPrune[value].get(child).clear();
    }
    private void initChildub(int child, int value) {
        ub.get(child)[value] = MAXVALUE;
//        CostReq[value].put(child,true);
//        for(int i=0;i<domain.length;i++)ubbackPrune[value].get(child).add(i);
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

    @Override
    protected void CheckDomainForDeletions(){
        UPdateAC_LBUB=false;
        Set<Integer> DeletValue=new HashSet<>();
        for(int i:neighbourDomains_AC.get(id)){
            if(UnaryConstraints[i]+LB_AC>UB_AC){
                DeletValue.add(i);
          }
//            int lb_sum=0;
//            for (int child : children){
//                if(branchPseudoParents.get(child).size()==0)
//                {
//                    if(lb.get(child)[i]<MAXVALUE)lb_sum+=lb.get(child)[i];
//                }
//            }
//            if(lb_sum>UB_AC){
//                DeletValue.add(i);
//            }
        }
        if(DeletValue.size()>0){
            ACDeletValue.addAll(DeletValue);
            ExtendCosts=true;
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

        Dom.removeAll(ACDeletValue);
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
               if(LastChValueMsg.keySet().contains(child)&&LastChValueMsg.get(child).value==valueIndex&&!SendValueMsg.get(child)&&!ReqCost[valueIndex].get(child)
               &&LastChValueMsg.get(child).ub==sendUb + lb.get(child)[valueIndex]
                       && LastChValueMsg.get(child).DelValFromP.equals(DeleteValueToAC[valueIndex].get(child)))  continue;
               else {
                   if (SendValueMsg.get(child) == true) SendValueMsg .put(child, false);
                   sendMessage(new Message(id, child, MSG_VALUE, new ValueMsg(valueIndex, ID, sendUb + lb.get(child)[valueIndex],UB_AC,LB_AC,DeleteValueToAC[valueIndex].get(child),ReqCost[valueIndex].get(child))));
                   LastChValueMsg.put(child, new ValueMsg(valueIndex, ID, sendUb + lb.get(child)[valueIndex],UB_AC,LB_AC, DeleteValueToAC[valueIndex].get(child),ReqCost[valueIndex].get(child)));
                   if (ReqCost[valueIndex].get(child) == true) ReqCost[valueIndex].put(child,false);
               }
            }
            for (int pc : pseudoChildren) {
                if(LastPChValueMsg.keySet().contains(pc)&&LastPChValueMsg.get(pc).value==valueIndex)  continue;

                else {
                    sendMessage(new Message(id, pc, MSG_VALUE, new ValueMsg(valueIndex, ID, MAXVALUE,UB_AC,LB_AC,new HashSet<>(),false)));
                    LastPChValueMsg.put(pc, new ValueMsg(valueIndex, ID, MAXVALUE,UB_AC,LB_AC,new HashSet<>(),false));
                }
            }
            if (!isRootAgent()) {
                int pjttolb_ac=0;
                for(int c:children)pjttolb_ac+=lb_AC.get(c);
                pjttolb_ac+=ProjectionToLB_AC;

                Set<Integer> lbPPrune=new HashSet<>(DV_P);
                lbPPrune.removeAll(DV_Self);
                Set<Integer> ubPPrune=new HashSet<>(DV_P);
                ubPPrune.addAll(DV_Self);
                CostMsg NewCostMsg=new  CostMsg(cpaId, domainLb, domainUb,ThReq,pjttolb_ac,lbPPrune,ubPPrune,Dom.size()<=0);
                if(LastParentCostMsg!=null&&CostMsgsEqual(LastParentCostMsg,NewCostMsg)&&!ThReq&&!context_change&&!SendCost);
                else {
                    sendMessage(new Message(id, parent, MSG_COST, NewCostMsg));
                    LastParentCostMsg=new  CostMsg(cpaId, domainLb, domainUb,ThReq,pjttolb_ac,lbPPrune,ubPPrune,Dom.size()<=0);
                    if(context_change)context_change=false;
                    if(ThReq)ThReq=false;
                    if(SendCost)SendCost=false;
                }
                if (domainUb == domainLb)
                    backCpa.putAll(cpaId.cpa);
            }
    }
    private boolean CostMsgsEqual(CostMsg FirstCostMsg,CostMsg SecondCostMsg)
    {
        if(FirstCostMsg.lb!=SecondCostMsg.lb||FirstCostMsg.ub!=SecondCostMsg.ub||!FirstCostMsg.lbbackPrune.equals(SecondCostMsg.lbbackPrune)
                ||!FirstCostMsg.lbbackPrune.equals(SecondCostMsg.ubbackPrune)||FirstCostMsg.push!=SecondCostMsg.push) return false;
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
        private Set<Integer> DelValFromP;
        private boolean CostReq;
        public ValueMsg(int value, int ID, int ub, int ub_ac, int lb_ac,Set<Integer> DelValFromP,boolean CostReq) {
            this.value = value;
            this.ID = ID;
            this.ub = ub;
            this.ub_ac = ub_ac;
            this.lb_ac = lb_ac;
            this.DelValFromP=new HashSet<>(DelValFromP);
            this.CostReq=CostReq;
        }
    }

    class CostMsg{
        private CpaId cpaId;
        private int lb;
        private int ub;
        boolean ThReq;
        private int LB_AC;
        boolean push;
        private Set<Integer> lbbackPrune;
        private Set<Integer> ubbackPrune;

        public CostMsg(CpaId cpaId, int lb, int ub,boolean ThReq,int lb_ac,Set<Integer> lbbackPrune,Set<Integer> ubbackPrune, boolean push) {
            this.cpaId = new CpaId(cpaId.cpa, cpaId.id);
            this.lb = lb;
            this.ub = ub;
            this.ThReq=ThReq;
            this.LB_AC=lb_ac;
            this.push=push;
            this.lbbackPrune=new HashSet<>(lbbackPrune);
            this.ubbackPrune=new HashSet<>(ubbackPrune);
        }
    }

    class CpaId{
        private Map<Integer, Integer> cpa;
        private Map<Integer, Integer> id;
        boolean back;
        public CpaId() {
            cpa = new HashMap<>();
            id = new HashMap<>();
            back=false;
        }

        public CpaId(Map<Integer, Integer> cpa, Map<Integer, Integer> id) {
            this.cpa = new HashMap<>();
            this.cpa.putAll(cpa);
            this.id = new HashMap<>();
            this.id.putAll(id);
            back=false;
        }
    }
}
