/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
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
import java.util.regex.Pattern;

public class QryEval {

    public static IndexReader READER;

    //  The index file reader is accessible via a global variable. This
    //  isn't great programming style, but the alternative is for every
    //  query operator to store or pass this value, which creates its
    //  own headaches.
    //  Create and configure an English analyzer that will be used for
    //  query parsing.
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
    public static BufferedWriter qryWriter_expand;
    static String usage = "Usage:  java " + System.getProperty("sun.java.command")
            + " paramFile\n\n";

    /**
     * @param args The only argument is the path to the parameter file.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        //timer starts
        long startTime = System.currentTimeMillis();

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

        DocLengthStore s = new DocLengthStore(READER);

        //RetrievalModel model = new RetrievalModelUnrankedBoolean();
        String modelName = (String) params.get("retrievalAlgorithm");
        RetrievalModel model;

        if (modelName.equals("UnrankedBoolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelName.equals("RankedBoolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelName.equals("BM25") && params.containsKey("BM25:k_1") && params.containsKey("BM25:k_3") && params.containsKey("BM25:b")) {
            model = new RetrievalModelBM25();
            model.setParameter("k_1", Double.parseDouble(params.get("BM25:k_1")));
            model.setParameter("k_3", Double.parseDouble(params.get("BM25:k_3")));
            model.setParameter("b", Double.parseDouble(params.get("BM25:b")));
        } else if (modelName.equals("Indri") && params.containsKey("Indri:mu") && params.containsKey("Indri:lambda")) {
            model = new RetrievalModelIndri();
            model.setParameter("mu", Double.parseDouble(params.get("Indri:mu")));
            model.setParameter("lambda", Double.parseDouble(params.get("Indri:lambda")));
        } else {
            model = new RetrievalModelUnrankedBoolean();
        }

        String output = params.get("trecEvalOutputPath");
        writer = new BufferedWriter(new FileWriter(new File(output)));

        // read in the queries into a list
        scan = new Scanner(new File(params.get("queryFilePath")));
        do {
            line = scan.nextLine();
            String[] queryPair = line.split(":");
            queryIDList.add(queryPair[0].trim());
            queryList.add(queryPair[1].trim());
        } while (scan.hasNext());
        scan.close();

        //  Using the example query parser.  Notice that this does no
        //  lexical processing of query terms.  Add that to the query
        //  parser.
        Qryop qTree;
        QryResult ret;

        if (params.containsKey("fb") && params.get("fb").equals("true")) {

            int fbDocs = Integer.parseInt(params.get("fbDocs"));
            int fbTerms = Integer.parseInt(params.get("fbTerms"));
            double fbOriginW = Double.parseDouble(params.get("fbOrigWeight"));
            double fbMu = Double.parseDouble(params.get("fbMu"));

            String expandQryPath = params.get("fbExpansionQueryFile");
            String initRankPath;

            //initialize writers
            try {
                qryWriter_expand = new BufferedWriter(new FileWriter(new File(expandQryPath)));
                //qryWriter_combined = new BufferedWriter(new FileWriter(new File(expandQryPath)));
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            //BufferedWriter qryWriter_combined;

            HashMap<String, ArrayList<RankingRecord>> referenceMap = new HashMap<String, ArrayList<RankingRecord>>();
            ArrayList<RankingRecord> references;

            if (!params.containsKey("fbInitialRankingFile")) {
                //generate initial ranking
                for (String query : queryList) {
                    String queryId = queryIDList.get(queryList.indexOf(query));
                    qTree = parseQuery(model, query);
                    ret = qTree.evaluate(model);
                    ArrayList<RankingRecord> refTemp = new ArrayList<RankingRecord>();
                    references = new ArrayList<RankingRecord>();
                    for (int i = 0; i < ret.docScores.scores.size(); i++) {
                        int curId = ret.docScores.getDocid(i);
                        String curDoc = getExternalDocid(curId);
                        Double curDocScore = ret.docScores.getDocidScore(i);
                        RankingRecord tmp = new RankingRecord(curDoc, curId, curDocScore);
                        refTemp.add(tmp);
                    }
                    Collections.sort(refTemp);
                    references = new ArrayList<RankingRecord>(refTemp.subList(0, fbDocs));
                    referenceMap.put(queryId, references);
                }
            } else {
                initRankPath = params.get("fbInitialRankingFile");
                //read initial ranking
                try {
                    scan = new Scanner(new File(initRankPath));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                for (String query : queryList) {
                    int cnt = 0;
                    String queryId = queryIDList.get(queryList.indexOf(query));
                    references = new ArrayList<RankingRecord>();
                    do {
                        line = scan.nextLine();
                        String[] rankInfo = line.split("\\s+");
                        if (queryId.equals(rankInfo[0])) {
                            int curDocId = getInternalDocid(rankInfo[2]);
                            RankingRecord tmpR = new RankingRecord(rankInfo[2], curDocId, Double.parseDouble(rankInfo[4]));
                            references.add(tmpR);
                            cnt++;
                        } else {
                            if (Integer.parseInt(rankInfo[0]) < Integer.parseInt(queryId)) {
                                continue;
                            } else {
                                break;
                            }
                        }
                    } while (scan.hasNext() && cnt < fbDocs);
                    Collections.sort(references);
                    referenceMap.put(queryId, references);
                }

            }

            //qry expansion and combination
            for (String query : queryList) {

                String queryId = queryIDList.get(queryList.indexOf(query));
                //fb qry expansion
                QryFdExpander qryExpander = new QryFdExpander(queryId, fbTerms, referenceMap.get(queryId), fbMu);
                String expandedQry = qryExpander.pseudoFeedBack();
                qryWriter_expand.append(queryId + ":" + expandedQry + "\n");
                qryWriter_expand.flush();
                //combine expanded qry and original qry
                String combinedQry = "#WAND ( " + fbOriginW + " #AND ( " + query + ") " + (1 - fbOriginW) + " " + expandedQry + ") ";
                //qryWriter_combined.write(queryId + ":" + combinedQry + "\n");

                //execute new qry
                qTree = parseQuery(model, combinedQry);
                ret = qTree.evaluate(model);
                printResults(query, ret);
            }

            //qryWriter_combined.close();

        } else {
            //no query expansion
            for (String query : queryList) {
                qTree = parseQuery(model, query);
                ret = qTree.evaluate(model);
                printResults(query, ret);
            }
        }

        // Later HW assignments will use more RAM, so you want to be aware
        // of how much memory your program uses.
        //printMemoryUsage(true);

        if (qryWriter_expand != null) {
            qryWriter_expand.close();
        }
        writer.close();
        //timer stops
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        System.out.println((totalTime / 1000) + " seconds");
    }

    /**
     * Write an error message and exit.  This can be done in other
     * ways, but I wanted something that takes just one statement so
     * that it is easy to insert checks without cluttering the code.
     *
     * @param message The error message to write before exiting.
     * @return void
     */
    static void fatalError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Get the external document id for a document specified by an
     * internal document id. If the internal id doesn't exists, returns null.
     *
     * @param iid The internal document id of the document.
     * @throws IOException
     */
    static String getExternalDocid(int iid) throws IOException {
        Document d = QryEval.READER.document(iid);
        String eid = d.get("externalId");
        return eid;
    }

