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
import java.util.Iterator;

public class QryopSlScore extends QryopSl {

    /**
     * Construct a new SCORE operator.  The SCORE operator accepts just
     * one argument.
     *
     * @param q The query operator argument.
     * @return @link{QryopSlScore}
     */
    public DocLengthStore dls;

    public String field;
    public int ctf;

    public long C;
    public int N;

    public QryopSlScore(Qryop q) throws IOException {
        this.dls = new DocLengthStore(QryEval.READER);
        this.args.add(q);
    }

    /**
     * Construct a new SCORE operator.  Allow a SCORE operator to be
     * created with no arguments.  This simplifies the design of some
     * query parsing architectures.
     *
     * @return @link{QryopSlScore}
     */
    public QryopSlScore() throws IOException {
        this.dls = new DocLengthStore(QryEval.READER);
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

    /**
     * Evaluate the query operator.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {

        if ((r instanceof RetrievalModelUnrankedBoolean) || (r instanceof RetrievalModelRankedBoolean))
            return (evaluateBoolean(r));

        if (r instanceof RetrievalModelBM25)
            return evaluateBM25(r);
        if (r instanceof RetrievalModelIndri)
            return evaluateIndri(r);

        return null;
    }


    public QryResult evaluateBM25(RetrievalModel r) throws IOException {
        QryResult result = args.get(0).evaluate(r);
        RetrievalModelBM25 bm25 = (RetrievalModelBM25) r;

        this.N = QryEval.READER.numDocs();
        double curDf = result.invertedList.df;
        this.field = result.invertedList.field;
        double fieldDocCount = QryEval.READER.getDocCount(this.field);
        double avgLen = QryEval.READER.getSumTotalTermFreq(field) / fieldDocCount;
        double qtf = 1.0;

        // Each pass of the loop computes a score for one document. Note:
        // If the evaluate operation above returned a score list (which is
        // very possible), this loop gets skipped.
        for (int i = 0; i < result.invertedList.df; i++) {
            //BM25. tf, idf
            double curScore = 1.0;
            int curDocId = result.invertedList.postings.get(i).docid;
            double curTf = result.invertedList.getTf(i);
            double curDocLen = dls.getDocLength(field, curDocId);

            double RSJweight = Math.max(Math.log((this.N - curDf + 0.5) / (curDf + 0.5)), 0);
            double tfWeight = curTf / (curTf + bm25.k_1 * ((1 - bm25.b) + bm25.b * curDocLen / avgLen));
            double userWeight = (bm25.k_3 + 1.0) * qtf / (bm25.k_3 + qtf);

            curScore = RSJweight * tfWeight * userWeight;
            result.docScores.add(curDocId, curScore);
        }
        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.

        if (result.invertedList.df > 0)
            result.invertedList = new InvList();
        return result;
    }

    public QryResult evaluateIndri(RetrievalModel r) throws IOException {
        QryResult result = args.get(0).evaluate(r);
        RetrievalModelIndri indri = (RetrievalModelIndri) r;

        this.field = result.invertedList.field;
        this.ctf = result.invertedList.ctf;

        this.C = QryEval_BK.READER.getSumTotalTermFreq(this.field);

        for (int i = 0; i < result.invertedList.postings.size(); i++) {
            int curDocId = result.invertedList.getDocid(i);
            double curScore = 1.0;
            double curTf = result.invertedList.getTf(i);
            double curDocLen = dls.getDocLength(this.field, curDocId);
            double pMLE = (double) this.ctf / this.C;

            curScore = (1 - indri.lambda) * (curTf + indri.mu * pMLE) / (curDocLen + indri.mu) + indri.lambda * pMLE;
            result.docScores.add(curDocId, curScore);
        }

        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

        return result;
    }

    /**
     * Evaluate the query operator for boolean retrieval models.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

        // Evaluate the query argument.

        QryResult result = args.get(0).evaluate(r);

        // Each pass of the loop computes a score for one document. Note:
        // If the evaluate operation above returned a score list (which is
        // very possible), this loop gets skipped.


        for (int i = 0; i < result.invertedList.df; i++) {

            // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY.
            // Unranked Boolean. All matching documents get a score of 1.0.
            double curScore = 1.0;
            int curDocId = result.invertedList.postings.get(i).docid;

            if (r instanceof RetrievalModelRankedBoolean) {
                curScore = result.invertedList.postings.get(i).tf;
            }
            result.docScores.add(curDocId, curScore);
        }

        // The SCORE operator should not return a populated inverted list.
        // If there is one, replace it with an empty inverted list.

        if (result.invertedList.df > 0)
            result.invertedList = new InvList();

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
        double defaultScore = 0.0;

        if (r instanceof RetrievalModelIndri) {

            RetrievalModelIndri indri = (RetrievalModelIndri) r;

            int tf = 0;
            double docLen = this.dls.getDocLength(this.field, (int) docid);

            double pMLE = (double) this.ctf / this.C;
            defaultScore = (1 - indri.lambda) * (tf + indri.mu * pMLE) / (docLen + indri.mu) + indri.lambda * pMLE;
        }

        return defaultScore;
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

        return ("#SCORE( " + result + ")");
    }
}
