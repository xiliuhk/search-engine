/**
 *  All query operators are subclasses of the Qryop class.  Most of
 *  this class is abstract, because different types of query operators
 *  (inverted list, score list) have different subclasses, and each
 *  query operator has its own subclass.  This class defines the
 *  common interface to query operators, and is a place to store data
 *  structures and methods that are common to all query operators.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class Qryop {

    //  ArgPtrs are used by query operators for query evaluation

    protected ArrayList<Qryop> args = new ArrayList<Qryop>();

    ;

    //  Initially the query operator starts with no arguments and no
    //  ArgPtrs.
    protected List<ArgPtr> argPtrs = new ArrayList<ArgPtr>();

    /**
     * Appends an argument to the list of query operator arguments.  This
     * simplifies the design of some query parsing architectures.
     *
     * @param {q} q The query argument (query operator) to append.
     * @return void
     * @throws IOException
     */
    public abstract void add(Qryop q) throws IOException;

    /**
     * Use the specified retrieval model to evaluate the query arguments.
     * Define and return ArgPtrs that the query operator can use.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The argPtrs.
     * @throws IOException
     */
    public abstract void allocArgPtrs(RetrievalModel r) throws IOException;

    /**
     * Evaluates the query operator, including any child operators and
     * returns the result.
     *
     * @param r A retrieval model that controls how the operator behaves.
     * @return The result of evaluating the query.
     * @throws IOException
     */
    public abstract QryResult evaluate(RetrievalModel r) throws IOException;

    /**
     * Free this operator's ArgPtrs.
     *
     * @return void
     */
    public void freeArgPtrs() {
        this.argPtrs = new ArrayList<ArgPtr>();
    }

    /**
     * Removes an argument from the list of query operator arguments.
     * This simplifies the design of some query parsing architectures.
     *
     * @param i The index of the query operator to remove.
     * @return void
     */
    public void remove(int i) {
        this.args.remove(i);
    }

    /*
     *  Return a string version of this query operator.
     *  @return The string version of this query operator.
     */
    public abstract String toString();

    ;

    protected class ArgPtr {
        protected ScoreList scoreList;    // A qry arg's score list (if any)
        protected InvList invList;        // A qry arg's inverted list (if any)
        protected int nextDoc;        // The next document to examine
    }

}