    /**
     * Finds the internal document id for a document specified by its
     * external id, e.g. clueweb09-enwp00-88-09710.  If no such
     * document exists, it throws an exception.
     *
     * @param externalId The external document id of a document.s
     * @return An internal doc id suitable for finding document vectors etc.
     * @throws Exception
     */
    static int getInternalDocid(String externalId) throws Exception {
        Query q = new TermQuery(new Term("externalId", externalId));

        IndexSearcher searcher = new IndexSearcher(QryEval.READER);
        TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        if (hits.length < 1) {
            throw new Exception("External id not found.");
        } else {
            return hits[0].doc;
        }
    }

    /**
     * parseQuery converts a query string into a query tree.
     *
     * @param qString A string containing a query.
     *                A query tree
     * @throws IOException
     */
    static Qryop parseQuery(RetrievalModel r, String qString) throws IOException {
        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).
        Qryop currentOp = null;
        Stack<Qryop> stack = new Stack<Qryop>();

        // Add a default query operator to an unstructured query. This
        // is a tiny bit easier if unnecessary whitespace is removed.

        qString = qString.trim();
        if (r instanceof RetrievalModelIndri) {
            qString = "#and ( " + qString + " )";
        } else if (r instanceof RetrievalModelBM25) {
            qString = "#sum (" + qString + " )";
        } else {
            qString = "#or ( " + qString + " )";
        }

