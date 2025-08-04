package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import cc.mallet.types.Dirichlet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.util.Randoms;

/**
 * This class defines the interface of a tree topic sampler.
 * 
 * @author Yuening Hu
 */

public interface TreeTopicSamplerInterface {
	
	/* Implemented in TreeTopicSampler.java.
	   Shared code by TreeTopicSamplerSortD.java and TreeTopicSamplerHashD.java
	 */
	public void setNumIterations(int iters);
	public void resume(InstanceList[] training, String resumeDir);
	
	// Also implemented in TreeTopicSampler.java, but do not need to be defined in interface.
	//public int getNumIterations();
	//public void resumeLHood(String lhoodFile) throws IOException;
	//public void report(String outputDir, int topWords) throws IOException;
	//public void printTopWords(File file, int numWords) throws IOException;
	//public void printStats (File file) throws IOException;
	//public void loadVocab(String vocabFile);
	//public void loadStopWords(String stopwordFile);
	//public void loadConstraints(String consFile);
	//abstract public void sampleDoc(int doc);
	
	
	/* Same code for TreeTopicSamplerSortD.java and TreeTopicSamplerHashD.java
	   But related with this.topics, so not the the shared parent class.
	 */
	public void initialize(String treeFiles, String hyperFile, String vocabFile, String removedwordsFile);
	public void estimate(int numIterations, String outputFolder, int outputInterval, int topWords);
	public TreeTopicInferencer getInferencer();
	public TreeMarginalProbEstimator getProbEstimator();
	// do not need to be defined in interface.
	//public double lhood();
	//public String displayTopWords (int numWords);
	//public void printState (File file) throws IOException;
	
	
	
	/* Different code for TreeTopicSamplerSortD.java and TreeTopicSamplerHashD.java
	   Stay in these two java files separately.
	 */	
	public void addInstances(InstanceList[] training);
	public void clearTopicAssignments(String option, String consFile, String keepFile);
	// Do not need to be defined in interface.
	//public void resumeStates(InstanceList training, String statesFile) throws IOException;
	//public void changeTopic(int doc, int index, int word, int new_topic, int new_path);
	//public double docLHood();
	//public void printDocumentTopics (File file) throws IOException;
	
}