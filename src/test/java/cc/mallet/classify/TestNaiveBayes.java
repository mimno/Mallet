/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.classify;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.types.*;
import cc.mallet.util.*;

public class TestNaiveBayes {

	@Test
	public void testNonTrained() {
		Alphabet fdict = new Alphabet();
		System.out.println("fdict.size=" + fdict.size());
		LabelAlphabet ldict = new LabelAlphabet();
		Multinomial.Estimator me1 = new Multinomial.LaplaceEstimator(fdict);
		Multinomial.Estimator me2 = new Multinomial.LaplaceEstimator(fdict);

		// Prior
		ldict.lookupIndex("sports");
		ldict.lookupIndex("politics");
		ldict.stopGrowth();
		System.out.println("ldict.size=" + ldict.size());
		Multinomial prior = new Multinomial(new double[]{.5, .5}, ldict);

		// Sports
		me1.increment("win", 5);
		me1.increment("puck", 5);
		me1.increment("team", 5);
		System.out.println("fdict.size=" + fdict.size());

		// Politics
		me2.increment("win", 5);
		me2.increment("speech", 5);
		me2.increment("vote", 5);

		Multinomial sports = me1.estimate();
		Multinomial politics = me2.estimate();

		// We must estimate from me1 and me2 after all data is incremented,
		// so that the "sports" multinomial knows the full dictionary size!

		Classifier c = new NaiveBayes(new Noop(fdict, ldict),
				prior,
				new Multinomial[]{sports, politics});

		Instance inst = c.getInstancePipe().instanceFrom(
				new Instance(new FeatureVector(fdict,
						new Object[]{"speech", "win"},
						new double[]{1, 1}),
						ldict.lookupLabel("politics"),
						null, null));
		System.out.println("inst.data = " + inst.getData());

		Classification cf = c.classify(inst);
		LabelVector l = (LabelVector) cf.getLabeling();
		//System.out.println ("l.size="+l.size());
		System.out.println("l.getBestIndex=" + l.getBestIndex());
		assertTrue(cf.getLabeling().getBestLabel()
				== ldict.lookupLabel("politics"));
		assertTrue(cf.getLabeling().getBestValue() > 0.6);
	}

	@Test
	public void testStringTrained() {
		String[] africaTraining = new String[]{
				"on the plains of africa the lions roar",
				"in swahili ngoma means to dance",
				"nelson mandela became president of south africa",
				"the saraha dessert is expanding"};
		String[] asiaTraining = new String[]{
				"panda bears eat bamboo",
				"china's one child policy has resulted in a surplus of boys",
				"tigers live in the jungle"};

		InstanceList instances =
				new InstanceList(
						new SerialPipes(new Pipe[]{
								new Target2Label(),
								new CharSequence2TokenSequence(),
								new TokenSequence2FeatureSequence(),
								new FeatureSequence2FeatureVector()}));

		instances.addThruPipe(new ArrayIterator(africaTraining, "africa"));
		instances.addThruPipe(new ArrayIterator(asiaTraining, "asia"));
		Classifier c = new NaiveBayesTrainer().train(instances);

		Classification cf = c.classify("nelson mandela never eats lions");
		assertTrue(cf.getLabeling().getBestLabel()
				== ((LabelAlphabet) instances.getTargetAlphabet()).lookupLabel("africa"));
	}

	@Test
	public void testRandomTrained() {
		InstanceList ilist = new InstanceList(new Randoms(1), 10, 2);
		Classifier c = new NaiveBayesTrainer().train(ilist);
		// test on the training data
		int numCorrect = 0;
		for (int i = 0; i < ilist.size(); i++) {
			Instance inst = ilist.get(i);
			Classification cf = c.classify(inst);
			cf.print();
			if (cf.getLabeling().getBestLabel() == inst.getLabeling().getBestLabel())
				numCorrect++;
		}
		System.out.println("Accuracy on training set = " + ((double) numCorrect) / ilist.size());
	}

	@Test
	public void testIncrementallyTrainedGrowingAlphabets() {
		System.out.println("testIncrementallyTrainedGrowingAlphabets");
		String[] args = new String[]{
				"src/test/resources/NaiveBayesData/learn/a",
				"src/test/resources/NaiveBayesData/learn/b"
		};

		File[] directories = new File[args.length];
		for (int i = 0; i < args.length; i++)
			directories[i] = new File(args[i]);

		SerialPipes instPipe =
				// MALLET pipeline for converting instances to feature vectors
				new SerialPipes(new Pipe[]{
						new Target2Label(),
						new Input2CharSequence(),
						//SKIP_HEADER only works for Unix
						//new CharSubsequence(CharSubsequence.SKIP_HEADER),
						new CharSequence2TokenSequence(),
						new TokenSequenceLowercase(),
						new TokenSequenceRemoveStopwords(),
						new TokenSequence2FeatureSequence(),
						new FeatureSequence2FeatureVector()});

		InstanceList instList = new InstanceList(instPipe);
		instList.addThruPipe(new
				FileIterator(directories, FileIterator.STARTING_DIRECTORIES));

		System.out.println("Training 1");
		NaiveBayesTrainer trainer = new NaiveBayesTrainer();
		NaiveBayes classifier = trainer.trainIncremental(instList);

		//instList.getDataAlphabet().stopGrowth();

		// incrementally train...
		String[] t2directories = {
				"src/test/resources/NaiveBayesData/learn/b"
		};

		System.out.println("data alphabet size " + instList.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList.getTargetAlphabet().size());
		InstanceList instList2 = new InstanceList(instPipe);
		instList2.addThruPipe(new
				FileIterator(t2directories, FileIterator.STARTING_DIRECTORIES));

		System.out.println("Training 2");

		System.out.println("data alphabet size " + instList2.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList2.getTargetAlphabet().size());

		NaiveBayes classifier2 = (NaiveBayes) trainer.trainIncremental(instList2);
	}

