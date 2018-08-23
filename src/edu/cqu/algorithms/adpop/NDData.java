package edu.cqu.algorithms.adpop;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NDData {
    int[] data;
    Map<Integer,Integer> weights;
    Map<Integer,Integer> domainSize;
    public List<Integer> orderedId;
    public NDData(int[][] matrix,int rowId,int colId){
        domainSize = new HashMap<>();
        orderedId = new LinkedList<>();
        orderedId.add(rowId);
        orderedId.add(colId);
        domainSize.put(rowId,matrix.length);
        domainSize.put(colId,matrix[0].length);
        weights = new HashMap<>();
        weights.put(colId,1);
        weights.put(rowId,domainSize.get(colId));
        data = new int[domainSize.get(rowId) * domainSize.get(colId)];
        int index = 0;
        for (int i = 0; i < domainSize.get(rowId); i++){
            for (int j = 0; j < domainSize.get(colId); j++){
                data[index++] = matrix[i][j];
            }
        }
    }

    private NDData(){

    }

    public int getValue(Map<Integer,Integer> assign){
        int index = assign2Index(assign,weights);
        return data[index];
    }

    private static int assign2Index(Map<Integer, Integer> assign,Map<Integer,Integer> weights) {
        int index = 0;
        for (int id : weights.keySet()){
            index += weights.get(id) * assign.get(id);
        }
        return index;
    }

    public void merge(NDData ndData){
        if (ndData == null){
            return;
        }
         List<Integer> tmpId = new LinkedList<>(orderedId);
         Map<Integer,Integer> tmpDomainSize = new HashMap<>(domainSize);
         int size = data.length;
         for (int id : ndData.orderedId){
             if (!weights.containsKey(id)){
                 tmpId.add(id);
                 tmpDomainSize.put(id,ndData.domainSize.get(id));
                 size *= tmpDomainSize.get(id);
             }
         }
         Map<Integer,Integer> tmpWeight = rearrangeWeights(tmpId,tmpDomainSize);
         int[] tmpData = new int[size];
         DomainEnumerator enumerator = new DomainEnumerator(new AssignChangedListener() {
             @Override
             public void onAssignChanged(Map<Integer, Integer> assign) {
                 tmpData[assign2Index(assign,tmpWeight)] = getValue(assign) + ndData.getValue(assign);
             }
         },tmpDomainSize,tmpId);
         enumerator.enumerate();
         this.weights = tmpWeight;
         this.domainSize = tmpDomainSize;
         this.orderedId = tmpId;
         this.data = tmpData;
    }

    public NDData restrict(int id,int value){
        List<Integer> tmpId = new LinkedList<>(orderedId);
        tmpId.remove((Object)id);
        Map<Integer,Integer> tmpDomainSize = new HashMap<>(domainSize);
        tmpDomainSize.remove(id);
        Map<Integer,Integer> tmpWeights = rearrangeWeights(tmpId,tmpDomainSize);
        int[] tmpData = new int[data.length / domainSize.get(id)];
        DomainEnumerator enumerator = new DomainEnumerator(new AssignChangedListener() {
            @Override
            public void onAssignChanged(Map<Integer, Integer> assign) {
                assign.put(id,value);
                int cost = getValue(assign);
                assign.remove(id);
                tmpData[assign2Index(assign,tmpWeights)] = cost;
            }
        },tmpDomainSize,tmpId);
        enumerator.enumerate();
        NDData ndData = new NDData();
        ndData.domainSize = tmpDomainSize;
        ndData.data = tmpData;
        ndData.orderedId = tmpId;
        ndData.weights = tmpWeights;
        return ndData;
    }

    private NDData eliminate(int id, boolean isMin){
        List<Integer> tmpId = new LinkedList<>(orderedId);
        tmpId.remove((Object)id);
        Map<Integer,Integer> tmpDomainSize = new HashMap<>(domainSize);
        tmpDomainSize.remove(id);
        Map<Integer,Integer> tmpWeights = rearrangeWeights(tmpId,tmpDomainSize);
        int[] tmpData = new int[data.length / domainSize.get(id)];
        DomainEnumerator enumerator = new DomainEnumerator(new AssignChangedListener() {
            @Override
            public void onAssignChanged(Map<Integer, Integer> assign) {
                int winner = isMin ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                for (int i = 0; i < domainSize.get(id); i++){
                    assign.put(id,i);
                    int cost = getValue(assign);
                    if (isMin){
                        winner = Integer.min(winner,cost);
                    }
                    else {
                        winner = Integer.max(winner,cost);
                    }
                }
                assign.remove(id);
                tmpData[assign2Index(assign,tmpWeights)] = winner;
            }
        },tmpDomainSize,tmpId);
        enumerator.enumerate();
        NDData ndData = new NDData();
        ndData.domainSize = tmpDomainSize;
        ndData.data = tmpData;
        ndData.orderedId = tmpId;
        ndData.weights = tmpWeights;
        return ndData;
    }

    public int getDim(){
        return orderedId.size();
    }

    public boolean containsDim(int id){
        return weights.containsKey(id);
    }

    public NDData copy(){
        NDData ndData = new NDData();
        ndData.domainSize = new HashMap<>(domainSize);
        ndData.weights = new HashMap<>(weights);
        ndData.orderedId = new LinkedList<>(orderedId);
        int[] tmpData = new int[data.length];
        for (int i = 0; i < data.length; i++){
            tmpData[i] = data[i];
        }
        ndData.data = tmpData;
        return ndData;
    }

    public NDData max(int id){
        return eliminate(id,false);
    }

    public NDData min(int id){
        if (!domainSize.containsKey(id)){
            return copy();
        }
        return eliminate(id,true);
    }

    public int argMin(int id,Map<Integer,Integer> highView){
        if (!domainSize.containsKey(id)){
            return 0;
        }
        int[] opt = new int[domainSize.get(id)];
        int min = Integer.MAX_VALUE;
        int ind = -1;
        Map<Integer,Integer> assign = new HashMap<>(highView);
        for (int i = 0; i < opt.length; i++){
            assign.put(id,i);
            int cost = getValue(assign);
            if (min > cost){
                min = cost;
                ind = i;
            }
            opt[i] = cost;
        }
        return ind;
    }


    private static Map<Integer,Integer> rearrangeWeights(List<Integer> orderedId,Map<Integer,Integer> domainSize){
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
}
