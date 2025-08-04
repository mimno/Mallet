package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.File;
import java.util.ArrayList;

import cc.mallet.topics.tree.TreeTopicSamplerHashD.DocData;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import junit.framework.TestCase;

/**
 * This class tests the naive sampler.
 * @author Yuening Hu
 */

public class testNaive extends TestCase{
	
	public static TreeTopicSamplerNaive Initialize() {
		
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
//		String treeFiles = "../synthetic/synthetic.wn.*";
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
		
		TreeTopicSamplerNaive topicModel = null;
		topicModel = new TreeTopicSamplerNaive(numTopics, alpha_sum, randomSeed);
		
		topicModel.initialize(treeFiles, hyperFile, vocabFile, removedFile);
		topicModel.addInstances(instances);
		
        topicModel.setNumIterations(numIterations);
        
        return topicModel;
	}
	
	public void testChangeTopic() {
		TreeTopicSamplerNaive topicModel = this.Initialize();
		for (int dd = 0; dd < topicModel.data.size(); dd++ ) {
			DocData doc = topicModel.data.get(dd);
			for (int index = 0; index < doc.tokens.size(); index++) {
				int word = doc.tokens.get(index);
				int old_topic = doc.topics.get(index);
				int old_path = doc.paths.get(index);
				int old_count = doc.topicCounts.get(old_topic);
				
				topicModel.changeTopic(dd, index, word, -1, -1);
				assertTrue(doc.topics.get(index) == -1);
				assertTrue(doc.paths.get(index) == -1);
				assertTrue(doc.topicCounts.get(old_topic) == old_count-1);
				
				int new_topic = topicModel.numTopics - old_topic - 1;
				int new_path = old_path;
				int new_count = doc.topicCounts.get(new_topic);
				topicModel.changeTopic(dd, index, word, new_topic, new_path);
				
				assertTrue(doc.topics.get(index) == new_topic);
				assertTrue(doc.paths.get(index) == new_path);
				assertTrue(doc.topicCounts.get(new_topic) == new_count+1);
			}
		}
	}
	
	public void testChangCount() {
		TreeTopicSamplerNaive topicModel = this.Initialize();
		for (int dd = 0; dd < topicModel.data.size(); dd++ ) {
			DocData doc = topicModel.data.get(dd);
			
			for (int index = 0; index < doc.tokens.size(); index++) {
				int word = doc.tokens.get(index);
				int old_topic = doc.topics.get(index);
				int old_path = doc.paths.get(index);
				
				TopicTreeWalk tw = topicModel.topics.traversals.get(old_topic);
				TIntArrayList path_nodes = topicModel.topics.wordPaths.get(word, old_path);
				
				int[] old_count = new int[path_nodes.size() - 1];
				for(int nn = 0; nn < path_nodes.size() - 1; nn++) {
					int parent = path_nodes.get(nn);
					int child = path_nodes.get(nn+1);
					old_count[nn] = tw.getCount(parent, child);
				}
				
				int[] old_node_count = new int[path_nodes.size()];
				for(int nn = 0; nn < path_nodes.size(); nn++) {
					int node = path_nodes.get(nn);
					old_node_count[nn] = tw.getNodeCount(node);
				}

				int inc = 1;
				tw.changeCount(path_nodes, inc);
				
				for(int nn = 0; nn < path_nodes.size() - 1; nn++) {
					int parent = path_nodes.get(nn);
					int child = path_nodes.get(nn+1);
					assertTrue(old_count[nn] == tw.getCount(parent, child) - inc);
				}
				
				for(int nn = 0; nn < path_nodes.size(); nn++) {
					int node = path_nodes.get(nn);
					assertTrue(old_node_count[nn] == tw.getNodeCount(node) - inc);
				}
				
			}
		}
		
	}
	
	public void testComputeTermScore() {
		TreeTopicSamplerNaive topicModel = this.Initialize();
		for (int dd = 0; dd < topicModel.data.size(); dd++ ) {
			DocData doc = topicModel.data.get(dd);
			System.out.println("------------" + dd + "------------");
			for (int index = 0; index < doc.tokens.size(); index++) {
				int word = doc.tokens.get(index);
				
				//topicModel.changeTopic(dd, index, word, -1, -1);
				
				ArrayList<double[]> topic_term_score = new ArrayList<double[]>();
				double norm = topicModel.topics.computeTopicTerm(topicModel.alpha, doc.topicCounts, word, topic_term_score);
				System.out.println(norm);
				
				for(int jj = 0; jj < topic_term_score.size(); jj++) {
					double[] tmp = topic_term_score.get(jj);
					int tt = (int) tmp[0];
					int pp = (int) tmp[1];
					double val = tmp[2];
					System.out.println(tt + " " + pp + " " + val);
				}
			}
		}
	}
	
}
