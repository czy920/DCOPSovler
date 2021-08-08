package lib.core;


import java.util.HashMap;
import java.util.Map;


public class Problem {
    public int[] allId;
    public Map<Integer,int[]> domains;
    public Map<Integer,Map<Integer,int[][]>> constraintCost;
    public Map<Integer,CommunicationStructure> communicationStructures;
    public Map<Integer,int[]> neighbours;

    public Map<Integer,int[]> getNeighbourDomain(int id){
        Map<Integer,int[]> neighbourDomain = new HashMap<>();
        int[] neighbour = neighbours.get(id);
        for (int neighbourId : neighbour){
            neighbourDomain.put(neighbourId,domains.get(neighbourId));
        }
        return neighbourDomain;
    }
}
