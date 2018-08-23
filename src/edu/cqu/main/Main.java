package edu.cqu.main;

import edu.cqu.core.FinishedListener;
import edu.cqu.core.ProgressChangedListener;
import edu.cqu.core.Solver;
import edu.cqu.result.Result;
import edu.cqu.result.ResultCycle;
import edu.cqu.utils.FileUtils;

import java.io.File;

/**
 * Created by dyc on 2017/6/29.
 */
public class Main {
    public static void main(String[] args){
        Solver solver = new Solver();

        // problem/am.xml ADPOP problem/RANDOM_A_MAX_DCSP_100_7_p2_0.9_density_0.4_0.xml result/
        String algoConfigurePath;
        String algoName ;
        String problemPath;
        String solutionPath;
        if (args.length != 4) {
            System.out.println("The parameter should be (algoConfigurePath: problem/am.xml,algoName: ADPOP,problemPath: problem/RANDOM_A_MAX_DCSP_100_7_p2_0.9_density_0.4_0.xml,solutionPath: result/)");
            return;
        }
        algoConfigurePath = args[0];
        algoName = args[1];
        problemPath = args[2];
        solutionPath = args[3];

        if (!algoName.equals("ADPOP")) {
            solver.solve(algoConfigurePath, algoName, problemPath, new FinishedListener() {
                @Override
                public void onFinished(Result result) {
                    ResultCycle resultCycle = (ResultCycle) result;
                    StringBuilder out = new StringBuilder();
                    double[] costCycle = resultCycle.costInCycle;

                    for (int i = 0; i < costCycle.length; ++i) {
                        System.out.println("cycle: " + i + ", cost: " + costCycle[i] / 2.0);
                        out.append(costCycle[i]+"\n");
                    }
                    FileUtils.writeStringToFile(out.toString(),solutionPath + "" + algoName +"/cost.txt");
                }
            }, false, false);
        }
        else {
            solver.solve(algoConfigurePath, algoName, problemPath, new FinishedListener() {
                @Override
                public void onFinished(Result result) {
                    ResultCycle resultCycle = (ResultCycle) result;
                    System.out.println("Total cost: " + resultCycle.getTotalCost() / 2.0);
                    FileUtils.writeStringToFile(" " + resultCycle.getTotalCost(),solutionPath + "" + algoName +"/cost.txt");
                }
            }, false, false);
        }
    }
}
