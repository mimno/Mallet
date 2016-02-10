package cc.mallet.topics;

import java.io.*;
import java.util.*;
import cc.mallet.types.*;

public class JSONTopicReports extends AbstractTopicReports implements TopicReports {
	
	public JSONTopicReports (ParallelTopicModel model) {
		super(model);
	}
	
	public void printSamplingState(PrintWriter out) throws IOException { }
	public void printTopicDocuments(PrintWriter out, int max) throws IOException { }
	public void printDocumentTopics(PrintWriter out, double threshold, int max) throws IOException { }
	public void printDenseDocumentTopics(PrintWriter out) throws IOException { }
	public void printTopicWordWeights(PrintWriter out) throws IOException { }
	public void printTypeTopicCounts(PrintWriter out) throws IOException { }
	public void printTopicPhrases(PrintWriter out, int numWords) throws IOException { }
	public void printSummary(PrintWriter out, int numWords) throws IOException {
		Formatter buffer = new Formatter();

		ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();

		buffer.format("[");

		// Print results for each topic
		for (int topic = 0; topic < model.numTopics; topic++) {
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
			int word = 0;
			Iterator<IDSorter> iterator = sortedWords.iterator();

			buffer.format("{\"topic\":%d, \"smoothing\":%f, \"words\":{", topic, model.alpha[topic]);

			while (iterator.hasNext() && word < numWords) {
				IDSorter info = iterator.next();
				buffer.format("\"%s\": %f", model.alphabet.lookupObject(info.getID()), info.getWeight());
				if (iterator.hasNext() && word < numWords - 1) {
					buffer.format(",");
				}
				word++;
			}
			buffer.format ("}}");
			if (topic < model.numTopics - 1) {
				buffer.format(",");
			}
		}
		
		buffer.format("]");

		out.println(buffer);
	}
	
	public static void main (String[] args) throws Exception {
		InstanceList instances = InstanceList.load(new File(args[0]));
		
		ParallelTopicModel model = new ParallelTopicModel(50, 5.0, 0.01);
		model.addInstances(instances);
		model.setNumIterations(100);
		model.estimate();
		
		TopicReports reports = new JSONTopicReports(model);
		reports.printSummary(new File("summary.json"), 20);
	}
	
}