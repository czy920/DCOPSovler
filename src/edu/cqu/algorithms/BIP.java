package edu.cqu.algorithms;

import algorithms.Infrastructure.Cache;
import algorithms.Infrastructure.DV;
import algorithms.Infrastructure.NDData;
import lib.core.Message;
import lib.core.SyncMailer;
import lib.ordering.DFSSyncAgent;
import util.HashList;

import java.util.*;

public abstract class BIP extends DFSSyncAgent {

    protected static final int K = Parameter.kp;
    //TODO: to be sorted: from big to small
    protected boolean LocalPrFlg;

    protected Set<Integer> pseudoChildren;
    protected NDData JoinedlocalDcUtil;

    protected Set<Integer> DV_P;//deleed values from parent
    protected Set<Integer> DV_Self;//deleted values calculate by oneself
    protected ArrayList<Integer> Dom;
    protected Map<Integer,Set<Integer>> DeleteValueToAC[];
    protected Map<Integer,Set<Integer>> lbbackPrune[];
    protected Map<Integer,Set<Integer>> ubbackPrune[];
    protected Map<Integer, Boolean> ReqCost[];
    protected long buffersize;
    protected long hitbuffersize;
    protected int CurrentIndex;

    protected Cache<Integer, DV> cache ;
    public Map<Integer,Integer> weights;
    public Map<Integer,Integer> domainSize;
    public List<Integer> orderedId;
    public BIP(int id, int[] domain, int[] neighbours, Map<Integer, int[][]> constraintCosts, Map<Integer, int[]> neighbourDomains, SyncMailer mailer) {
        super(id, domain, neighbours, constraintCosts, neighbourDomains, mailer);
    }
    protected abstract void AlgorithmStart();
    public void AddDimensional(int id,int size){
        if(orderedId==null) orderedId = new HashList<>();
        orderedId.add(id);
        if(domainSize==null)domainSize = new HashMap<>();
        domainSize.put(id, size);
    }
    public static int assign2Index(Map<Integer, Integer> assign, Map<Integer,Integer> weights) {
        int index = 0;
        for (int id : weights.keySet()){
            index += weights.get(id) * assign.get(id);
        }
        return index;
    }
    public static Map<Integer,Integer> rearrangeWeights(List<Integer> orderedId,Map<Integer,Integer> domainSize){
        Map<Integer,Integer> weights = new HashMap<>();
        for (int i = orderedId.size() - 1; i >= 0; i--){
            if (i == orderedId.size() - 1){
                weights.put(orderedId.get(i),1);
            }
            else {
                int nextId = orderedId.get(i + 1);
                weights.put(orderedId.get(i),weights.get(nextId) * domainSize.get(nextId));
            }
        }
        return weights;
    }
    public void arrangeWeights()
    {
        if(orderedId!=null)weights=rearrangeWeights(orderedId,domainSize);
    }

    @Override
    protected void pseudoTreeCreated() {
        pseudoChildren = new HashSet<>();
        for (int neighbor : neighbours){
            if (neighbor != parent && !pseudoParents.contains(neighbor) && !children.contains(neighbor)){
                pseudoChildren.add(neighbor);
            }
        }
        weights = new HashMap<>();
        buffersize=0;
        hitbuffersize=0;
        LocalPrFlg=false;

        for(int ppid:pseudoParents)
        {
            AddDimensional(ppid, neighbourDomains.get(ppid).length);
        }
        if(!isRootAgent()) {
            AddDimensional(parent, neighbourDomains.get(parent).length);
        }
        Dom=new ArrayList<>();
        DV_P=new HashSet<>();
        DV_Self=new HashSet();
        DeleteValueToAC=InitialDeleteValueToAC();
        ReqCost=InitialCostReqToC();
        lbbackPrune=Initiallbubbkprune();
        ubbackPrune=Initiallbubbkprune();
        Initial_ACPruning();
        AlgorithmStart();
    }
    protected  Map<Integer,Set<Integer>> [] InitialDeleteValueToAC( )
    {
        Map<Integer,Set<Integer>> deleteValueToAC[];
        deleteValueToAC=new Map[domain.length];
        for(int i=0;i<domain.length;i++)
        {
            deleteValueToAC[i]=new HashMap<>();
            for(int acid:children)
            {
                Set<Integer> DelValue;
                DelValue=new HashSet<>();
                deleteValueToAC[i].put(acid, DelValue);
            }
            for(int acid:pseudoChildren)
            {
                Set<Integer> DelValue;
                DelValue=new HashSet<>();
                deleteValueToAC[i].put(acid, DelValue);
            }
        }
        return deleteValueToAC;
    }
    @Override
    public void disposeMessage(Message message) {
        super.disposeMessage(message);
        if (message.getType() < 20 && message.getType() >= 4){
            int iii = 0;
        }
    }
    private void Initial_ACPruning()
    {
            if (pseudoChildren.size() +children.size()<= K-1) {

                int PMK= (int)Math.pow(domain.length,pseudoChildren.size() +children.size()+1);
                int tatal=children.size();
                if(tatal==0)tatal=1;
               int  TMK=tatal*(int)Math.pow(domain.length,K);

                if(TMK-PMK>0) {
                   LocalPrFlg=true;
                   cache = new Cache<Integer, DV>(TMK - PMK, true);
                   for (int cid : children) {

                       JoinChildConstrain(constraintCosts.get(cid), cid);
                   }
                   for (int pcid : pseudoChildren) {
                       JoinChildConstrain(constraintCosts.get(pcid), pcid);
                   }
               }
            }
            arrangeWeights();
            if(JoinedlocalDcUtil!=null)ncccs+=JoinedlocalDcUtil.operationCount;
            if (isRootAgent()) {
                System.out.println("root id:"+id+" height:"+height);
            }
    }
    private void   JoinChildConstrain(int [][]constrain ,int pcid)
    {
        NDData data = new NDData(constrain,id,pcid);
        if (JoinedlocalDcUtil == null){
            JoinedlocalDcUtil = data;
        }
        else {
            JoinedlocalDcUtil.merge(data);
        }
    }

