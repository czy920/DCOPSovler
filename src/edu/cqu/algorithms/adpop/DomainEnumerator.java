package edu.cqu.algorithms.adpop;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DomainEnumerator {
    AssignChangedListener listener;
    Map<Integer,Integer> domainSize;
    List<Integer> orderedId;

    public DomainEnumerator(AssignChangedListener listener, Map<Integer, Integer> domainSize, List<Integer> orderedId) {
        this.listener = listener;
        this.domainSize = domainSize;
        this.orderedId = orderedId;
    }

    public void enumerate(){
        if (orderedId.size() == 0){
            listener.onAssignChanged(new HashMap<>());
            return;
        }
        Map<Integer,Integer> assign = new HashMap<>();
        for (int id : orderedId){
            assign.put(id,0);
        }
        int lastId = orderedId.get(orderedId.size() - 1);
        boolean exit = false;
        while (!exit){
            listener.onAssignChanged(assign);
            assign.put(lastId,assign.get(lastId) + 1);
            for (int i = orderedId.size() - 1; i >= 0; i--){
                int curId = orderedId.get(i);
                if (assign.get(curId).intValue() == domainSize.get(curId)){
                    if (i == 0){
                        exit = true;
                        break;
                    }
                    else {
                        assign.put(curId,0);
                        int preId = orderedId.get(i - 1);
                        assign.put(preId,assign.get(preId) + 1);
                    }
                }
                else {
                    break;
                }

            }
        }
    }
}
