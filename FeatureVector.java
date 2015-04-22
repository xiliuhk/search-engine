/**
 * Created by laceyliu on 4/9/15.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;


public class FeatureVector {

    public static HashMap<String, Double>prMap;

    public int qryId;
    public int relScore;
    public String eid;

    public List<Double> features;


    public FeatureVector(String[] queryTerms, int qryId, int relScore, String eid, RetrievalModelLeToR model) throws Exception {
        this.qryId = qryId;
        this.relScore = relScore;
        this.eid = eid;
        this.features = createFeatures(queryTerms, model, eid);
    }

    public ArrayList<Double> createFeatures(String[] queryTerms, RetrievalModelLeToR model, String eid) throws
            Exception {

        ArrayList<Double> features = new ArrayList<Double>();

        int docId = QryEval.getInternalDocid(eid);

        //f1: spam score
        double spamscore = getSpamScore(docId);
        features.add(spamscore);

        //f2: url depth
        double depth = getUrlLen(docId);
        features.add(depth);

        //f3: wiki score
        features.add(getWikiScore(docId));

        //f4: pageRank
        if (prMap.containsKey(eid)){
            features.add(prMap.get(eid)*1.0);
        }else{
            features.add(-1.0);
        }

        TermVector vector = null;
        try{
            vector = new TermVector(docId, "body");
        }catch (Exception e){
            //System.err.println(e.toString());
        }

        //f5:BM25 body score
        features.add(computeFeatureBM25(queryTerms, docId, "body", vector, model));

        //f6:Indri body score
        features.add(computeFeatureIndri(queryTerms, docId, "body", vector, model));

        //f7: term-body overlap
        features.add(computeOverlap(queryTerms, vector));

        vector = null;
        try{
            vector = new TermVector(docId, "title");
        }catch (Exception e){
            //System.err.println(e.toString());
        }

        //f8:BM25 title score
        features.add(computeFeatureBM25(queryTerms, docId, "title", vector, model));

        //f9:Indri title score
        features.add(computeFeatureIndri(queryTerms, docId, "title", vector, model));

        //f710 term-title overlap
        features.add(computeOverlap(queryTerms, vector));

        vector = null;
        try{
            vector = new TermVector(docId, "url");
        }catch (Exception e){
            //System.err.println(e.toString());
        }

        //f11:BM25 url score
        features.add(computeFeatureBM25(queryTerms, docId, "url", vector, model));

        //f12:Indri url score
        features.add(computeFeatureIndri(queryTerms, docId, "url", vector, model));

        //f13 term-url overlap
        features.add(computeOverlap(queryTerms, vector));

        vector = null;
        try{
            vector = new TermVector(docId, "inlink");
        }catch (Exception e){
            //System.err.println(e.toString());
        }

        //f14:BM25 inlink score
        features.add(computeFeatureBM25(queryTerms, docId, "inlink", vector, model));

        //f15:Indri inlink score
        features.add(computeFeatureIndri(queryTerms, docId, "inlink", vector, model));

        //f16: term-inlink overlap
        features.add(computeOverlap(queryTerms, vector));

        vector = null;
        try{
            vector = new TermVector(docId, "body");
        }catch (Exception e){
            //System.err.println(e.toString());
        }
        //f17:stream length
        features.add(getDocLength(queryTerms, vector));

        //f18:sum of term frequencies in body
        features.add(getTfSum(queryTerms, vector));

        return features;

    }

    public double getSpamScore(int docId) throws IOException {
        if (docId == -1){
            return -1;
        }
        Document d = QryEval.READER.document(docId);
        return Double.parseDouble(d.get("score"));
    }

    public double getUrlLen(int docId) throws IOException {
        if (docId == -1){
            return -1;
        }
        Document d = QryEval.READER.document(docId);
        String rawUrl = d.get("rawUrl");
        int depth = 0;
        for (int i = 0; i<rawUrl.length(); i++){
            if (rawUrl.charAt(i) == '/'){
                depth++;
            }
        }
        return depth*1.0;

    }

    public double getWikiScore(int docId) throws IOException {
        if (docId == -1) {
            return -1;
        }
        Document d = QryEval.READER.document(docId);
        String rawUrl = d.get("rawUrl");
        if (rawUrl.contains("wikipedia.org")) {
            return 1;
        }else{
            return 0;
        }
    }


    public double getDocLength(String[] qTerms, TermVector vector){
        if (vector != null){
            return vector.stemsLength()*1.0;
        }else{
            return -1;
        }
    }

    public double getTfSum(String[] qTerms, TermVector vector){
        int cnt = 0;

        if (vector == null){
            return -1;
        }

        for (String term : qTerms){
            for (int i = 1; i<vector.stems.length; i++){
                if (vector.stems[i].equals(term)){
                    cnt += vector.stemFreq(i);
                }
            }
        }
        return 1.0*cnt;
    }

    public double computeOverlap(String[]qTerms, TermVector vector){
        int overlap = 0;

        if (vector == null){
            return -1.0;
        }

        for(String term: qTerms){
            for (int i = 1; i<vector.stems.length; i++){
                if (vector.stems[i].equals(term)){
                    overlap+=1;
                    break;
                }
            }
        }
        return overlap*1.0/qTerms.length;
    }

    public double computeFeatureBM25(String[]qTerms, int docid, String field, TermVector vector, RetrievalModelLeToR
            m) {

        if (vector == null){
            return -1;
        }

        double score = 0.0;
        double avg_doclen = 0;
        try {
            avg_doclen = 1.0* QryEval.READER.getSumTotalTermFreq(field)/ QryEval.READER.getDocCount(field);

            double N = 1.0* QryEval.READER.numDocs();
            double docLen = 1.0*(new DocLengthStore(QryEval.READER))
    .getDocLength(field, docid);
            int overlap = 0;
            for(String term: qTerms){
                for (int i = 1; i<vector.stems.length; i++){
                    if (vector.stems[i].equals(term)){
                        double tf = vector.stemFreq(i);
                        double df = QryEval.READER.docFreq(new Term(field, new BytesRef(term)));
                        double rsj = Math.max(Math.log((N - df + 0.5) / (df + 0.5)),0);
                        double tfW = tf/(tf + m.k_1*((1-m.b)+m.b*docLen/avg_doclen));
                        double userW = (m.k_3+1)*1.0/(m.k_3+1.0);
                        score += rsj*tfW*userW;
                        overlap += 1;
                        break;
                    }
                }
            }
            if (overlap == 0){
                return 0.0;
            }
    } catch (Exception e) {
        return -1.0;
    }
        return score;
    }

    public double computeFeatureIndri(String[]qTerms, int docid, String field, TermVector vector, RetrievalModelLeToR
            m){

        if (vector == null){
            return -1;
        }

        double score = 1.0;

        try {
            double docLen = 1.0*(new DocLengthStore(QryEval.READER)).getDocLength(field, docid);
            double cLen = QryEval.READER.getSumTotalTermFreq(field);
            int overlap = 0;
            for(String term: qTerms){
                double p_mle = QryEval.READER.totalTermFreq(new Term(field, new BytesRef(term)))/cLen;

                boolean match = false;
                for (int i = 1; i<vector.stems.length; i++){
                    if (vector.stems[i].equals(term)){
                        match = true;
                        double tf = vector.stemFreq(i);
                        double termScore = (1.0-m.lambda)*(tf + m.mu*p_mle)/(docLen + m.mu) + m.lambda*p_mle;
                        score *= Math.pow(termScore, 1.0/qTerms.length);
                        overlap += 1;
                        break;
                    }
                }
                if (match == false){
                    double termScore = (1.0 - m.lambda)*m.mu*p_mle/(docLen + m.mu) + m.lambda*p_mle;
                    score *= Math.pow(termScore, 1.0/qTerms.length);
                }
            }
            if (overlap == 0){
                return 0.0;
            }
        } catch (IOException e) {
            return -1.0;
        }

        return score;
    }

    static HashMap<String, Double> constructPRMap(String pgrPath) throws Exception {
        HashMap<String, Double> prMap = new HashMap<String, Double>();
        BufferedReader scan = new BufferedReader(new FileReader(pgrPath));
        String line = null;
        while((line = scan.readLine())!= null){
            String[] tmp = line.split("\t");
            String eid = tmp[0];
            double score = Double.parseDouble(tmp[1]);
            prMap.put(eid, score);
        }
        return prMap;
    }

}
