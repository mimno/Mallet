package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;

import java.io.File;
import java.util.ArrayList;

import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import cc.mallet.types.InstanceList;
import junit.framework.TestCase;

/**
 * This class tests the fast sampler.
 * @author Yuening Hu
 */

public class testFast extends TestCase{
	
	public TreeTopicSamplerFast Initialize() {
		
		String inputFile = "input/toy/toy-topic-input.mallet";
		String treeFiles = "input/toy/toy.wn.*";
		String hyperFile = "input/toy/tree_hyperparams";
		String vocabFile = "input/toy/toy.voc";	
		String removedFile = "input/toy/removed";		
		int numTopics = 3;
		double alpha_sum = 0.3;
		int randomSeed = 0;
		int numIterations = 10;
		
//		String inputFile = "../input/synthetic-topic-input.mallet";
//		String treeFiles = "../synthetic/synthetic_empty.wn.*";
//		String hyperFile = "../synthetic/tree_hyperparams";
//		String vocabFile = "../synthetic/synthetic.voc";
//		int numTopics = 5;
//		double alpha_sum = 0.5;
//		int randomSeed = 0;
//		int numIterations = 10;
		
		InstanceList[] instances = new InstanceList[1];
		InstanceList ilist = InstanceList.load (new File(inputFile));
		System.out.println ("Data loaded.");
		instances[0] = ilist;
		
		TreeTopicSamplerFast topicModel = null;
		topicModel = new TreeTopicSamplerFast(numTopics, alpha_sum, randomSeed, false);
		
		topicModel.initialize(treeFiles, hyperFile, vocabFile, removedFile);
		topicModel.addInstances(instances);
		
        topicModel.setNumIterations(numIterations);
        
        return topicModel;
	}
	
	public void testUpdateParams() {
		TreeTopicSamplerFast topicModel = this.Initialize();
		topicModel.topics.updateParams();
		
		for(int dd = 0; dd < topicModel.data.size(); dd++) {
			System.out.println(topicModel.data.get(dd));
		}
		
		System.out.println("**************\nNormalizer");
		int numPaths = topicModel.topics.pathToWord.size();
		for(int tt = 0; tt < topicModel.numTopics; tt++) {
			for(int pp = 0; pp < numPaths; pp++) {
				System.out.println("topic " + tt + " path " + pp + " normalizer " + topicModel.topics.normalizer.get(tt, pp));
			}
		}
		
		System.out.println("**************\nNon zero paths");
		for(int ww : topicModel.topics.nonZeroPaths.keys()) {
			for(int tt : topicModel.topics.nonZeroPaths.get(ww).getKey1Set()) {
				for(int pp : topicModel.topics.nonZeroPaths.get(ww).get(tt).keys()) {
					System.out.println("word " + ww + " topic " + tt + " path " + pp + " " + topicModel.topics.nonZeroPaths.get(ww).get(tt, pp));
				}
			}
		}
	}
	
	public void testUpdatePathmaskedCount() {
		TreeTopicSamplerFast topicModel = this.Initialize();
		topicModel.topics.updateParams();
		int numPaths = topicModel.topics.pathToWord.size();
		
		TreeTopicModelFast topics = (TreeTopicModelFast)topicModel.topics;
		
		for (int ww : topics.nonZeroPaths.keys()) {
			for(int tt : topics.nonZeroPaths.get(ww).getKey1Set()) {
				for(int pp : topicModel.topics.nonZeroPaths.get(ww).get(tt).keys()) {
					TIntArrayList path_nodes = topics.wordPaths.get(ww, pp);					
					int parent = path_nodes.get(path_nodes.size() - 2);
					int child = path_nodes.get(path_nodes.size() - 1);
					
					int mask = topics.nonZeroPaths.get(ww).get(tt, pp) - topics.traversals.get(tt).getCount(parent, child);
					
					System.out.println("*************************");
					System.out.println("Topic " + tt + " Word " + ww + " path " + pp);
					String tmp = "[";
					for (int ii : path_nodes.toNativeArray()) {
						tmp += " " + ii;
					}
					System.out.println("Real path " + tmp + " ]");
					System.out.println("Real count " + topics.traversals.get(tt).getCount(parent, child));
					System.out.println("Masked count " + topics.nonZeroPaths.get(ww).get(tt, pp));
					System.out.println("Masekd count " + Integer.toBinaryString(topics.nonZeroPaths.get(ww).get(tt, pp)));
					System.out.println("*************************");
				}
			}
		}
	}
	
