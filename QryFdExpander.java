import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class QryFdExpander {
    int fbTerms;
    ArrayList<RankingRecord> references;
    String qid;
    double fbMu;
    ArrayList<TermVector> forwardIndex;

    HashMap<String, Double> termMap;

    public QryFdExpander(String qid, int fbTerms, ArrayList<RankingRecord> references, double fbMu) {
        this.qid = qid;
        this.fbTerms = fbTerms;
        this.references = references;
        this.fbMu = fbMu;
        this.termMap = new HashMap<String, Double>();
        this.forwardIndex = new ArrayList<TermVector>();
    }

    public String pseudoFeedBack() throws IOException {
        String ret = "";
        //initalize forward indices
        for (int i = 0; i < references.size(); i++) {
            //term vector
            RankingRecord record = references.get(i);
            TermVector v = new TermVector(record.docId, "body");
            forwardIndex.add(v);
        }
        for (TermVector v : forwardIndex) {
            for (String stem : v.stems) {
                if (stem != null && !termMap.containsKey(stem)) {
                    termMap.put(stem, 0.0);
                }
            }
        }

        //calculate score for each term by forward indexing
        DocLengthStore s = new DocLengthStore(QryEval.READER);
        double collectionSize = QryEval.READER.getSumTotalTermFreq("body");
        for (int i = 0; i < references.size(); i++) {
            // collection vocabulary
            HashMap<String, Integer> dictionary = new HashMap<String, Integer>();
            TermVector v = forwardIndex.get(i);
            for (int j = 0; j < v.stemsLength(); j++) {
                String stem = v.stemString(j);
                if (stem != null && !dictionary.containsKey(stem)) {
                    dictionary.put(stem, v.stemFreq(j));
                }
            }

            double docLen = s.getDocLength("body", references.get(i).docId);
            double docScore = references.get(i).score;

            //update term weights
            for (String term : termMap.keySet()) {
                double score = termMap.get(term);
                double ctf = QryEval.READER.totalTermFreq(new Term("body", new BytesRef(term)));
                double pMLE = ctf / collectionSize;
                double p;
                if (dictionary.containsKey(term)) {
                    p = (dictionary.get(term) + fbMu * pMLE) / (docLen + fbMu);
                } else {
                    p = fbMu * pMLE / (docLen + fbMu);
                }
                score += p * docScore * Math.log(collectionSize / ctf);
                termMap.put(term, score);
            }
        }

        //rank terms by score
        RetrievalList termRanking = new RetrievalList();
        for (String term : termMap.keySet()) {
            termRanking.add(term, termMap.get(term));
        }
        termRanking.sort();

        // generate expanded qry
        ret = "#WAND(";
        int termCnt = 0;
        for (int i = 0; i < termRanking.size(); i++) {
            String term = termRanking.getKey(i);
            if (term.contains(".") || term.contains(",")) {
                continue;
            } else {
                ret += termRanking.getScore(i) + " " + termRanking.getKey(i) + " ";
                termCnt++;
                if (termCnt >= fbTerms) {
                    break;
                }
            }
        }
        ret += ")";
        return ret;
    }

}
