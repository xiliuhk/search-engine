/**
 * Created by laceyliu on 3/21/15.
 */
public class RankingRecord implements Comparable<RankingRecord> {
    public String eid;
    public int docId;
    public double score;

    public RankingRecord(String eid, int docId, double score) {
        this.eid = eid;
        this.docId = docId;
        this.score = score;
    }

    public int compareTo(RankingRecord o) {
        if (this.score < o.score) {
            return 1;
        } else if (this.score > o.score) {
            return -1;
        } else {
            return this.eid.compareTo(o.eid);
        }
    }

}
