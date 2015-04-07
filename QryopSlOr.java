/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.HashMap;

public class QryopSlOr extends QryopSl {

    /**
     * It is convenient for the constructor to accept a variable number
     * of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
     *
     * @param q A query argument (a query operator).
     */
    public QryopSlOr(Qryop... q) {
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

        if (r instanceof RetrievalModelUnrankedBoolean)
            return (evaluateBoolean(r));
        if (r instanceof RetrievalModelRankedBoolean)
            return (evaluateBoolean(r));
        return null;
    }

    /**
     * Evaluates the query operator for boolean retrieval models,
     * including any child operators and returns the result.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws java.io.IOException
     */
    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        //  Initialization

        allocArgPtrs(r);
        QryResult result = new QryResult();


        HashMap<Integer, Double> scoreMap = new HashMap<Integer, Double>();

        for (int i = 0; i < this.argPtrs.size(); i++) {
            ScoreList curScoreList = this.argPtrs.get(i).scoreList;
            for (int j = 0; j < curScoreList.size(); j++) {
                int curDocId = curScoreList.getDocid(j);
                double curScore = curScoreList.getDocidScore(j);

                if (scoreMap.containsKey(curDocId)) {
                    curScore = Math.max(curScore, scoreMap.get(curDocId));
                }

                scoreMap.put((Integer) curDocId, curScore);
            }
        }

        for (Integer docId : scoreMap.keySet()) {
            double docScore = scoreMap.get(docId);
            result.docScores.add(docId, docScore);
        }

        freeArgPtrs();

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

        if (r instanceof RetrievalModelUnrankedBoolean)
            return (0.0);
        if (r instanceof RetrievalModelRankedBoolean)
            return (0.0);
        return 0.0;
    }

    /*
     *  Return a string version of this query operator.
     *  @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#OR( " + result + ")");
    }
}
