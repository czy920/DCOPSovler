package lib.benchmark.graphcoloring;

import lib.benchmark.randomdcops.RandomDCOPGenerator;

public class WeightedGraphColoringGenerator extends RandomDCOPGenerator {
    public WeightedGraphColoringGenerator(String name, int nbAgent, int domainSize, int minCost,int maxCost,double density) {
        super(name, nbAgent, domainSize,minCost,maxCost, density);
    }

    @Override
    protected int randomCost(int i, int j) {
        return i == j ? super.randomCost(i,j) : 0;
    }
}
