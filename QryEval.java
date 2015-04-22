import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.*;

/**
 * Created by laceyliu on 4/7/15.
 */
public class QryEval {

    public static IndexReader READER;

    public static EnglishAnalyzerConfigurable analyzer =
            new EnglishAnalyzerConfigurable(Version.LUCENE_43);
    static {
        analyzer.setLowercase(true);
        analyzer.setStopwordRemoval(true);
        analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
    }

    public static List<String> queryList = new ArrayList<String>();
    public static List<String> queryIDList = new ArrayList<String>();
    public static BufferedWriter writer;
    static String usage = "Usage:  java " + System.getProperty("sun.java.command")
            + " paramFile\n";

    public static void main(String[] args) throws Exception{
        // must supply parameter file
        if (args.length < 1) {
            System.err.println(usage);
            System.exit(1);
        }

        // read in the parameter file; one parameter per line in format of key=value
        Map<String, String> params = new HashMap<String, String>();
        Scanner scan = new Scanner(new File(args[0]));
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            params.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());
        scan.close();

        // parameters required for this example to run
        if (!params.containsKey("indexPath")) {
            System.err.println("Error: Parameters were missing.");
            System.exit(1);
        }

        // open the index
        READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

        if (READER == null) {
            System.err.println(usage);
            System.exit(1);
        }

        //DocLengthStore s = new DocLengthStore(READER);
        RetrievalModelLeToR model;
        String modelName = params.get("retrievalAlgorithm");

        if (modelName.equals("letor")){
            model = new RetrievalModelLeToR();
            model.setParameter("trainingQueryFile", params.get("letor:trainingQueryFile"));
            model.setParameter("trainingRelsFile", params.get("letor:trainingQrelsFile"));
            model.setParameter("trainingFeatureVectorsFile", params.get("letor:trainingFeatureVectorsFile"));
            model.setParameter("testingFeatureVectorsFile", params.get("letor:testingFeatureVectorsFile"));
            model.setParameter("testingDocumentScores", params.get("letor:testingDocumentScores"));
            model.setParameter("pageRankFile", params.get("letor:pageRankFile"));
            if (params.containsKey("letor:featureDisable")){
                model.setParameter("featureDisable", params.get("letor:featureDisable"));
            }else{
                model.setParameter("featureDisable", "");
            }
            model.setParameter("svmRankLearnPath", params.get("letor:svmRankLearnPath"));
            model.setParameter("svmRankClassifyPath", params.get("letor:svmRankClassifyPath"));
            model.setParameter("svmRankParamC", params.get("letor:svmRankParamC"));
            model.setParameter("svmRankModelFile", params.get("letor:svmRankModelFile"));
            model.setParameter("k_1", Double.parseDouble(params.get("BM25:k_1")));
            model.setParameter("k_3", Double.parseDouble(params.get("BM25:k_3")));
            model.setParameter("b", Double.parseDouble(params.get("BM25:b")));
            model.setParameter("mu", Double.parseDouble(params.get("Indri:mu")));
            model.setParameter("lambda", Double.parseDouble(params.get("Indri:lambda")));

        }else{
            return;
        }

        // read in training queries into a list
        scan = new Scanner(new File(model.trainQryPath));
        do {
            line = scan.nextLine();
            String[] queryPair = line.split(":");
            queryIDList.add(queryPair[0].trim());
            queryList.add(queryPair[1].trim());
        } while (scan.hasNext());
        scan.close();

        //construct page rank map for feature vectors
        FeatureVector.prMap = FeatureVector.constructPRMap(model.pageRankPath);

        //LeToRank Model
        LeToRank rankModel = new LeToRank(model);
        //build train vectors
        rankModel.buildRelMap(model.trainRelPath);
        TreeMap<Integer, ArrayList<FeatureVector>>  trainVects = rankModel.createTrainVects(queryIDList, queryList);
        rankModel.writeVectorToFile(queryIDList, trainVects, model.trainVectPath);
        //train SVM model
        rankModel.trainModel(model.svmLearnPath, model.svmC, model.trainVectPath, model.svmModelPath);

        //reset qry list
        queryIDList = new ArrayList<String>();
        queryList = new ArrayList<String>();

        // read test queries into a list
        scan = new Scanner(new File(params.get("queryFilePath")));
        do {
            line = scan.nextLine();
            String[] queryPair = line.split(":");
            queryIDList.add(queryPair[0].trim());
            queryList.add(queryPair[1].trim());
        } while (scan.hasNext());
        scan.close();

        //build test vectors
        TreeMap<Integer, ArrayList<FeatureVector>>  testVects = new TreeMap<Integer, ArrayList<FeatureVector>>();
        //generate initial ranks
        Qryop qTree;
        QryResult ret=null;
        RetrievalModel bm25_initial = new RetrievalModelBM25();
        bm25_initial.setParameter("k_1", model.k_1);
        bm25_initial.setParameter("k_3", model.k_3);
        bm25_initial.setParameter("b", model.b);
        for (int i = 0; i<queryList.size(); i++) {
            String query = queryList.get(i);
            int qryId = Integer.parseInt(queryIDList.get(i));
            qTree = QryEval.parseQuery(bm25_initial, query);
            ret = qTree.evaluate(bm25_initial);
            ArrayList<FeatureVector> records = rankModel.createTestVects(query, qryId, ret);
            testVects.put(qryId, records);
        }

