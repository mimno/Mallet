package cc.mallet.topics;

import java.io.*;

public abstract class AbstractTopicReports implements TopicReports {
	
	ParallelTopicModel model;
	
	public AbstractTopicReports(ParallelTopicModel model) {
		this.model = model;
	}
	
	/* These methods will write to the provided writer */
	public void printSamplingState(PrintWriter out) throws IOException { }
	public void printTopicDocuments(PrintWriter out, int max) throws IOException { }
	public void printDocumentTopics(PrintWriter out, double threshold, int max) throws IOException { }
	public void printDenseDocumentTopics(PrintWriter out) throws IOException { }
	public void printTopicWordWeights(PrintWriter out) throws IOException { }
	public void printTypeTopicCounts(PrintWriter out) throws IOException { }
	public void printTopicPhrases(PrintWriter out, int numWords) throws IOException { }
	public void printSummary(PrintWriter out, int numWords) throws IOException { }

	/* These methods open a file, pass the writer to the above methods, and then close the file */
	public void printSamplingState(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printSamplingState(out);
		out.close();
	}
	public void printTopicDocuments(File file, int max) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printTopicDocuments(out, max);
		out.close();
	}
	public void printDocumentTopics(File file, double threshold, int max) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printDocumentTopics(out, threshold, max);
		out.close();
	}
	public void printDenseDocumentTopics(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printDenseDocumentTopics(out);
		out.close();
	}
	public void printTopicWordWeights(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printTopicWordWeights(out);
		out.close();
	}
	public void printTypeTopicCounts(File file) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printTypeTopicCounts(out);
		out.close();
	}
	public void printTopicPhrases(File file, int numWords) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printTopicPhrases(out, numWords);
		out.close();
	}
	public void printSummary(File file, int numWords) throws IOException {
		PrintWriter out = new PrintWriter (new FileWriter (file) );
		printSummary(out, numWords);
		out.close();
	}
}