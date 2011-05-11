package cc.mallet.topics;

import java.io.*;
import java.util.*;
import java.text.*;

import cc.mallet.types.*;
import cc.mallet.util.*;

import gnu.trove.*;

public class TopicModelDiagnostics {

	int numTopics;
	int numTopWords;

	/**  All words in sorted order, with counts */
	ArrayList<TreeSet<IDSorter>> topicSortedWords;
	
	/** The top N words in each topic in an array for easy access */
	String[][] topicTopWords;

	ArrayList<TopicScores> diagnostics; 

	ParallelTopicModel model;
	Alphabet alphabet;

	int[][][] topicCodocumentMatrices;
	double[][] docTopicProportions;
	int[] wordTypeCounts;
	int numTokens = 0;

	public TopicModelDiagnostics (ParallelTopicModel model, int numTopWords) {
		numTopics = model.getNumTopics();
		this.numTopWords = numTopWords;

		this.model = model;

		alphabet = model.getAlphabet();
		topicSortedWords = model.getSortedWords();

		topicTopWords = new String[numTopics][numTopWords];

		diagnostics = new ArrayList<TopicScores>();

		for (int topic = 0; topic < numTopics; topic++) {

			int position = 0;
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
                        
			// How many words should we report? Some topics may have fewer than
			//  the default number of words with non-zero weight.
			int limit = numTopWords;
			if (sortedWords.size() < numTopWords) { limit = sortedWords.size(); }

			Iterator<IDSorter> iterator = sortedWords.iterator();
			for (int i=0; i < limit; i++) {
				IDSorter info = iterator.next();
				topicTopWords[topic][i] = (String) alphabet.lookupObject(info.getID());
			}

		}

		collectDocumentStatistics();
		
		diagnostics.add(getTokensPerTopic(model.tokensPerTopic));
		diagnostics.add(getWordLengthScores());
		diagnostics.add(getCoherence());
		diagnostics.add(getDistanceFromUniform());
		diagnostics.add(getDistanceFromCorpus());
		diagnostics.add(getEffectiveNumberOfWords());
		diagnostics.add(getTokenDocumentDiscrepancies());
		diagnostics.add(getRank1Percent());
	}

	public void collectDocumentStatistics () {

		topicCodocumentMatrices = new int[numTopics][numTopWords][numTopWords];
		wordTypeCounts = new int[alphabet.size()];
		numTokens = 0;

		// This is an array of hash sets containing the words-of-interest for each topic,
		//  used for checking if the word at some position is one of those words.
		TIntHashSet[] topicTopWordIndices = new TIntHashSet[numTopics];
		
		// The same as the topic top words, but with int indices instead of strings,
		//  used for iterating over positions.
		int[][] topicWordIndicesInOrder = new int[numTopics][numTopWords];

		// This is an array of hash sets that will hold the words-of-interest present in a document,
		//  which will be cleared after every document.
		TIntHashSet[] docTopicWordIndices = new TIntHashSet[numTopics];
		
		int numDocs = model.getData().size();
		docTopicProportions = new double[numDocs][numTopics];

		// The count of each topic, again cleared after every document.
		int[] topicCounts = new int[numTopics];

		for (int topic = 0; topic < numTopics; topic++) {
			TIntHashSet wordIndices = new TIntHashSet();

			for (int i = 0; i < numTopWords; i++) {
				if (topicTopWords[topic][i] != null) {
					int type = alphabet.lookupIndex(topicTopWords[topic][i]);
					topicWordIndicesInOrder[topic][i] = type;
					wordIndices.add(type);
				}
			}
			
			topicTopWordIndices[topic] = wordIndices;
			docTopicWordIndices[topic] = new TIntHashSet();
		}

		int doc = 0;

		for (TopicAssignment document: model.getData()) {

			FeatureSequence tokens = (FeatureSequence) document.instance.getData();
			FeatureSequence topics =  (FeatureSequence) document.topicSequence;
			
			for (int position = 0; position < tokens.size(); position++) {
				int type = tokens.getIndexAtPosition(position);
				int topic = topics.getIndexAtPosition(position);

				numTokens++;
				wordTypeCounts[type]++;

				topicCounts[topic]++;
				docTopicProportions[doc][topic]++;
				
				if (topicTopWordIndices[topic].contains(type)) {
					docTopicWordIndices[topic].add(type);
				}
			}

			int docLength = tokens.size();

			if (docLength > 0) {
				for (int topic = 0; topic < numTopics; topic++) {
					docTopicProportions[doc][topic] /= docLength;
					
					TIntHashSet supportedWords = docTopicWordIndices[topic];
					int[] indices = topicWordIndicesInOrder[topic];
					if (topicCounts[topic] > 0) {
						for (int i = 0; i < numTopWords; i++) {
							if (supportedWords.contains(indices[i])) {
								for (int j = i; j < numTopWords; j++) {
									if (i == j) {
										// Diagonals are total number of documents with word W in topic T
										topicCodocumentMatrices[topic][i][i]++;
									}
									else if (supportedWords.contains(indices[j])) {
										topicCodocumentMatrices[topic][i][j]++;
										topicCodocumentMatrices[topic][j][i]++;
									}
								}
							}
						}
						
						docTopicWordIndices[topic].clear();
						topicCounts[topic] = 0;
					}
				}
			}

			doc++;
		}
	}

