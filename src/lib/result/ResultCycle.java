package lib.result;

import lib.result.annotations.AverageField;


public class ResultCycle extends Result {
    @AverageField
    public double[] costInCycle;

    public ResultCycle() {
        costInCycle = new double[0];
    }

    public void setCostInCycle(double[] costInCycle, int tail){
       this.costInCycle = new double[tail];
       for (int i = 0; i < tail; i++){
           this.costInCycle[i] = costInCycle[i];
       }
    }
}
