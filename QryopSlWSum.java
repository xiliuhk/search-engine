/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

public class QryopSlWSum extends QryopSl {

    /**
     * It is convenient for the constructor to accept a variable number
     * of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
     *
     * @param q A query argument (a query operator).
     */
    public ArrayList<Double> weights;

    public QryopSlWSum(Qryop... q) {
        this.weights = new ArrayList<Double>();
        for (int i = 0; i < q.length; i++)
            this.args.add(q[i]);
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws java.io.IOException
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluates the query operator, including any child operators and
     * returns the result.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws java.io.IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri)
            return evaluateIndri(r);
        return null;
    }

    /*Compute Indri score
      *if doc contains current term, retrieve its current score to indri formula,
      *otherwise compute the default score by Score then apply it to the formula
      * store the results in scoreMap<docid, IndriScore>
      *     */
    public QryResult evaluateIndri(RetrievalModel r) throws IOException {

        //initialize
        allocArgPtrs(r);
        QryResult result = new QryResult();
        double totalWeight = 0.0;
        for (double w : this.weights) {
            totalWeight += w;
        }

        TreeMap<Integer, Double> scoreMap = new TreeMap<Integer, Double>();
        HashMap<Integer, HashMap> invtListMap = new HashMap<Integer, HashMap>();

        for (int i = 0; i < this.argPtrs.size(); i++) {
            ScoreList curScoreList = this.argPtrs.get(i).scoreList;
            HashMap<Integer, Integer> docList = new HashMap<Integer, Integer>();
            //HashSet<Integer> uniqueDoc = new HashSet<Integer>();

            for (int j = 0; j < curScoreList.size(); j++) {
                int curDocId = curScoreList.getDocid(j);
                double curScore = 0.0;
                if (docList.keySet().contains(curDocId)) {
                    continue;
                } else {
                    docList.put(curDocId, j);
                }
                if (scoreMap.containsKey(curDocId)) {
                    continue;
                } else {
                    scoreMap.put(curDocId, 0.0);
                }
            }
            invtListMap.put(i, docList);
        }

        int qrySize = this.args.size();

        for (int curDocId : scoreMap.keySet()) {
            double curScore = scoreMap.get(curDocId);
            double docScore;
            for (int j = 0; j < qrySize; j++) {
                HashMap<Integer, Integer> curDocList = invtListMap.get(j);
                double curWeight = this.weights.get(j);
                Qryop curArg = this.args.get(j);
                ArgPtr curArgPtr = this.argPtrs.get(j);

                if (curDocList.keySet().contains(curDocId)) {
                    docScore = curArgPtr.scoreList.getDocidScore(curDocList.get(curDocId));
                } else {
                    docScore = ((QryopSl) curArg).getDefaultScore(r, curDocId);
                }
                curScore += docScore * (curWeight / totalWeight);
            }
            result.docScores.add(curDocId, curScore);
        }
        return result;
    }

    /*
     *  Calculate the default score for the specified document if it
     *  does not match the query operator.  This score is 0 for many
     *  retrieval models, but not all retrieval models.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @param docid The internal id of the document that needs a default score.
     *  @return The default score.
     */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        double defaultScore = 0.0;

        if (r instanceof RetrievalModelIndri) {
            defaultScore = 1.0;
            int qrySize = this.args.size();
            for (int i = 0; i < qrySize; i++) {
                double tmpScore = ((QryopSl) this.args.get(i)).getDefaultScore(r, docid);
                defaultScore *= Math.pow(tmpScore, 1.0 / qrySize);
            }
        }

        return defaultScore;
    }

    /*
     *  Return a string version of this query operator.
     *  @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#AND( " + result + ")");
    }
}
