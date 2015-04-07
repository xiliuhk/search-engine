/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class QryopSlSum extends QryopSl {

    /**
     * Construct a new SCORE operator.  The SCORE operator accepts just
     * one argument.
     *
     * @param q The query operator argument.
     * @return @link{QryopSlScore}
     */
    public QryopSlSum(Qryop q) {
        this.args.add(q);
    }

    /**
     * Construct a new SCORE operator.  Allow a SCORE operator to be
     * created with no arguments.  This simplifies the design of some
     * query parsing architectures.
     *
     * @return @link{QryopSlScore}
     */
    public QryopSlSum() {
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param q The query argument to append.
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    public void add(Qryop... a) {
        for (Qryop q : a) {
            this.args.add(q);
        }
    }

    /**
     * Evaluate the query operator.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws java.io.IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        if (!(r instanceof RetrievalModelBM25)) {
            return null;
        } else {
            return (evaluateBM25(r));
        }
    }

    /**
     * Evaluate the query operator for boolean retrieval models.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws java.io.IOException
     */
    public QryResult evaluateBM25(RetrievalModel r) throws IOException {

        allocArgPtrs(r);
        // Evaluate the query argument.
        QryResult result = new QryResult();
        HashMap<Integer, Double> map = new HashMap<Integer, Double>();

        // record scores in hashmap by docid
        for (ArgPtr ptr : this.argPtrs) {
            for (; ptr.nextDoc < ptr.scoreList.size(); ptr.nextDoc++) {
                int curDocId = ptr.scoreList.getDocid(ptr.nextDoc);
                double curScore = ptr.scoreList.getDocidScore(ptr.nextDoc);
                if (map.containsKey(curDocId)) {
                    map.put(curDocId, curScore + map.get(curDocId));
                } else {
                    map.put(curDocId, curScore);
                }
            }
        }

        for (int docId : map.keySet()) {
            result.docScores.add(docId, map.get(docId));
        }

        freeArgPtrs();
        return result;
    }

    /*
     *  Calculate the default score for a document that does not match
     *  the query argument.  This score is 0 for many retrieval models,
     *  but not all retrieval models.
     *  @param r A retrieval model that controls how the operator behaves.
     *  @param docid The internal id of the document that needs a default score.
     *  @return The default score.
     */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

        return 0.0;
    }

    /**
     * Return a string version of this query operator.
     *
     * @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
            result += (i.next().toString() + " ");

        return ("#SUM( " + result + ")");
    }
}
