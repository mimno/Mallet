package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectIntHashMap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.Alphabet;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.Randoms;

/**
 * This class defines a naive tree topic sampler.
 * It calls the naive tree topic model.
 * 
 * @author Yuening Hu
 */

public class TreeTopicSamplerNaive extends TreeTopicSamplerHashD {
		
	public TreeTopicSamplerNaive (int numberOfTopics, double alphaSum) {
		this (numberOfTopics, alphaSum, 0);
	}
		
	public TreeTopicSamplerNaive (int numberOfTopics, double alphaSum, int seed) {
		super (numberOfTopics, alphaSum, seed);
		this.topics = new TreeTopicModelNaive(this.numTopics, this.random);
	}

	/**
	 * For each word in a document, firstly covers its topic and path, then sample a
	 * topic and path, and update.
	 */
	public void sampleDoc(int doc_id){
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
			
			this.changeTopic(doc_id, ii, word, -1, -1);
			ArrayList<double[]> topic_term_score = new ArrayList<double[]>();
			double norm = this.topics.computeTopicTerm(this.alpha, doc.topicCounts, word, topic_term_score);
			//System.out.println(norm);
			
			int new_topic = -1;
			int new_path = -1;
			
			double sample = this.random.nextDouble();
			//double sample = 0.8;
			sample *= norm;
			
			for(int jj = 0; jj < topic_term_score.size(); jj++) {
				double[] tmp = topic_term_score.get(jj);
				int tt = (int) tmp[0];
				int pp = (int) tmp[1];
				double val = tmp[2];
				sample -= val;
				if (sample <= 0.0) {
					new_topic = tt;
					new_path = pp;
					break;
				}
			}
			
			myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling!");
			
			this.changeTopic(doc_id, ii, word, new_topic, new_path);
		}
	}
	
}
