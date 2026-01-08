package cc.mallet.topics.tree;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class CorpusWriter {
	
	public static void writeCorpus(InstanceList training, String outfilename, String vocabname) throws FileNotFoundException {
		
		ArrayList<String> vocab = loadVocab(vocabname);
		
		PrintStream out = new PrintStream (new File(outfilename));
		
		int count = -1;
		for (Instance instance : training) {
			count++;
			if (count % 1000 == 0) {
				System.out.println("Processed " + count + " number of documents!");
			}
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			String name = instance.getName().toString();

			TIntArrayList tokens = new TIntArrayList(original_tokens.getLength());
			TIntIntHashMap topicCounts = new TIntIntHashMap ();			
			TIntArrayList topics = new TIntArrayList(original_tokens.getLength());
			TIntArrayList paths = new TIntArrayList(original_tokens.getLength());

			String doc = "";
			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				int token = vocab.indexOf(word);
				doc += word + " ";
				//if(token != -1) {
				//	doc += word + " ";
				//}
			}
			System.out.println(name);
			System.out.println(doc);
			
			if (!doc.equals("")) {
				out.println(doc);
			}
		}
		
		out.close();
	}
	
	public static void writeCorpusMatrix(InstanceList training, String outfilename, String vocabname) throws FileNotFoundException {
	
		// each document is represented in a vector (vocab size), and each entry is the frequency of a word.
		
		ArrayList<String> vocab = loadVocab(vocabname);
		
		PrintStream out = new PrintStream (new File(outfilename));
		
		int count = -1;
		for (Instance instance : training) {
			count++;
			if (count % 1000 == 0) {
				System.out.println("Processed " + count + " number of documents!");
			}
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			String name = instance.getName().toString();

			int[] tokens = new int[vocab.size()];
			for (int jj = 0; jj < tokens.length; jj++) {
				tokens[jj] = 0;
			}

			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				int index = vocab.indexOf(word);
				tokens[index] += 1;
			}
			
			String doc = "";
			for (int jj = 0; jj < tokens.length; jj++) {
				doc += tokens[jj] + "\t";
			}
			
			System.out.println(name);
			System.out.println(doc);
			
			if (!doc.equals("")) {
				out.println(doc);
			}
		}
		
		out.close();
	}
	
	public static ArrayList<String> loadVocab(String vocabFile) {
		
		ArrayList<String> vocab = new ArrayList<String>();
		
		try {
			FileInputStream infstream = new FileInputStream(vocabFile);
			DataInputStream in = new DataInputStream(infstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				String[] str = strLine.split("\t");
				if (str.length > 1) {
					vocab.add(str[1]);
				} else {
					System.out.println("Error! " + strLine);
				}
			}
			in.close();
			
		} catch (IOException e) {
			System.out.println("No vocab file Found!");
		}
		return vocab;
	}
	
	
	public static void main(String[] args) {
		//String input = "input/nyt/nyt-topic-input.mallet";
		//String corpus = "../../pylda/variational/data/20_news/doc.dat";
		//String vocab = "../../pylda/variational/data/20_news/voc.dat";
		
		String input = "input/synthetic/synthetic-topic-input.mallet";
		//String corpus = "../../spectral/input/synthetic-ordered.dat";
		//String vocab = "../../spectral/input/synthetic-ordered.voc";
		String corpus = "../../spectral/input/synthetic.dat";
		String vocab = "../../spectral/input/synthetic.voc";
		
		//String input = "../../itm-evaluation/results/govtrack-109/input/govtrack-109-topic-input.mallet";
		//String corpus = "../../pylda/variational/data/20_news/doc.dat";
		//String vocab = "../../itm-evaluation/results/govtrack-109/input/govtrack-109.voc";
		
		try{
			InstanceList data = InstanceList.load (new File(input));
			writeCorpusMatrix(data, corpus, vocab);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