	@Test
	public void testIncrementallyTrained() {
		System.out.println("testIncrementallyTrained");
		String[] args = new String[]{
				"src/test/resources/NaiveBayesData/learn/a",
				"src/test/resources/NaiveBayesData/learn/b"
		};

		File[] directories = new File[args.length];
		for (int i = 0; i < args.length; i++)
			directories[i] = new File(args[i]);

		SerialPipes instPipe =
				// MALLET pipeline for converting instances to feature vectors
				new SerialPipes(new Pipe[]{
						new Target2Label(),
						new Input2CharSequence(),
						//SKIP_HEADER only works for Unix
						//new CharSubsequence(CharSubsequence.SKIP_HEADER),
						new CharSequence2TokenSequence(),
						new TokenSequenceLowercase(),
						new TokenSequenceRemoveStopwords(),
						new TokenSequence2FeatureSequence(),
						new FeatureSequence2FeatureVector()});

		InstanceList instList = new InstanceList(instPipe);
		instList.addThruPipe(new
				FileIterator(directories, FileIterator.STARTING_DIRECTORIES));

		System.out.println("Training 1");
		NaiveBayesTrainer trainer = new NaiveBayesTrainer();
		NaiveBayes classifier = (NaiveBayes) trainer.trainIncremental(instList);

		Classification initialClassification = classifier.classify("Hello Everybody");
		Classification initial2Classification = classifier.classify("Goodbye now");
		System.out.println("Initial Classification = ");
		initialClassification.print();
		initial2Classification.print();
		System.out.println("data alphabet " + classifier.getAlphabet());
		System.out.println("label alphabet " + classifier.getLabelAlphabet());


		// incrementally train...
		String[] t2directories = {
				"src/test/resources/NaiveBayesData/learn/b"
		};

		System.out.println("data alphabet size " + instList.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList.getTargetAlphabet().size());
		InstanceList instList2 = new InstanceList(instPipe);
		instList2.addThruPipe(new
				FileIterator(t2directories, FileIterator.STARTING_DIRECTORIES));

		System.out.println("Training 2");

		System.out.println("data alphabet size " + instList2.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList2.getTargetAlphabet().size());

		NaiveBayes classifier2 = (NaiveBayes) trainer.trainIncremental(instList2);


	}

	@Test
	public void testEmptyStringBug() {
		System.out.println("testEmptyStringBug");
		String[] args = new String[]{
				"src/test/resources/NaiveBayesData/learn/a",
				"src/test/resources/NaiveBayesData/learn/b"
		};

		File[] directories = new File[args.length];
		for (int i = 0; i < args.length; i++)
			directories[i] = new File(args[i]);

		SerialPipes instPipe =
				// MALLET pipeline for converting instances to feature vectors
				new SerialPipes(new Pipe[]{
						new Target2Label(),
						new Input2CharSequence(),
						//SKIP_HEADER only works for Unix
						//new CharSubsequence(CharSubsequence.SKIP_HEADER),
						new CharSequence2TokenSequence(),
						new TokenSequenceLowercase(),
						new TokenSequenceRemoveStopwords(),
						new TokenSequence2FeatureSequence(),
						new FeatureSequence2FeatureVector()});

		InstanceList instList = new InstanceList(instPipe);
		instList.addThruPipe(new
				FileIterator(directories, FileIterator.STARTING_DIRECTORIES));

		System.out.println("Training 1");
		NaiveBayesTrainer trainer = new NaiveBayesTrainer();
		NaiveBayes classifier = (NaiveBayes) trainer.trainIncremental(instList);

		Classification initialClassification = classifier.classify("Hello Everybody");
		Classification initial2Classification = classifier.classify("Goodbye now");
		System.out.println("Initial Classification = ");
		initialClassification.print();
		initial2Classification.print();
		System.out.println("data alphabet " + classifier.getAlphabet());
		System.out.println("label alphabet " + classifier.getLabelAlphabet());


		// test
		String[] t2directories = {
				"src/test/resources/NaiveBayesData/learn/b"
		};

		System.out.println("data alphabet size " + instList.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList.getTargetAlphabet().size());
		InstanceList instList2 = new InstanceList(instPipe);
		instList2.addThruPipe(new
				FileIterator(t2directories, FileIterator.STARTING_DIRECTORIES, true));

		System.out.println("Training 2");

		System.out.println("data alphabet size " + instList2.getDataAlphabet().size());
		System.out.println("target alphabet size " + instList2.getTargetAlphabet().size());

		NaiveBayes classifier2 = (NaiveBayes) trainer.trainIncremental(instList2);
		Classification secondClassification = classifier.classify("Goodbye now");
		secondClassification.print();

	}

}
