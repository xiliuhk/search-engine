/**
 * The BM25 retrieval model has 3 parameters: k_1, k_3, and b
 * <p/>
 * Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelBM25 extends RetrievalModel {

    public double k_1;
    public double k_3;
    public double b;


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
        } else if (parameterName.equals("k_3") && value >= 0) {
            this.k_3 = value;
        } else if (parameterName.equals("b") && value > 0 && value < 1) {
            this.b = value;
        } else {
            System.err.println("Error: Unknown parameter name for retrieval model " +
                    "BM25: " +
                    parameterName);
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
        System.err.println("Error: Unknown parameter name for retrieval model " +
                "BM25: " +
                parameterName);
        return false;
    }

}
