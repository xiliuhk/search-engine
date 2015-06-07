/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreList {

    //  A little utilty class to create a <docid, score> object.

    public List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();

    /**
     * Append a document score to a score list.
     *
     * @param docid An internal document id.
     * @param score The document's score.
     * @return void
     */
    public void add(int docid, double score) {
        scores.add(new ScoreListEntry(docid, score));
    }

    /**
     * Get the n'th document id.
     *
     * @param n The index of the requested document.
     * @return The internal document id.
     */
    public int getDocid(int n) {
        return this.scores.get(n).docid;
    }

    /**
     * Get the score of the n'th document.
     *
     * @param n The index of the requested document score.
     * @return The document's score.
     */
    public double getDocidScore(int n) {
        return this.scores.get(n).score;
    }

    public void sort() {
        Collections.sort(scores);
    }

    public int size() {
        return this.scores.size();
    }

    protected class ScoreListEntry implements Comparable<ScoreListEntry> {
        private int docid;
        private double score;

        private ScoreListEntry(int docid, double score) {
            this.docid = docid;
            this.score = score;
        }

        @Override
        public int compareTo(ScoreListEntry o) {
            if (this.score > o.score) {
                return -1;
            } else if (this.score < o.score) {
                return 1;
            } else {
                return 0;
            }
        }

    }
}