        //write test vectors to file
        rankModel.writeVectorToFile(queryIDList, testVects, model.testVectPath);

        //generate new score for each docs
        rankModel.testModel( model.svmTestPath, model.testVectPath, model.svmModelPath, model.svmScorePath);


        //rerank
        writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
        printRankedRet(testVects, model.svmScorePath, queryIDList);
        writer.close();

        return;
    }

    static Qryop parseQuery(RetrievalModel r, String qString) throws IOException {
        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).
        Qryop currentOp = null;
        Stack<Qryop> stack = new Stack<Qryop>();

        // Add a default query operator to an unstructured query. This
        // is a tiny bit easier if unnecessary whitespace is removed.

        qString = qString.trim();
        qString = "#sum (" + qString + " )";

        // Tokenize the query.
        StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
        String token = null;

        while (tokens.hasMoreTokens()) {

            token = tokens.nextToken();

            if (token.matches("[ ,(\t\n\r]")) {
                // Ignore most delimiters.
                continue;

            } else if (token.equalsIgnoreCase("#sum")) {
                currentOp = new QryopSlSum();
                stack.push(currentOp);
            } else if (token.startsWith(")")) {
                stack.pop();
                if (stack.empty())
                    break;
                Qryop arg = currentOp;
                currentOp = stack.peek();
                currentOp.add(arg);
            } else {
                String[] terms = tokenizeQuery(token);
                if (terms.length == 0) {
                        continue;
                } else {
                    currentOp.add(new QryopIlTerm(terms[0]));
                }
            }
        }

        // A broken structured query can leave unprocessed tokens on the
        // stack, so check for that.
        if (tokens.hasMoreTokens()) {
            System.err.println("Error:  Query syntax is incorrect.  " + qString);
            return null;
        }

        return currentOp;

    }

    static void printRankedRet(TreeMap<Integer, ArrayList<FeatureVector>> ret, String prediction, List<String>qryIdList) throws IOException {

        Scanner scan = new Scanner(new File(prediction));

        for (String qryId : qryIdList){
            int qid = Integer.parseInt(qryId);
            RetrievalList curRet = new RetrievalList();
            ArrayList<FeatureVector> curFVList = ret.get(qid);
            int i = 0;
            while (scan.hasNext() && i < curFVList.size()){
                String line = scan.nextLine();
                double score = Double.parseDouble(line);
                String eid = curFVList.get(i).eid;
                curRet.add(eid, score);
                i++;
            }
            if (curFVList.size()-1>i){
                System.err.println("docs missing");
                return;
            }else{
                curRet.sort();
            }

            for (int j = 1; j<= curRet.size(); j++){
                writer.write(qryId + "\tQ0\t" + curRet.getEid(j-1) + "\t" + j + "\t" + curRet.getScore(j-1) + "\trun-1\n");
            }

        }

    }

    static void printResults(String queryName, QryResult result) throws IOException {

        try {

            //BufferedWriter writer = new BufferedWriter(new FileWriter(new File(ouputPath)));

            int queryIndex = queryList.indexOf(queryName);
            String queryID = queryIDList.get(queryIndex);

            ScoreList scoreList = result.docScores;
            RetrievalList retrievalList = new RetrievalList();
            for (int i = 0; i < scoreList.scores.size(); i++) {
                int curId = scoreList.getDocid(i);
                String curDoc = getExternalDocid(curId);
                Double curScore = scoreList.getDocidScore(i);
                retrievalList.add(curDoc, curScore);
            }
            retrievalList.sort();

            if (retrievalList.size() < 1) {
                writer.append(queryID + "\tQ0\t");
                writer.append("dummy" + "\t"
                        + "1" + "\t" + "0" + "\t" + "run-1");
                writer.append("\n");
            } else {

                int size = Math.min(retrievalList.size(), 100);

                for (int i = 0; i < size; i++) {
                    writer.append(queryID + "\tQ0\t");
                    writer.append(retrievalList.getEid(i) + "\t"
                            + (i + 1) + "\t" + retrievalList.getScore(i) + "\t" + "run-1");
                    writer.append("\n");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static int getInternalDocid(String externalId) throws Exception {
        Query q = new TermQuery(new Term("externalId", externalId));

        IndexSearcher searcher = new IndexSearcher(QryEval.READER);
        TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        if (hits.length < 1) {
            return -1;
            //throw new Exception("External id not found.");
        } else {
            return hits[0].doc;
        }
    }

    static String getExternalDocid(int iid) throws IOException {
        Document d = QryEval.READER.document(iid);
        String eid = d.get("externalId");
        return eid;
    }
    static String[] tokenizeQuery(String query) throws IOException {

        Analyzer.TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
        TokenStream tokenStream = comp.getTokenStream();

        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();

        List<String> tokens = new ArrayList<String>();
        while (tokenStream.incrementToken()) {
            String term = charTermAttribute.toString();
            tokens.add(term);
        }
        return tokens.toArray(new String[tokens.size()]);
    }
}
