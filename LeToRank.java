/**
 * Created by laceyliu on 4/9/15.
 */
import java.util.*;
import java.io.*;

public class LeToRank {

    private RetrievalModelLeToR ltr;

    public HashMap<Integer, HashMap<String, Integer>>relMap;

    public HashMap<Integer, ArrayList<String>>trainSeq;

    public LeToRank(RetrievalModel model) throws Exception {
        this.ltr = (RetrievalModelLeToR) model;
        this.relMap = new HashMap<Integer, HashMap<String, Integer>>();
    }

    public void buildRelMap(String relPath) throws Exception {

        Scanner scan = new Scanner(new File(relPath));
        String line = "";
        this.trainSeq = new HashMap<Integer, ArrayList<String>>();
        HashMap<String, Integer> tmp = new HashMap<String, Integer>();
        ArrayList<String> seq = new ArrayList<String>();
        int prevQryId = -1;
        do{
            line = scan.nextLine();
            String[] content = line.split("\\s+");
            int qryId = Integer.parseInt(content[0]);
            if (prevQryId == -1){
                prevQryId = qryId;
            }
            String eid = content[2];
            int rel = Integer.parseInt(content[3]);

            if (prevQryId != qryId){
                this.relMap.put(prevQryId, tmp);
                this.trainSeq.put(prevQryId, seq);
                tmp = new HashMap<String, Integer>();
                seq = new ArrayList<String>();
                prevQryId = qryId;
            }
            tmp.put(eid, rel);
            seq.add(eid);

            if (!scan.hasNext()){
                this.relMap.put(prevQryId, tmp);
                this.trainSeq.put(qryId, seq);
            }

        }while(scan.hasNext());
    }

    public TreeMap<Integer, ArrayList<FeatureVector>> createTrainVects(List<String> qryIdList, List<String> qryList) throws Exception {

        TreeMap<Integer, ArrayList<FeatureVector>> vects = new TreeMap<Integer, ArrayList<FeatureVector>>();

        for (int i = 0; i<qryIdList.size(); i++){
            String[] qTerms = QryEval.tokenizeQuery(qryList.get(i));
            int qryId = Integer.parseInt(qryIdList.get(i));
            ArrayList<FeatureVector> vectorList = new ArrayList<FeatureVector>();
            vects.put(qryId, vectorList);

            for (String eid : this.trainSeq.get(qryId)){
                FeatureVector tmp = new FeatureVector(qTerms, qryId, this.relMap.get(qryId).get(eid), eid, this.ltr);
                vects.get(qryId).add(tmp);
            }
            vects.put(qryId, normalize(vects.get(qryId)));
        }

        return vects;
    }

    public ArrayList<FeatureVector> createTestVects(String qry, int qryId, QryResult ret) throws Exception {

        ArrayList<FeatureVector> vects = new ArrayList<FeatureVector>();

        ArrayList<RankingRecord> records = new ArrayList<RankingRecord>();

        for (int i = 0; i<ret.docScores.scores.size(); i++){
            int docId = ret.docScores.getDocid(i);
            double score = ret.docScores.getDocidScore(i);
            records.add(new RankingRecord("", docId, score));
        }
        Collections.sort(records);

        int size = Math.min(100, records.size());
        String[] qTerms = QryEval.tokenizeQuery(qry);

        for (int i = 0; i<size; i++){
            int docId = records.get(i).docId;
            String eid = QryEval.getExternalDocid(docId);
            FeatureVector tmp = new FeatureVector(qTerms, qryId, 0, eid, this.ltr);
            vects.add(tmp);
        }
        return vects;
    }



    public ArrayList<FeatureVector> normalize(ArrayList<FeatureVector> vectors){

        ArrayList<FeatureVector> ret = vectors;

        for (int i = 0; i<18; i++){
            if (ltr.disabledFeatures.contains(i+1)){
                continue;
            }
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            for (FeatureVector v : vectors){
                double tmp = v.features.get(i);
                if (tmp == -1){
                    continue;
                }else {
                    min = Math.min(min, tmp);
                    max = Math.max(max,tmp);
                }
            }
            for (FeatureVector v : ret){
                double pre = v.features.get(i);
                if (max == min || pre == -1){
                    v.features.set(i, 0.0);
                }else{
                    v.features.set(i, (pre - min)/(max - min));
                }
            }
        }
        return ret;
    }

    public void writeVectorToFile(List<String> qryIdList, TreeMap<Integer, ArrayList<FeatureVector>> vect, String path) throws IOException {
        BufferedWriter vectWriter = new BufferedWriter(new FileWriter(path));

        for (String qid : qryIdList){

            ArrayList<FeatureVector> vects = vect.get(Integer.parseInt(qid));

            for (FeatureVector fv : vects){
                String ret = "";
                ret += fv.relScore + "\t";
                ret += "qid:"+fv.qryId + "\t";
                for (int i = 0; i<fv.features.size(); i++){
                    if (this.ltr.disabledFeatures != null && this.ltr.disabledFeatures.contains(i+1)){
                        continue;
                    }else{
                        ret += (i+1)+":"+fv.features.get(i) + "\t";
                    }
                }
                ret += "# " + fv.eid;
                vectWriter.write(ret + "\n");
                //System.out.println(ret);
            }
        }
        vectWriter.close();
    }

    public void testModel(String execPath, String testPath, String modelPath, String predictionPath) throws Exception {

        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { execPath, testPath, modelPath,
                        predictionPath });

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM test crashed.");
        }
    }

    public void trainModel(String execPath, String c, String trainset, String modelOutputFile) throws Exception {

        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { execPath, "-c", String.valueOf(c), trainset,
                        modelOutputFile });

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM train crashed.");
        }
    }


}
