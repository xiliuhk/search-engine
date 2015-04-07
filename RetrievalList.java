/**
 * Created by laceyliu on 1/28/15.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RetrievalList {

    public List<RetrievalEntry> retrievalList;

    public RetrievalList() {
        this.retrievalList = new ArrayList<RetrievalEntry>();
    }

    public void add(String doc, double score) {
        this.retrievalList.add(new RetrievalEntry(doc, score));
    }

    public void sort() {
        Collections.sort(this.retrievalList);
    }

    public int size() {
        return retrievalList.size();
    }

    public String getEid(int n) {
        return retrievalList.get(n).doc;
    }

    public double getScore(int n) {
        return retrievalList.get(n).score;
    }

    public String getKey(int n) {
        return retrievalList.get(n).doc;
    }

    protected class RetrievalEntry implements Comparable<RetrievalEntry> {
        public String doc;
        public double score;

        public RetrievalEntry(String doc, double score) {
            this.doc = doc;
            this.score = score;
        }

        @Override
        public int compareTo(RetrievalEntry o) {
            if (this.score > o.score) {
                return -1;
            } else if (this.score < o.score) {
                return 1;
            } else if (this.doc.compareTo(o.doc) < 0) {
                return -1;
            } else if (this.doc.compareTo(o.doc) >= 0) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
