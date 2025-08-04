package cc.mallet.topics.tree;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectIntHashMap;
import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Maths;


/**
 * This class generates the vocab file from mallet input.
 * This generated vocab can be filtered by either frequency or tfidf.
 * Tree-based topic model need this vocab for:
 * (1) filter words more flexible
 * (2) generate tree structure
 * (3) allow removing words
 * Main entrance: genVocab()
 * 
 * @author Yuening Hu
 */

public class VocabGenerator {
	public static TObjectDoubleHashMap<String> getIdf(InstanceList data) {
		// get idf
		TObjectDoubleHashMap<String> idf = new TObjectDoubleHashMap<String> ();

		for (Instance instance : data) {
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			HashSet<String> words = new HashSet<String>();
			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				words.add(word);
			}
			for(String word : words) {
				idf.adjustOrPutValue(word, 1, 1);
			}
		}
		
		int D = data.size();
		for(Object ob : idf.keys()){
			String word = (String) ob;
			double value = D / (1 + idf.get(word));
			value = Math.log(value) - idf.get(word);
			idf.adjustValue(word, value);
		}
		
		System.out.println("Idf size: " + idf.size());
		return idf;
	}
	
	public static TObjectDoubleHashMap<String> computeTfidf(InstanceList data) {
		// get idf
		TObjectDoubleHashMap<String> idf = getIdf(data);
		
		// compute tf-idf for each word
		HashMap<String, HashSet<Double>> tfidf = new HashMap<String, HashSet<Double>> ();
		
		for (Instance instance : data) {
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			TObjectIntHashMap<String> tf = new TObjectIntHashMap();
			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				tf.adjustOrPutValue(word, 1, 1);
			}
			for(Object ob : tf.keys()) {
				String word = (String) ob;
				HashSet<Double> values;
				if (tfidf.containsKey(word)) {
					values = tfidf.get(word);
				} else {
					values = new HashSet<Double> ();
					tfidf.put(word, values);
				}
				double value = tf.get(word) * idf.get(word);
				values.add(value);
			}
		}
		
		// averaged tfidf
		TObjectDoubleHashMap<String> vocabtfidf = new TObjectDoubleHashMap();
		for(String word : tfidf.keySet()) {
			double sum = 0;
			int count = tfidf.get(word).size();
			for(double value : tfidf.get(word)) {
				sum += value;
			}
			sum = sum / count;
			vocabtfidf.put(word, sum);
		}
		
		System.out.println("vocab tfidf size: " + vocabtfidf.size());
		return vocabtfidf;
	}
	
	public static TObjectDoubleHashMap getFrequency (InstanceList data) {
		TObjectDoubleHashMap<String> freq = new TObjectDoubleHashMap<String> ();
		Alphabet alphabet = data.getAlphabet();
		for(int ii = 0; ii < alphabet.size(); ii++) {
			String word = alphabet.lookupObject(ii).toString();
			freq.put(word, 0);
		}

		for (Instance instance : data) {
			FeatureSequence original_tokens = (FeatureSequence) instance.getData();
			for (int jj = 0; jj < original_tokens.getLength(); jj++) {
				String word = (String) original_tokens.getObjectAtPosition(jj);
				freq.adjustValue(word, 1);
			}
		}
		
		System.out.println("Alphabet size: " + alphabet.size());
		System.out.println("Frequency size: " + freq.size());
		return freq;
	}
	
	public static void genVocab_all(InstanceList data, String vocab, Boolean tfidfRank, double tfidfthresh, double freqthresh, double wordlength) {
	//public static void genVocab(InstanceList data, String vocab) {
		try{
			File file = new File(vocab);
			PrintStream out = new PrintStream (file);
			
			int language_id = 0;
			Alphabet alphabet = data.getAlphabet();
			for(int ii = 0; ii < alphabet.size(); ii++) {
				String word = alphabet.lookupObject(ii).toString();
				System.out.println(word);
				out.println(language_id + "\t" + word);
			}
			out.close();
		} catch (IOException e) {
			e.getMessage();
		}

	}
	
	/**
	 * After the preprocessing of mallet, a vocab is needed to generate
	 * the prior tree. So this function simply read in the alphabet
	 * of the training data, filter the words either by frequency or tfidf,
	 * then output the vocab.
	 * Currently, the language_id is fixed.
	 */
	public static void genVocab(InstanceList[] data, String vocab, Boolean tfidfRank, double tfidfthresh, double freqthresh, double wordlength) {
		
		class WordCount implements Comparable {
			String word;
			double value;
			public WordCount (String word, double value) { this.word = word; this.value = value; }
			public final int compareTo (Object o2) {
				if (value > ((WordCount)o2).value)
					return -1;
				else if (value == ((WordCount)o2).value)
					return 0;
				else return 1;
			}
		}		
		
		
		try{
			File file = new File(vocab);
			PrintStream out = new PrintStream (file, "UTF8");
			
			HashSet<String> allwords = new HashSet<String> ();
			for (int ll = 0; ll < data.length; ll++) {
				System.out.println("Language " + ll);
				TObjectDoubleHashMap freq = getFrequency(data[ll]);
				TObjectDoubleHashMap tfidf = computeTfidf(data[ll]);
				TObjectDoubleHashMap selected;
				if (tfidfRank) {
					selected = tfidf;
				} else {
					selected = freq;
				}
				
				WordCount[] array = new WordCount[selected.keys().length];
				int index = -1;
				for(Object o : selected.keys()) {
					String word = (String)o;
					double count = selected.get(word);
					index++;
					array[index] = new WordCount(word, count);
				}
				System.out.println("Array size: " + array.length);
				Arrays.sort(array);		
				System.out.println("After sort array size: " + array.length);
				
				int language_id = ll;
				int count = 0;
				for(int ii = 0; ii < array.length; ii++) {
					String word = array[ii].word;
					if (word.length() >= wordlength && tfidf.get(word) > tfidfthresh && freq.get(word) > freqthresh) {
						if (allwords.contains(word)) {
							continue;
						}
						allwords.add(word);
						out.println(language_id + "\t" + array[ii].word + "\t" + tfidf.get(word) + "\t" + (int)freq.get(word));
						count++;
					}
				}
				System.out.println("Filtered vocab size: " + count);
				System.out.println("*******************");
			}
			out.close();
		
		} catch (IOException e) {
			e.getMessage();
		}
	}
	
	public static void main(String[] args) {
		//String input = "input/synthetic-topic-input.mallet";
		//String vocab = "input/synthetic.voc";
		
		String input = "../../itm-evaluation/results/fbis-itm/input/fbis-itm-topic-input.mallet";
		String vocab = "../../itm-evaluation/results/fbis-itm/input/fbis-itm.voc";
		
		InstanceList[] instances = new InstanceList[ 2 ];
		InstanceList data = InstanceList.load (new File(input));
		instances[0] = data;
		InstanceList data1 = InstanceList.load (new File(input));
		instances[1] = data1;
		genVocab(instances, vocab, true, 1, 10, 3);
		System.out.println("Done!");
	}
}
