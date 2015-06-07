import java.util.List;
import java.util.Vector;

/**
 * Created by laceyliu on 2/2/15.
 */
public class DocPosting implements Comparable<DocPosting> {

    public int docid = 0;
    public int tf = 0;
    public Vector<Integer> positions = new Vector<Integer>();

    public int nextPos = 0;

    public DocPosting(int d, int... locations) {
        this.docid = d;
        this.tf = locations.length;
        for (int i = 0; i < locations.length; i++)
            this.positions.add(locations[i]);

    }

    public DocPosting(int d, List<Integer> locations) {
        this.docid = d;
        this.tf = locations.size();
        for (int i = 0; i < locations.size(); i++)
            this.positions.add(locations.get(i));

    }

    public int getNextPos(int n) {
        return this.positions.get(n);
    }

    @Override
    public int compareTo(DocPosting o) {
        if (this.docid > o.docid) {
            return -1;
        } else if (this.docid < o.docid) {
            return 1;
        } else if (this.tf < o.tf) {
            return 1;
        } else if (this.tf > o.tf) {
            return -1;
        } else {
            return 0;
        }
    }


}