    protected void getDeletedValues(Map<Integer, Integer> assign,int[] UnaryCost)
    {
        Map<Integer, Integer> Assign=new HashMap<>(assign);
        CurrentIndex=0;
        if(!LocalPrFlg) {
            DeleteValueToAC=InitialDeleteValueToAC();
            DV_Self.clear();
            DV_P.clear();
            getDomain();
            return;
        }
        int key=assign2Index(Assign,weights);
        if(cache.map.containsKey(key)) {
            cache.get(key);
            DV_Self= new HashSet<>(((DV)cache.CurentDV).DV_Self);
            DeleteValueToAC = ((DV)cache.CurentDV).DeleteValueToAC;
                hitbuffersize+=((DV)cache.CurentDV).size;

        }
        else {
            List<Integer> ImportantDesc;
            ImportantDesc = new LinkedList<>(children);
             sortValue(Assign,ImportantDesc, UnaryCost);
        }
    }
    protected void getDomain()
    {
        Dom.clear();
        for(int i=0;i<domain.length;i++){
            if(DV_Self.contains(i)||DV_P.contains(i)) continue;
            else Dom.add(i);
        }
    }
    protected void sortValue(Map<Integer, Integer> assign,List<Integer> ImprotantDesc,int[] UnaryCost)
    {
      //  Map<Integer,Integer> ValueCnt=new HashMap<>();
        int data[] = new int[domain.length];
        Arrays.fill(data, 0);
        for (int i = 0; i < domain.length; i++)
            data[i] = UnaryCost[i];
        if(isLeafAgent()) {
            JoinedlocalDcUtil = new NDData(data, id);
            Arrays.fill(data, 0);
        }
        NDData StatisticResult=JoinedlocalDcUtil.ValueOccrrenceNum(id,data,ImprotantDesc);
        DeleteValueToAC=StatisticResult.getDelValueToAC();
        ncccs+=StatisticResult.operationCount;
        ncccs+=StatisticResult.operationCount*(1+ImprotantDesc.size())/domain.length;
        DV_Self.clear();
        for(int i=0;i<domain.length;i++) {
            assign.put(id, i);
            int Cnt= StatisticResult.getValue(i);
            if(Cnt==0) DV_Self.add(i);

        }
        int s=buffursize(DV_Self ,DeleteValueToAC);
        buffersize+=s;
        DV dv=new DV(new HashSet<>(DV_Self),DeleteValueToAC, s);
        int key=assign2Index(assign,weights);
        cache.put(key,dv);
    }
    protected int buffursize( Set<Integer> dv_self,Map<Integer,Set<Integer>> DeleteValueToAC[])
    {
        int size=1;
        size+=  dv_self.size();
        for(int j=0;j<domain.length;j++) {
            if(!dv_self.contains(j)) {
                Set<Integer> prunesetforac = DeleteValueToAC[j].keySet();
                for (int acid : prunesetforac) {
                    if (DeleteValueToAC[j].size() != 0) {
                        size += DeleteValueToAC[j].size();
                    }
                }
            }
        }
        return size;
    }
    protected  Map<Integer,Set<Integer>> [] Initiallbubbkprune( )
    {
        Map<Integer,Set<Integer>> lbubbkprune[];
        lbubbkprune=new Map[domain.length];
        for(int i=0;i<domain.length;i++)
        {
            lbubbkprune[i]=new HashMap<>();
            for(int acid:children)
            {
                Set<Integer> Dom_DVc;
                Dom_DVc=new HashSet<>();
                lbubbkprune[i].put(acid, Dom_DVc);
            }
        }
        return lbubbkprune;
    }
    protected  Map<Integer,Boolean> [] InitialCostReqToC( )
    {
        Map<Integer,Boolean> tCostReq[];
        tCostReq=new Map[domain.length];
        for(int i=0;i<domain.length;i++)
        {
            tCostReq[i]=new HashMap<>();
            for(int acid:children)
            {
                Boolean DelValue=true;
                tCostReq[i].put(acid, DelValue);
            }
        }
        return tCostReq;
    }
    protected int getNextValue()
    {
        int Value=domain.length;
        if(CurrentIndex<Dom.size()) {
            Value = Dom.get(CurrentIndex);
            CurrentIndex++;
        }
        return Value;
    }
    protected int getNextValue(int key)
    {
        int Value=domain.length;
        if(Dom.contains(key)) {
            int i = Dom.indexOf(key) + 1;
            if(i<Dom.size()) Value=Dom.get(i);
        }
        return Value;
    }
}
