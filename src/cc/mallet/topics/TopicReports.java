package cc.mallet.topics;

import java.io.*;

public interface TopicReports {
	
	/* These methods will write to the provided writer */
	void printSamplingState(PrintWriter out) throws IOException;
	void printTopicDocuments(PrintWriter out, int max) throws IOException;
	void printDocumentTopics(PrintWriter out, double threshold, int max) throws IOException;
	void printDenseDocumentTopics(PrintWriter out) throws IOException;
	void printTopicWordWeights(PrintWriter out) throws IOException;
	void printTypeTopicCounts(PrintWriter out) throws IOException;
	void printTopicPhrases(PrintWriter out, int numWords) throws IOException;
	void printSummary(PrintWriter out, int numWords) throws IOException;

	/* These methods open a file, pass the writer to the above methods, and then close the file */
	void printSamplingState(File file) throws IOException;
	void printTopicDocuments(File file, int max) throws IOException;
	void printDocumentTopics(File file, double threshold, int max) throws IOException;
	void printDenseDocumentTopics(File file) throws IOException;
	void printTopicWordWeights(File file) throws IOException;
	void printTypeTopicCounts(File file) throws IOException;
	void printTopicPhrases(File file, int numWords) throws IOException;
	void printSummary(File file, int numWords) throws IOException;
	
}