	public TopicScores getTokensPerTopic(int[] tokensPerTopic) {
		TopicScores scores = new TopicScores("tokens", numTopics, numTopWords);

		for (int topic = 0; topic < numTopics; topic++) {
			scores.setTopicScore(topic, tokensPerTopic[topic]);
		}

		return scores;
	}

	public TopicScores getDistanceFromUniform() {
		int[] tokensPerTopic = model.tokensPerTopic;

		TopicScores scores = new TopicScores("uniform_dist", numTopics, numTopWords);
        scores.wordScoresDefined = true;

		int numTypes = alphabet.size();

		for (int topic = 0; topic < numTopics; topic++) {

			double topicScore = 0.0;
			int position = 0;
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);

			for (IDSorter info: sortedWords) {
				int type = info.getID();
				double count = info.getWeight();

				double score = (count / tokensPerTopic[topic]) *
					Math.log( (count * numTypes) / tokensPerTopic[topic] );

				if (position < numTopWords) {
					scores.setTopicWordScore(topic, position, score);
				}
				
				topicScore += score;
				position++;
			}

			scores.setTopicScore(topic, topicScore);
		}

		return scores;
	}

	public TopicScores getEffectiveNumberOfWords() {
		int[] tokensPerTopic = model.tokensPerTopic;

		TopicScores scores = new TopicScores("eff_num_words", numTopics, numTopWords);

		int numTypes = alphabet.size();

		for (int topic = 0; topic < numTopics; topic++) {

			double sumSquaredProbabilities = 0.0;
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);

			for (IDSorter info: sortedWords) {
				int type = info.getID();
				double probability = info.getWeight() / tokensPerTopic[topic];
				
				sumSquaredProbabilities += probability * probability;
			}

			scores.setTopicScore(topic, 1.0 / sumSquaredProbabilities);
		}

		return scores;
	}

	/** Low-quality topics may be very similar to the global distribution. */
	public TopicScores getDistanceFromCorpus() {

		int[] tokensPerTopic = model.tokensPerTopic;

		TopicScores scores = new TopicScores("corpus_dist", numTopics, numTopWords);
		scores.wordScoresDefined = true;

		for (int topic = 0; topic < numTopics; topic++) {

			double coefficient = (double) numTokens / tokensPerTopic[topic];

			double topicScore = 0.0;
			int position = 0;
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);

			for (IDSorter info: sortedWords) {
				int type = info.getID();
				double count = info.getWeight();

				double score = (count / tokensPerTopic[topic]) *
					Math.log( coefficient * count / wordTypeCounts[type] );

				if (position < numTopWords) {
					//System.out.println(alphabet.lookupObject(type) + ": " + count + " * " + numTokens + " / " + wordTypeCounts[type] + " * " + tokensPerTopic[topic] + " = " + (coefficient * count / wordTypeCounts[type]));
					scores.setTopicWordScore(topic, position, score);
				}
				
				topicScore += score;

				position++;
			}

			scores.setTopicScore(topic, topicScore);
		}

		return scores;
	}

	public TopicScores getTokenDocumentDiscrepancies() {
		TopicScores scores = new TopicScores("token-doc-diff", numTopics, numTopWords);
        scores.wordScoresDefined = true;
		
		for (int topic = 0; topic < numTopics; topic++) {
			int[][] matrix = topicCodocumentMatrices[topic];
			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);

			double topicScore = 0.0;
			
			double[] wordDistribution = new double[numTopWords];
			double[] docDistribution = new double[numTopWords];

			double wordSum = 0.0;
			double docSum = 0.0;

			int position = 0;
			Iterator<IDSorter> iterator = sortedWords.iterator();
			while (iterator.hasNext() && position < numTopWords) {
				IDSorter info = iterator.next();
				
				wordDistribution[position] = info.getWeight();
				docDistribution[position] = matrix[position][position];

				wordSum += wordDistribution[position];
				docSum += docDistribution[position];
				
				position++;
			}

			for (position = 0; position < numTopWords; position++) {
				double p = wordDistribution[position] / wordSum;
				double q = docDistribution[position] / docSum;
				double meanProb = 0.5 * (p + q);

				double score = 0.0;
				if (p > 0) {
					score += 0.5 * p * Math.log(p / meanProb);
				}
				if (q > 0) {
					score += 0.5 * q * Math.log(q / meanProb);
				}

				scores.setTopicWordScore(topic, position, score);
				topicScore += score;
			}
			
			scores.setTopicScore(topic, topicScore);
		}
		
		return scores;
	}
	
	/** Low-quality topics often have lots of unusually short words. */
	public TopicScores getWordLengthScores() {

		TopicScores scores = new TopicScores("word-length", numTopics, numTopWords);
		scores.wordScoresDefined = true;

		// Get the mean length

		double meanLength = 0.0;
		int totalWords = 0;

		for (int topic = 0; topic < numTopics; topic++) {
			for (int position = 0; position < topicTopWords[topic].length; position++) {
				// Some topics may not have all N words
				if (topicTopWords[topic][position] == null) { break; }
				meanLength += topicTopWords[topic][position].length();
				totalWords ++;
			}
		}

		meanLength /= totalWords;
		
		// Now calculate the standard deviation
		
		double lengthVariance = 0.0;

		for (int topic = 0; topic < numTopics; topic++) {
            for (int position = 0; position < topicTopWords[topic].length; position++) {
                if (topicTopWords[topic][position] == null) { break; }
				
				int length = topicTopWords[topic][position].length();

                lengthVariance += (length - meanLength) * (length - meanLength);
			}
		}
		lengthVariance /= (totalWords - 1);

		// Finally produce an overall topic score

		double lengthSD = Math.sqrt(lengthVariance);
		for (int topic = 0; topic < numTopics; topic++) {
            for (int position = 0; position < topicTopWords[topic].length; position++) {
                if (topicTopWords[topic][position] == null) { break; }
				
				int length = topicTopWords[topic][position].length();

				scores.addToTopicScore(topic, (length - meanLength) / lengthSD);
				scores.setTopicWordScore(topic, position, (length - meanLength) / lengthSD);
			}
		}

		return scores;
	}

	public TopicScores getCoherence() {
        TopicScores scores = new TopicScores("coherence", numTopics, numTopWords);
        scores.wordScoresDefined = true;

		for (int topic = 0; topic < numTopics; topic++) {
			int[][] matrix = topicCodocumentMatrices[topic];

			double topicScore = 0.0;

			for (int row = 0; row < numTopWords; row++) {
				double rowScore = 0.0;
				double minScore = 0.0;
				for (int col = 0; col < row; col++) {
					double score = Math.log( (matrix[row][col] + 1.0) / (matrix[col][col] + 1.0) );
					rowScore += score;
					if (score < minScore) { minScore = score; }
				}
				topicScore += rowScore;
				scores.setTopicWordScore(topic, row, minScore);
			}

			scores.setTopicScore(topic, topicScore);
		}
		
		return scores;
	}

	public TopicScores getRank1Percent() {
        TopicScores scores = new TopicScores("rank_1_docs", numTopics, numTopWords);

		int[] numRank1Documents = new int[numTopics];
		int[] numNonZeroDocuments = new int[numTopics];

		for (int doc = 0; doc < docTopicProportions.length; doc++) {
			//			StringBuilder out = new StringBuilder();
			//Formatter formatter = new Formatter(out, Locale.US);

			int maxTopic = -1;
			double maxTopicProb = 0.0;
			for (int topic = 0; topic < numTopics; topic++) {
				//formatter.format("%.4f ", docTopicProportions[doc][topic]);
				if (docTopicProportions[doc][topic] > 0.0) {
					numNonZeroDocuments[topic]++;
				}
				if (docTopicProportions[doc][topic] > maxTopicProb) {
					maxTopic = topic;
					maxTopicProb = docTopicProportions[doc][topic];
				}
			}
			//System.out.println(out);

			//assert(maxTopic != -1) : "Did not find a maximum topic";

			// Documents with length 0 can cause all probabilities to be 0.0
			if (maxTopic != -1) {
				numRank1Documents[maxTopic]++;
			}
		}

		for (int topic = 0; topic < numTopics; topic++) {
			scores.setTopicScore(topic, (double) numRank1Documents[topic] / numNonZeroDocuments[topic]);
		}

		return scores;
	}

	public String toString() {

		StringBuilder out = new StringBuilder();
		Formatter formatter = new Formatter(out, Locale.US);

		for (int topic = 0; topic < numTopics; topic++) {
			
			formatter.format("Topic %d", topic);

			for (TopicScores scores: diagnostics) {
				formatter.format("\t%s=%.4f", scores.name, scores.scores[topic]);
			}
			formatter.format("\n");

			for (int position = 0; position < topicTopWords[topic].length; position++) {
                if (topicTopWords[topic][position] == null) { break; }
				
				formatter.format("  %s", topicTopWords[topic][position]);
				for(TopicScores scores: diagnostics) {
					if (scores.wordScoresDefined) {
						formatter.format("\t%s=%.4f", scores.name, scores.topicWordScores[topic][position]);
					}
				}
				out.append("\n");
			}
		}
	
		return out.toString();
	}

	public String toXML() {

		int[] tokensPerTopic = model.tokensPerTopic;

		StringBuilder out = new StringBuilder();
		Formatter formatter = new Formatter(out, Locale.US);
		

		out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out.append("<model>\n");

		for (int topic = 0; topic < numTopics; topic++) {
			
			formatter.format("<topic id='%d'", topic);

			for (TopicScores scores: diagnostics) {
				formatter.format(" %s='%.4f'", scores.name, scores.scores[topic]);
			}
			out.append(">\n");

			TreeSet<IDSorter> sortedWords = topicSortedWords.get(topic);
                        
			// How many words should we report? Some topics may have fewer than
			//  the default number of words with non-zero weight.
			int limit = numTopWords;
			if (sortedWords.size() < numTopWords) { limit = sortedWords.size(); }

			double cumulativeProbability = 0.0;

			Iterator<IDSorter> iterator = sortedWords.iterator();
			for (int position=0; position < limit; position++) {
				IDSorter info = iterator.next();
				double probability = info.getWeight() / tokensPerTopic[topic];
				cumulativeProbability += probability;
				
				formatter.format("<word rank='%d' count='%.0f' prob='%.5f' cumulative='%.5f'", position+1, info.getWeight(), probability, cumulativeProbability);

				for(TopicScores scores: diagnostics) {
					if (scores.wordScoresDefined) {
						formatter.format(" %s='%.4f'", scores.name, scores.topicWordScores[topic][position]);
					}
				}
				formatter.format(">%s</word>\n", topicTopWords[topic][position]);
			}

			out.append("</topic>\n");
		}
		out.append("</model>\n");
	
		return out.toString();
	}

	public class TopicScores {
		public String name;
		public double[] scores;
		public double[][] topicWordScores;
		
		/** Some diagnostics have meaningful values for each word, others do not */
		public boolean wordScoresDefined = false;

		public TopicScores (String name, int numTopics, int numWords) {
			this.name = name;
			scores = new double[numTopics];
			topicWordScores = new double[numTopics][numWords];
		}

		public void setTopicScore(int topic, double score) {
			scores[topic] = score;
		}
		
		public void addToTopicScore(int topic, double score) {
			scores[topic] += score;
		}
		
		public void setTopicWordScore(int topic, int wordPosition, double score) {
			topicWordScores[topic][wordPosition] = score;
			wordScoresDefined = true;
		}
	}
	
	public static void main (String[] args) throws Exception {
		InstanceList instances = InstanceList.load(new File(args[0]));
		int numTopics = Integer.parseInt(args[1]);
		ParallelTopicModel model = new ParallelTopicModel(numTopics, 5.0, 0.01);
		model.addInstances(instances);
		model.setNumIterations(1000);
	
		model.estimate();

		TopicModelDiagnostics diagnostics = new TopicModelDiagnostics(model, 20);

		if (args.length == 3) {
			PrintWriter out = new PrintWriter(args[2]);
			out.println(diagnostics.toXML());
			out.close();
		}
	}
}