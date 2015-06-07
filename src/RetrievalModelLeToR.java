/**
 * The BM25 retrieval model has 3 parameters: k_1, k_3, and b
 * <p/>
 * Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.util.*;

public class RetrievalModelLeToR extends RetrievalModel {

    //BM25
    public double k_1;
    public double k_3;
    public double b;

    //Indri
    public double mu;
    public double lambda;

    //ML model
    public String trainQryPath;
    public String trainRelPath;
    public String trainVectPath;
    public String testVectPath;
    public String pageRankPath;
    public List<Integer> disabledFeatures;
    public String svmLearnPath;
    public String svmTestPath;
    public String svmC;
    public String svmModelPath;
    public String svmScorePath;

    /**
     * Set a retrieval model parameter.
     *
     * @param parameterName
     * @param value
     * @return Always false because this retrieval model has no parameters.
     */
    public boolean setParameter(String parameterName, double value) {
        if (parameterName.equals("k_1") && value >= 0) {
            this.k_1 = value;
        } else if (parameterName.equals("k_3") && value>= 0) {
            this.k_3 = value;
        } else if (parameterName.equals("b") && value > 0 && value < 1) {
            this.b = value;
        }else if (parameterName.equals("mu") && value >= 0) {
            this.mu = value;
        }else if (parameterName.equals("lambda") && value >= 0) {
            this.lambda = value;
        }else {
            System.err.println("Error: Unknown parameter name for retrieval model " +"BM25: " + parameterName);
            return false;
        }
        return true;
    }

    /**
     * Set a retrieval model parameter.
     *
     * @param parameterName
     * @param value
     * @return Always false because this retrieval model has no parameters.
     */
    public boolean setParameter(String parameterName, String value) {
        if (parameterName.equals("trainingQueryFile")) {
            this.trainQryPath = value;
        }else if (parameterName.equals("trainingRelsFile")) {
            this.trainRelPath = value;
        }else if (parameterName.equals("trainingFeatureVectorsFile")) {
            this.trainVectPath = value;
        }else if (parameterName.equals("testingFeatureVectorsFile")) {
            this.testVectPath = value;
        }else if (parameterName.equals("pageRankFile")) {
            this.pageRankPath = value;
        }else if (parameterName.equals("featureDisable") ){
            this.disabledFeatures = new ArrayList<Integer>();
            if (!value.equals("")){
                String[] strList = value.split(",");
                for (String s : strList){
                    this.disabledFeatures.add(Integer.parseInt(s));
                }
            }
        }else if (parameterName.equals("svmRankLearnPath")) {
            this.svmLearnPath = value;
        }else if (parameterName.equals("svmRankClassifyPath") ) {
            this.svmTestPath = value;
        }else if (parameterName.equals("svmRankParamC")) {
            this.svmC = value;
        }else if (parameterName.equals("svmRankModelFile")) {
            this.svmModelPath = value;
        }else if (parameterName.equals("testingDocumentScores")){
            this.svmScorePath = value;
        }else{
            System.err.println("Error: Unknown parameter name for retrieval model " +
                    "LeToR: " +
                    parameterName);
            return false;
        }

        return true;
    }

}