	public void testChangeTopic() {
		TreeTopicSamplerFast topicModel = this.Initialize();
		topicModel.topics.updateParams();
		TreeTopicModelFast topics = (TreeTopicModelFast)topicModel.topics;
		//for(int dd = 0; dd < topicModel.data.size(); dd++){
		for(int dd = 0; dd < 1; dd++){
			DocData doc = topicModel.data.get(dd);
			for(int ii = 0; ii < doc.tokens.size(); ii++) {	
				int word = doc.tokens.get(ii);
				int old_topic = doc.topics.get(ii);
				int old_path = doc.paths.get(ii);
				TIntArrayList path_nodes = topicModel.topics.wordPaths.get(word, old_path);
				int node = path_nodes.get(0);
				int leaf = path_nodes.get(path_nodes.size() - 1);
				int total = 0;
				for(int nn : topics.traversals.get(word).counts.get(node).keys()){
					total += topics.traversals.get(word).getCount(node, nn);
				}
				
				assertTrue(topics.traversals.get(word).getNodeCount(node) == total);
				
				System.out.println("*************************");
				System.out.println("old topic " + old_topic + " word " + word);
				System.out.println("old normalizer " + topics.normalizer.get(old_topic, old_path));
				System.out.println("old root count " + topics.traversals.get(old_topic).getNodeCount(node) + " " + total);
				System.out.println("old non zero count " + Integer.toBinaryString(topics.nonZeroPaths.get(word).get(old_topic, old_path)));
				System.out.println("old leaf count " + topics.traversals.get(old_topic).getNodeCount(leaf));
				
				topicModel.changeTopic(dd, ii, word, -1, -1);
				
				total = 0;
				for(int nn : topics.traversals.get(old_topic).counts.get(node).keys()){
					total += topics.traversals.get(old_topic).getCount(node, nn);
				}
				assertTrue(topics.traversals.get(old_topic).getNodeCount(node) == total);
				System.out.println("*************************");
				System.out.println("updated old topic " + old_topic + " word " + word);
				System.out.println("updated old normalizer " + topics.normalizer.get(old_topic, old_path));
				System.out.println("updated old root count " + topics.traversals.get(old_topic).getNodeCount(node) + " " + total);
				System.out.println("updated old non zero count " + Integer.toBinaryString(topics.nonZeroPaths.get(word).get(old_topic, old_path)));
				System.out.println("updated old leaf count " + topics.traversals.get(old_topic).getNodeCount(leaf));
				
				
				int new_topic = topicModel.numTopics - old_topic - 1;
				int new_path = old_path;
				
				total = 0;
				for(int nn : topics.traversals.get(new_topic).counts.get(node).keys()){
					total += topics.traversals.get(new_topic).getCount(node, nn);
				}
				assertTrue(topics.traversals.get(new_topic).getNodeCount(node) == total);
				
				System.out.println("*************************");
				System.out.println("new topic " + new_topic + " word " + word);
				System.out.println("new normalizer " + topics.normalizer.get(new_topic, new_path));
				System.out.println("new root count " + topics.traversals.get(new_topic).getNodeCount(node) + " " + total);
				System.out.println("new non zero count " + Integer.toBinaryString(topics.nonZeroPaths.get(word).get(new_topic, new_path)));
				System.out.println("new leaf count " + topics.traversals.get(new_topic).getNodeCount(leaf));
				
				topicModel.changeTopic(dd, ii, word, new_topic, new_path);
				
				
				total = 0;
				for(int nn : topics.traversals.get(new_topic).counts.get(node).keys()){
					total += topics.traversals.get(new_topic).getCount(node, nn);
				}
				assertTrue(topics.traversals.get(new_topic).getNodeCount(node) == total);
				System.out.println("*************************");
				System.out.println("updated new topic " + new_topic + " word " + word);
				System.out.println("updated new normalizer " + topics.normalizer.get(new_topic, new_path));
				System.out.println("updated new root count " + topics.traversals.get(new_topic).getNodeCount(node) + " " + total);
				System.out.println("updated new non zero count " + Integer.toBinaryString(topics.nonZeroPaths.get(word).get(new_topic, new_path)));
				System.out.println("updated new leaf count " + topics.traversals.get(new_topic).getNodeCount(leaf));
				
				System.out.println("*************************\n");
			}
		}
	}
	
	public void testBinValues() {
		TreeTopicSamplerFast topicModelFast = this.Initialize();
		topicModelFast.topics.updateParams();
		
		TreeTopicSamplerNaive topicModelNaive = testNaive.Initialize();
		topicModelNaive.topics.updateParams();
		
		//for(int dd = 0; dd < topicModelFast.data.size(); dd++){
		for(int dd = 0; dd < 1; dd++){
			DocData doc = topicModelFast.data.get(dd);
			DocData doc1 = topicModelNaive.data.get(dd);
			
			//for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			for(int ii = 4; ii < 5; ii++) {
				int word = doc.tokens.get(ii);
				int topic = doc.topics.get(ii);
				int path = doc.paths.get(ii);
				
				double smoothing = topicModelFast.callComputeTermSmoothing(word);
				double topicbeta = topicModelFast.callComputeTermTopicBeta(doc.topicCounts, word);
				ArrayList<double[]> dict = new ArrayList<double[]>();
				double topictermscore = topicModelFast.topics.computeTopicTerm(topicModelFast.alpha, 
						doc.topicCounts, word, dict);
				double norm = smoothing + topicbeta + topictermscore;
				
				double smoothing1 = topicModelFast.computeTopicSmoothTest(word);
				double topicbeta1 = topicModelFast.computeTopicTermBetaTest(doc.topicCounts, word);
				HIntIntDoubleHashMap dict1 = new HIntIntDoubleHashMap();
				double topictermscore1 = topicModelFast.computeTopicTermScoreTest(topicModelFast.alpha, 
						doc.topicCounts, word, dict1);
				double norm1 = smoothing1 + topicbeta1 + topictermscore1;
				
				System.out.println("*************");
				System.out.println("Index " + ii);
				System.out.println(smoothing + " " + smoothing1);
				System.out.println(topicbeta + " " + topicbeta1);
				System.out.println(topictermscore + " " + topictermscore1);
				
				ArrayList<double[]> dict2 = new ArrayList<double[]>();
				double norm2 = topicModelFast.computeTopicTermTest(topicModelNaive.alpha, doc.topicCounts, word, dict2);
				
				ArrayList<double[]> dict3 = new ArrayList<double[]>();
				double norm3 = topicModelNaive.topics.computeTopicTerm(topicModelNaive.alpha, doc.topicCounts, word, dict3);

				System.out.println(norm + " " + norm1 + " " + norm2 + " " + norm3);
//				if (norm1 != norm2) {
//					System.out.println(norm + " " + norm1 + " " + norm2 + " " + norm3 );
//				}
				System.out.println("*************");
				assert(norm == norm1);
				assert(1 == 0);
			}
		}
	}
}
