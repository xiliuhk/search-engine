/**
 * The BM25 retrieval model has 3 parameters: k_1, k_3, and b
 * <p/>
 * Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelIndri extends RetrievalModel {

    public double mu;
    public double lambda;
    public double b;


    /**
     * Set a retrieval model parameter.
     *
     * @param parameterName
     * @param parametervalue
     * @return Always false because this retrieval model has no parameters.
     */
    public boolean setParameter(String parameterName, double value) {
        if (parameterName.equals("mu") && value >= 0) {
            this.mu = value;
        } else if (parameterName.equals("lambda") && value > 0 && value < 1) {
            this.lambda = value;
        } else {
            System.err.println("Error: Unknown parameter name for retrieval model " +
                    "Indri: " +
                    parameterName);
            return false;
        }
        return true;
    }

    /**
     * Set a retrieval model parameter.
     *
     * @param parameterName
     * @param parametervalue
     * @return Always false because this retrieval model has no parameters.
     */
    public boolean setParameter(String parameterName, String value) {
        System.err.println("Error: Unknown parameter name for retrieval model " +
                "RankedBoolean: " +
                parameterName);
        return false;
    }

}
