package cc.mallet.examples;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class RunCRFPipe {
	
	public RunCRFPipe(String trainingFilename) throws IOException {
		
		ArrayList<Pipe> pipes = new ArrayList<Pipe>();

		PrintWriter out = new PrintWriter("test.out");

		int[][] conjunctions = new int[3][];
		conjunctions[0] = new int[] { -1 };
		conjunctions[1] = new int[] { 1 };
		conjunctions[2] = new int[] { -2, -1 };

		pipes.add(new SimpleTaggerSentence2TokenSequence());
		//pipes.add(new FeaturesInWindow("PREV-", -1, 1));
		//pipes.add(new FeaturesInWindow("NEXT-", 1, 2));
		pipes.add(new OffsetConjunctions(conjunctions));
		pipes.add(new TokenTextCharSuffix("C1=", 1));
		pipes.add(new TokenTextCharSuffix("C2=", 2));
		pipes.add(new TokenTextCharSuffix("C3=", 3));
		pipes.add(new RegexMatches("CAPITALIZED", Pattern.compile("^\\p{Lu}.*")));
		pipes.add(new RegexMatches("STARTSNUMBER", Pattern.compile("^[0-9].*")));
		pipes.add(new RegexMatches("HYPHENATED", Pattern.compile(".*\\-.*")));
		pipes.add(new RegexMatches("DOLLARSIGN", Pattern.compile("\\$.*")));
		pipes.add(new TokenFirstPosition("FIRSTTOKEN"));
		pipes.add(new TokenSequence2FeatureVectorSequence());
		pipes.add(new SequencePrintingPipe(out));

		Pipe pipe = new SerialPipes(pipes);

		InstanceList trainingInstances = new InstanceList(pipe);

		trainingInstances.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(trainingFilename)))), Pattern.compile("^\\s*$"), true));

		out.close();
		
	}

	public static void main (String[] args) throws Exception {
		RunCRFPipe trainer = new RunCRFPipe(args[0]);

	}

}