/**
 *  This class implements the TERM operator for all retrieval models.
 *  The TERM operator stores a query term, for example "apple" in the
 *  query "#AND (apple pie).  Although it may seem odd to use a query
 *  operator to store a term, doing so makes it easy to build
 *  structured queries with nested query operators.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.List;
import java.util.Vector;


public class QryopIlNear extends QryopIl {

    private int distance;

    /**
     * Constructor.  The term is assumed to match the body field.
     *
     * @param dis n in Near/n
     * @param q   terms
     * @return @link{QryopIlTerm} A TERM query operator.
     */
    public QryopIlNear(int dis, Qryop... q) {
        for (int i = 0; i < q.length; i++) {
            this.args.add(q[i]);
        }
        this.distance = dis;
    }

    public QryopIlNear(int dis) {
        this.distance = dis;
    }

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws IOException
     */
    public void add(Qryop a) {
        this.args.add(a);
    }

    /**
     * Evaluates the query operator and returns the result.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws java.io.IOException
     */
    public QryResult evaluate(RetrievalModel r) throws IOException {
        allocArgPtrs(r);
        QryResult result = new QryResult();

        if (this.argPtrs.size() == 0) {
            return null;
        }

        ArgPtr ptr0 = this.argPtrs.get(0);
        EVALUATEDOCUMENTS:
        for (; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc++) {

            int ptr0Docid = ptr0.invList.getDocid(ptr0.nextDoc);


            //  Do the other query arguments have the ptr0Docid?
            for (int j = 1; j < this.argPtrs.size(); j++) {
                ArgPtr ptrj = this.argPtrs.get(j);
                while (true) {
                    if (ptrj.nextDoc >= ptrj.invList.postings.size()) {
                        break EVALUATEDOCUMENTS;        // No more docs can match
                    } else if (ptrj.invList.getDocid(ptrj.nextDoc) > ptr0Docid) {
                        continue EVALUATEDOCUMENTS;    // The ptr0docid can't match.
                    } else if (ptrj.invList.getDocid(ptrj.nextDoc) < ptr0Docid) {
                        ptrj.nextDoc++;            // Not yet at the right doc.}
                    } else {// ptrj matches ptr0Docid
                        break;
                    }
                }
            }


            List<Integer> tmpPositions = new Vector<Integer>();
            DocPosting tmpPosting = null;

            ArgPtr curPtr = this.argPtrs.get(0);
            DocPosting curPosting = curPtr.invList.getPosting(curPtr.nextDoc);

            int curTf = 0;
            int curPos = 0;


            EVALUATEDOCUMENTS2:
            for (; curPosting.nextPos < curPosting.positions.size(); curPosting.nextPos++) {
                //curTf = 0;
                curPos = curPosting.positions.get(curPosting.nextPos);

                for (int i = 1; i < this.argPtrs.size(); i++) {
                    ArgPtr nextPtr = this.argPtrs.get(i);
                    DocPosting nextPosting = nextPtr.invList.getPosting(nextPtr.nextDoc);

                    while (true) {

                        if (nextPosting.nextPos >= nextPosting.positions.size()) {
                            break EVALUATEDOCUMENTS2;
                        } else if (nextPosting.getNextPos(nextPosting.nextPos)
                                <= curPos) {
                            nextPosting.nextPos++;
                        } else if (nextPosting.getNextPos(nextPosting.nextPos)
                                - curPos <= this.distance) {
                            curPos = nextPosting.getNextPos(nextPosting.nextPos);
                            break;
                        } else {
                            continue EVALUATEDOCUMENTS2;
                        }
                    }
                }

                tmpPositions.add(curPos);
                curTf++;

                for (int j = 1; j < this.argPtrs.size(); j++) {
                    ArgPtr p = this.argPtrs.get(j);
                    p.invList.postings.get(p.nextDoc).nextPos++;
                }

            }
            if (curTf != 0) {
                tmpPosting = new DocPosting(curPtr.invList.getDocid(curPtr.nextDoc), tmpPositions);
                tmpPosting.tf = curTf;
                result.invertedList.appendPosting(tmpPosting);
            }


        }

        result.invertedList.field = ptr0.invList.field;

        freeArgPtrs();
        return result;

    }

    /*
     *  Return a string version of this query operator.
     *  @return The string version of this query operator.
     */
    public String toString() {

        String result = new String();

        for (int i = 0; i < this.args.size(); i++)
            result += this.args.get(i).toString() + " ";

        return ("#NEAR( " + result + ")");
    }
}
