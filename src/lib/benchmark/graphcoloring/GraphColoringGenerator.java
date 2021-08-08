package lib.benchmark.graphcoloring;

import lib.benchmark.randomdcops.RandomDCOPGenerator;

public class GraphColoringGenerator extends RandomDCOPGenerator {
    public GraphColoringGenerator(String name, int nbAgent, int domainSize, double density) {
        super(name, nbAgent, domainSize, -1, -1, density);
    }

    @Override
    protected int randomCost(int i, int j) {
        return i == j ? 1 : 0;
    }
}