        // Tokenize the query.
        StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
        String token = null;
        Pattern nearPattern = Pattern.compile("(#NEAR/)(\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern windowPattern = Pattern.compile("(#WINDOW/)(\\d+)", Pattern.CASE_INSENSITIVE);

        boolean isWeight = true;

        while (tokens.hasMoreTokens()) {

            token = tokens.nextToken();

            if (token.matches("[ ,(\t\n\r]")) {
                // Ignore most delimiters.
                continue;

            } else if (token.equalsIgnoreCase("#and")) {
                currentOp = new QryopSlAnd();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#syn")) {
                currentOp = new QryopIlSyn();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#or")) {
                currentOp = new QryopSlOr();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#sum")) {
                currentOp = new QryopSlSum();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#wsum")) {
                currentOp = new QryopSlWSum();
                stack.push(currentOp);
            } else if (token.equalsIgnoreCase("#wand")) {
                currentOp = new QryopSlWAnd();
                stack.push(currentOp);
            } else if (nearPattern.matcher(token).matches()) {
                int dis = Integer.parseInt(token.split("/")[1]);
                currentOp = new QryopIlNear(dis);
                stack.push(currentOp);
            } else if (windowPattern.matcher(token).matches()) {
                int dis = Integer.parseInt(token.split("/")[1]);
                currentOp = new QryopIlWindow(dis);
                stack.push(currentOp);
            } else if (token.startsWith(")")) { // Finish current query operator.
                // If the current query operator is not an argument to
                // another query operator (i.e., the stack is empty when it
                // is removed), we're done (assuming correct syntax - see
                // below). Otherwise, add the current operator as an
                // argument to the higher-level operator, and shift
                // processing back to the higher-level operator.
                stack.pop();
                if (stack.empty())
                    break;
                Qryop arg = currentOp;
                currentOp = stack.peek();
                currentOp.add(arg);
            } else {
                // NOTE: You should do lexical processing of the token before
                // creating the query term, and you should check to see whether
                // the token specifies a particular field (e.g., apple.title).
                if ((currentOp instanceof QryopSlWSum || currentOp instanceof QryopSlWAnd) && isWeight) {
                    if (currentOp instanceof QryopSlWAnd) {
                        ((QryopSlWAnd) currentOp).weights.add(Double.parseDouble(token.trim()));
                    } else {
                        ((QryopSlWSum) currentOp).weights.add(Double.parseDouble(token.trim()));
                    }
                    isWeight = false;
                    continue;
                }

                if (token.contains(".")) {
                    String[] tokenVal = token.split("\\.");
                    String field = tokenVal[1];
                    String termVal = tokenVal[0];
                    String[] terms = tokenizeQuery(termVal);
                    if (terms.length == 0) {
                        if (currentOp instanceof QryopSlWAnd) {
                            ((QryopSlWAnd) currentOp).weights.remove(((QryopSlWAnd) currentOp).weights.size() - 1);
                        } else {
                            ((QryopSlWSum) currentOp).weights.remove(((QryopSlWSum) currentOp).weights.size() - 1);
                        }
                    } else {
                        currentOp.add(new QryopIlTerm(terms[0], field));
                    }
                } else {
                    String[] terms = tokenizeQuery(token);
                    if (terms.length == 0) {
                        if (currentOp instanceof QryopSlWAnd) {
                            ((QryopSlWAnd) currentOp).weights.remove(((QryopSlWAnd) currentOp).weights.size() - 1);
                        } else if (currentOp instanceof QryopSlWSum) {
                            ((QryopSlWSum) currentOp).weights.remove(((QryopSlWSum) currentOp).weights.size() - 1);
                        } else {
                            continue;
                        }
                    } else {
                        currentOp.add(new QryopIlTerm(terms[0]));
                    }
                }

            }
            isWeight = true;
        }

        // A broken structured query can leave unprocessed tokens on the
        // stack, so check for that.

        if (tokens.hasMoreTokens()) {
            System.err.println("Error:  Query syntax is incorrect.  " + qString);
            return null;
        }

        return currentOp;

    }

    /**
     * Print a message indicating the amount of memory used.  The
     * caller can indicate whether garbage collection should be
     * performed, which slows the program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     * @return void
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc) {
            runtime.gc();
        }

        System.out.println("Memory used:  " +
                ((runtime.totalMemory() - runtime.freeMemory()) /
                        (1024L * 1024L)) + " MB");
        //System.out.println("Total time: "+ runtime.toString());
    }

    /**
     * Print the query results.
     * <p/>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
     * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
     * PAGE, WHICH IS:
     * <p/>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    Result object generated
     * @throws IOException
     */
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

    /**
     * Given a query string, returns the terms one at a time with stopwords
     * removed and the terms stemmed using the Krovetz stemmer.
     * <p/>
     * Use this method to process raw query terms.
     *
     * @param query String containing query
     * @return Array of query tokens
     * @throws IOException
     */
    static String[] tokenizeQuery(String query) throws IOException {

        TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
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

