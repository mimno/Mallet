/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst.tests;

import junit.framework.*;

import java.util.Random;
import java.util.regex.Pattern;
import java.io.*;

import cc.mallet.fst.*;
import cc.mallet.optimize.*;
import cc.mallet.optimize.tests.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

// TODO (gsc (08/25/08)): some tests fail because tests are using CRFTrainerByLabelLikelihood
// instead of CRFOptimizableByLabelLikelihood and CRFOptimizableByValueGradients
public class TestCRF extends TestCase {

	public TestCRF(String name)
	{
		super(name);
	}


	public static final String[] data = new String[]{
		"Free software is a matter of the users' freedom to run, copy, distribute, study, change and improve the software. More precisely, it refers to four kinds of freedom, for the users of the software.",
		"The freedom to run the program, for any purpose.",
		"The freedom to study how the program works, and adapt it to your needs.",
		"The freedom to redistribute copies so you can help your neighbor.",
		"The freedom to improve the program, and release your improvements to the public, so that the whole community benefits.",
		"A program is free software if users have all of these freedoms. Thus, you should be free to redistribute copies, either with or without modifications, either gratis or charging a fee for distribution, to anyone anywhere. Being free to do these things means (among other things) that you do not have to ask or pay for permission.",
		"You should also have the freedom to make modifications and use them privately in your own work or play, without even mentioning that they exist. If you do publish your changes, you should not be required to notify anyone in particular, or in any particular way.",
		"In order for the freedoms to make changes, and to publish improved versions, to be meaningful, you must have access to the source code of the program. Therefore, accessibility of source code is a necessary condition for free software.",
		"Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
		"In order for these freedoms to be real, they must be irrevocable as long as you do nothing wrong; if the developer of the software has the power to revoke the license, without your doing anything to give cause, the software is not free.",
		"However, certain kinds of rules about the manner of distributing free software are acceptable, when they don't conflict with the central freedoms. For example, copyleft (very simply stated) is the rule that when redistributing the program, you cannot add restrictions to deny other people the central freedoms. This rule does not conflict with the central freedoms; rather it protects them.",
		"Thus, you may have paid money to get copies of free software, or you may have obtained copies at no charge. But regardless of how you got your copies, you always have the freedom to copy and change the software, even to sell copies.",
		"Rules about how to package a modified version are acceptable, if they don't effectively block your freedom to release modified versions. Rules that ``if you make the program available in this way, you must make it available in that way also'' can be acceptable too, on the same condition. (Note that such a rule still leaves you the choice of whether to publish the program or not.) It is also acceptable for the license to require that, if you have distributed a modified version and a previous developer asks for a copy of it, you must send one.",
		"Sometimes government export control regulations and trade sanctions can constrain your freedom to distribute copies of programs internationally. Software developers do not have the power to eliminate or override these restrictions, but what they can and must do is refuse to impose them as conditions of use of the program. In this way, the restrictions will not affect activities and people outside the jurisdictions of these governments.",
		"Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
		"The GNU Project was launched in 1984 to develop a complete Unix-like operating system which is free software: the GNU system."
	};


	public void testGetSetParameters()
	{
		int inputVocabSize = 100;
		int numStates = 5;
		Alphabet inputAlphabet = new Alphabet();
		for (int i = 0; i < inputVocabSize; i++)
			inputAlphabet.lookupIndex("feature" + i);
		Alphabet outputAlphabet = new Alphabet();
		CRF crf = new CRF(inputAlphabet, outputAlphabet);
		String[] stateNames = new String[numStates];
		for (int i = 0; i < numStates; i++)
			stateNames[i] = "state" + i;
		crf.addFullyConnectedStates(stateNames);
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		Optimizable.ByGradientValue mcrf = crft.getOptimizableCRF(new InstanceList(null));
		TestOptimizable.testGetSetParameters(mcrf);
	}
	
	public void testSumLogProb ()
	{
		double w1 = Math.log(.2);
		double w2 = Math.log(.8);
		double s1 = Math.log(.2+.8);
		double s2 = Transducer.sumLogProb(w1, w2);
		assertEquals(s1, s2, 0.00001);
		w1 = Math.log(99999);
		w2 = Math.log(.0001);
		s1 = Math.log(99999.0001);
		s2 = Transducer.sumLogProb(w1, w2);
		assertEquals(s1, s2, 0.00001);

	}
	
	public void testSumLattice ()
	{
		int inputVocabSize = 1;
		int numStates = 2;
		Alphabet inputAlphabet = new Alphabet();
		for (int i = 0; i < inputVocabSize; i++)
			inputAlphabet.lookupIndex("feature" + i);
		Alphabet outputAlphabet = new Alphabet();

		CRF crf = new CRF(inputAlphabet, outputAlphabet);

		String[] stateNames = new String[numStates];
		for (int i = 0; i < numStates; i++)
			stateNames[i] = "state" + i;
		crf.addFullyConnectedStates(stateNames);

		crf.setWeightsDimensionDensely();
		crf.getState(0).setInitialWeight(1.0);
		crf.getState(1).setInitialWeight(Transducer.IMPOSSIBLE_WEIGHT);
		crf.getState(0).setFinalWeight(0.0);
		crf.getState(1).setFinalWeight(0.0);
		crf.setParameter(0, 0, 0, Transducer.IMPOSSIBLE_WEIGHT); // state0 self-transition
		crf.setParameter(0, 1, 0, 1.0); // state0->state1 
		crf.setParameter(1, 1, 0, 1.0); // state1 self-transition
		crf.setParameter(1, 0, 0, Transducer.IMPOSSIBLE_WEIGHT); // state1->state0 
		
		FeatureVectorSequence fvs = new FeatureVectorSequence(new FeatureVector[]{
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
		});
		
		SumLattice lattice = new SumLatticeDefault (crf, fvs, true);
		// We start in state0
		assertTrue (lattice.getGammaProbability(0, crf.getState(0)) == 1.0);
		assertTrue (lattice.getGammaProbability(0, crf.getState(1)) == 0.0);
		// We go to state1
		assertTrue (lattice.getGammaProbability(1, crf.getState(0)) == 0.0);
		assertTrue (lattice.getGammaProbability(1, crf.getState(1)) == 1.0);
		// And on through a self-transition 
		assertTrue (lattice.getXiProbability(1, crf.getState(1), crf.getState(1)) == 1.0);
		assertTrue (lattice.getXiProbability(1, crf.getState(1), crf.getState(0)) == 0.0);
		assertTrue ("Lattice weight = "+lattice.getTotalWeight(),	lattice.getTotalWeight() == 4.0);
		// Gammas at all times sum to 1.0
		for (int time = 0; time < lattice.length()-1; time++) {
			double gammasum = lattice.getGammaProbability(time, crf.getState(0)) + lattice.getGammaProbability(time, crf.getState(1));
			assertEquals("Gammas at time step "+time+" sum to "+gammasum, 1.0, gammasum, 0.0001);
		}
		// Xis at all times sum to 1.0
		for (int time = 0; time < lattice.length()-1; time++) {
			double xissum = lattice.getXiProbability(time, crf.getState(0), crf.getState(0)) + 
			lattice.getXiProbability(time, crf.getState(0), crf.getState(1)) + 
			lattice.getXiProbability(time, crf.getState(1), crf.getState(0)) + 
			lattice.getXiProbability(time, crf.getState(1), crf.getState(1));
			assertEquals("Xis at time step "+time+" sum to "+xissum, 1.0, xissum, 0.0001);
		}
	}

	public void testMaxLattice ()
	{
		int inputVocabSize = 1;
		int numStates = 2;
		Alphabet inputAlphabet = new Alphabet();
		for (int i = 0; i < inputVocabSize; i++)
			inputAlphabet.lookupIndex("feature" + i);
		Alphabet outputAlphabet = new Alphabet();

		CRF crf = new CRF(inputAlphabet, outputAlphabet);

		String[] stateNames = new String[numStates];
		for (int i = 0; i < numStates; i++)
			stateNames[i] = "state" + i;
		crf.addFullyConnectedStates(stateNames);

		crf.setWeightsDimensionDensely();
		crf.getState(0).setInitialWeight(1.0);
		crf.getState(1).setInitialWeight(Transducer.IMPOSSIBLE_WEIGHT);
		crf.getState(0).setFinalWeight(0.0);
		crf.getState(1).setFinalWeight(0.0);
		crf.setParameter(0, 0, 0, Transducer.IMPOSSIBLE_WEIGHT); // state0 self-transition
		crf.setParameter(0, 1, 0, 1.0); // state0->state1 
		crf.setParameter(1, 1, 0, 1.0); // state1 self-transition
		crf.setParameter(1, 0, 0, Transducer.IMPOSSIBLE_WEIGHT); // state1->state0 
		
		FeatureVectorSequence fvs = new FeatureVectorSequence(new FeatureVector[]{
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
				new FeatureVector((Alphabet) crf.getInputAlphabet(), new double[]{1}),
		});
		
		MaxLattice lattice = new MaxLatticeDefault (crf, fvs);
		Sequence<Transducer.State> viterbiPath = lattice.bestStateSequence();
		// We start in state0
		assertTrue (viterbiPath.get(0) == crf.getState(0));
		// We go to state1
		assertTrue (viterbiPath.get(1) == crf.getState(1));
		// And on through a self-transition to state1 again 
		assertTrue (viterbiPath.get(2) == crf.getState(1));
	}


	// Should print at end:
	// parameters 4 4 3: unconstrainedWeight=2912.0 constrainedWeight=428.0 maxWeight=35770.0 minGrad=520.0
	public void doTestCost(boolean useSave)
	{
		int inputVocabSize = 4;
		int numStates = 5;
		// Create a file to store the CRF
		File f = new File("TestObject.obj");
		File f2 = new File("TestObject2.obj");
		Alphabet inputAlphabet = new Alphabet();
		for (int i = 0; i < inputVocabSize; i++)
			inputAlphabet.lookupIndex("feature" + i);
		Alphabet outputAlphabet = new Alphabet();
		String[] stateNames = new String[numStates];
		for (int i = 0; i < numStates; i++) {
			stateNames[i] = "state" + i;
			outputAlphabet.lookupIndex(stateNames[i]);
		}
		
		CRF crf = new CRF(inputAlphabet, outputAlphabet);
		CRF saveCRF = crf;
		//inputAlphabet = (Feature.Alphabet) crf.getInputAlphabet();
		FeatureVectorSequence fvs = new FeatureVectorSequence(new FeatureVector[]{
				new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}),
				new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}),
				new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}),
				new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}),
		});
		FeatureSequence ss = new FeatureSequence(crf.getOutputAlphabet(), new int[]{0, 1, 2, 3});
		InstanceList ilist = new InstanceList(new Noop (inputAlphabet, outputAlphabet));
		ilist.add(fvs, ss, null, null);

		crf.addFullyConnectedStates(stateNames);
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crft.setUseSparseWeights(false);

		if (useSave) {
			try {
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
				oos.writeObject(crf);
				oos.close();
			} catch (IOException e) {
				System.err.println("Exception writing file: " + e);
			}
			System.err.println("Wrote out CRF");
			System.err.println("CRF parameters. hyperbolicPriorSlope: " + crft.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crft.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crft.getGaussianPriorVariance());
			// And read it back in
			crf = null;
			try {
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
				crf = (CRF) ois.readObject();
				ois.close();
			} catch (IOException e) {
				System.err.println("Exception reading file: " + e);
			} catch (ClassNotFoundException cnfe) {
				System.err.println("Cound not find class reading in object: " + cnfe);
			}
			System.err.println("Read in CRF.");
			System.err.println("CRF parameters. hyperbolicPriorSlope: " + crft.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crft.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crft.getGaussianPriorVariance());

			try {
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f2));
				oos.writeObject(crf);
				oos.close();
			} catch (IOException e) {
				System.err.println("Exception writing file: " + e);
			}
			System.err.println("Wrote out CRF");
			crf = saveCRF;
		}
		Optimizable.ByGradientValue mcrf = crft.getOptimizableCRF(ilist);
		double unconstrainedWeight = new SumLatticeDefault (crf, fvs).getTotalWeight();
		double constrainedWeight = new SumLatticeDefault (crf, fvs, ss).getTotalWeight();
		double optimizableValue = 0, gradientNorm = 0;
		double[] gradient = new double [mcrf.getNumParameters()];
		//System.out.println ("unconstrainedCost="+unconstrainedCost+" constrainedCost="+constrainedCost);
		for (int i = 0; i < numStates; i++)
			for (int j = 0; j < numStates; j++)
				for (int k = 0; k < inputVocabSize; k++) {
					crf.setParameter(i, j, k, (k + i + j) * (k * i + i * j));
					unconstrainedWeight = new SumLatticeDefault (crf, fvs).getTotalWeight();
					constrainedWeight = new SumLatticeDefault (crf, fvs, ss).getTotalWeight();
					optimizableValue = mcrf.getValue ();
					mcrf.getValueGradient (gradient);
					gradientNorm = MatrixOps.oneNorm (gradient);
					System.out.println("parameters "+ i + " " + j + " " + k
							+ ": unconstrainedWeight =" + unconstrainedWeight
							+ " constrainedWeight =" + constrainedWeight
							+ " optimizableValue =" + optimizableValue
							+ " gradientNorm =" + gradientNorm);
				}
		assertTrue("Value should be 35770 but is"+optimizableValue, Math.abs(optimizableValue + 35770) < 0.001);
		assertTrue(Math.abs(gradientNorm - 520) < 0.001);
	}

	public void testCost() {
		doTestCost(false);
	}

	public void testCostSerialized() {
		doTestCost(true);
	}

	public void testIncrement()
	{
	}


	public static class TestCRFTokenSequenceRemoveSpaces extends Pipe implements Serializable {

		public TestCRFTokenSequenceRemoveSpaces()
		{
			super(null, new Alphabet());
		}


		public Instance pipe(Instance carrier)
		{
			TokenSequence ts = (TokenSequence) carrier.getData();
			TokenSequence newTs = new TokenSequence();
			FeatureSequence labelSeq = new FeatureSequence(getTargetAlphabet());
			boolean lastWasSpace = true;
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < ts.size(); i++) {
				Token t = ts.get(i);
				if (t.getText().equals(" "))
					lastWasSpace = true;
				else {
					sb.append(t.getText());
					newTs.add(t);
					labelSeq.add(lastWasSpace ? "start" : "notstart");
					lastWasSpace = false;
				}
			}
			if (isTargetProcessing())
				carrier.setTarget(labelSeq);
			carrier.setData(newTs);
			carrier.setSource(sb.toString());
			return carrier;
		}


		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;


		private void writeObject(ObjectOutputStream out) throws IOException
		{
			out.writeInt(CURRENT_SERIAL_VERSION);
		}


		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
		{
			int version = in.readInt();
		}

	}

	public class TestCRF2String extends Pipe implements Serializable {

		public TestCRF2String()
		{
			super();
		}


		public Instance pipe(Instance carrier)
		{
			StringBuffer sb = new StringBuffer();
			String source = (String) carrier.getSource();
			Sequence as = (Sequence) carrier.getTarget();
			//int startLabelIndex = as.getAlphabet().lookupIndex("start");
			for (int i = 0; i < source.length(); i++) {
				System.out.println("target[" + i + "]=" + as.get(i).toString());
				if (as.get(i).toString().equals("start") && i != 0)
					sb.append(' ');
				sb.append(source.charAt(i));
			}
			carrier.setSource(sb.toString());
			System.out.println("carrier.getSource() = " + carrier.getSource());
			return carrier;
		}


		private static final long serialVersionUID = 1;
		private static final int CURRENT_SERIAL_VERSION = 0;


		private void writeObject(ObjectOutputStream out) throws IOException
		{
			out.writeInt(CURRENT_SERIAL_VERSION);
		}


		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
		{
			int version = in.readInt();
		}
	}

	public void testValueGradient()
	{
		doTestSpacePrediction(true);
	}


	public void testTrain()
	{
		doTestSpacePrediction(false);
	}

	public void doTestSpacePrediction(boolean testValueAndGradient)
	{
		Pipe p = makeSpacePredictionPipe ();
		Pipe p2 = new TestCRF2String();

		InstanceList instances = new InstanceList(p);
		instances.addThruPipe(new ArrayIterator(data));
		InstanceList[] lists = instances.split(new Random(1), new double[]{.5, .5});
		CRF crf = new CRF(p, p2);
		crf.addFullyConnectedStatesForLabels();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		if (testValueAndGradient) {
			Optimizable.ByGradientValue optable = crft.getOptimizableCRF(lists[0]);
			//TestOptimizable.testValueAndGradient(minable);
			double[] gradient = new double[optable.getNumParameters()];
			optable.getValueGradient(gradient);
			//TestOptimizable.testValueAndGradientInDirection(optable, gradient);
			//TestOptimizable.testValueAndGradientCurrentParameters(optable);
			TestOptimizable.testValueAndGradient(optable); // This tests at current parameters and at parameters purturbed toward the gradient
		} else {
			System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
			System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
			System.out.println("Training...");
			crft.trainIncremental(lists[0]);
			System.out.println("Training Accuracy after training = " + crf.averageTokenAccuracy(lists[0]));
			System.out.println("Testing  Accuracy after training = " + crf.averageTokenAccuracy(lists[1]));
			System.out.println("Training results:");
			for (int i = 0; i < lists[0].size(); i++) {
				Instance inst = lists[0].get(i);
				Sequence input = (Sequence) inst.getData ();
				Sequence output = crf.transduce (input);
				System.out.println (output);
			}
			System.out.println ("Testing results:");
			for (int i = 0; i < lists[1].size(); i++) {
				Instance inst = lists[1].get(i);
				Sequence input = (Sequence) inst.getData ();
				Sequence output = crf.transduce (input);
				System.out.println (output);
			}
		}
	}


	public void doTestSpacePrediction(boolean testValueAndGradient, 
			boolean useSaved,
			boolean useSparseWeights)
	{
		Pipe p = makeSpacePredictionPipe ();

		CRF savedCRF;
		File f = new File("TestObject.obj");
		InstanceList instances = new InstanceList(p);
		instances.addThruPipe(new ArrayIterator(data));
		InstanceList[] lists = instances.split(new double[]{.5, .5});
		CRF crf = new CRF(p.getDataAlphabet(), p.getTargetAlphabet());
		crf.addFullyConnectedStatesForLabels();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crft.setUseSparseWeights (useSparseWeights);
		if (testValueAndGradient) {
			Optimizable.ByGradientValue minable = crft.getOptimizableCRF(lists[0]);
			TestOptimizable.testValueAndGradient(minable);
		} else {
			System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
			System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
			savedCRF = crf;
			System.out.println("Training serialized crf.");
			crft.trainIncremental(lists[0]);
			double preTrainAcc = crf.averageTokenAccuracy(lists[0]);
			double preTestAcc = crf.averageTokenAccuracy(lists[1]);
			System.out.println("Training Accuracy after training = " + preTrainAcc);
			System.out.println("Testing  Accuracy after training = " + preTestAcc);
			try {
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
				oos.writeObject(crf);
				oos.close();
			} catch (IOException e) {
				System.err.println("Exception writing file: " + e);
			}
			System.err.println("Wrote out CRF");
			System.err.println("CRF parameters. hyperbolicPriorSlope: " + crft.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crft.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crft.getGaussianPriorVariance());
			// And read it back in
			if (useSaved) {
				crf = null;
				try {
					ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
					crf = (CRF) ois.readObject();
					ois.close();
				} catch (IOException e) {
					System.err.println("Exception reading file: " + e);
				} catch (ClassNotFoundException cnfe) {
					System.err.println("Cound not find class reading in object: " + cnfe);
				}
				System.err.println("Read in CRF.");
				crf = savedCRF;

				double postTrainAcc = crf.averageTokenAccuracy(lists[0]);
				double postTestAcc = crf.averageTokenAccuracy(lists[1]);
				System.out.println("Training Accuracy after saving = " + postTrainAcc);
				System.out.println("Testing  Accuracy after saving = " + postTestAcc);

				assertEquals(postTrainAcc, preTrainAcc, 0.0001);
				assertEquals(postTestAcc, preTestAcc, 0.0001);
			}
		}
	}


	private Pipe makeSpacePredictionPipe ()
	{
		Pipe p = new SerialPipes(new Pipe[]{
				new CharSequence2TokenSequence("."),
				new TokenSequenceLowercase(),
				new TestCRFTokenSequenceRemoveSpaces(),
				new TokenText(),
				new OffsetConjunctions(true,
						new int[][]{{0}, 
						{1}, {-1, 0}, 
						
						// Original test had this conjunction in it too
						//{1},{-1,0},{0,1}, 
						//{0, 1},

						// I'd like to comment out this next line to make it run faster, but then we'd need to adjust likelihood and accuracy test values. -akm 12/2007
						// TODO uncomment this line
						//{-2, -1, 0}, {0, 1, 2}, {-3, -2, -1}, {1, 2, 3},

						// (These were commented before...)
						//{-2,-1}, {-1,0}, {0,1}, {1,2},
						//{-3,-2,-1}, {-2,-1,0}, {-1,0,1}, {0,1,2}, {1,2,3},
				}),
//				new PrintInputAndTarget(),
				new TokenSequence2FeatureVectorSequence()
		});
		return p;
	}


	public void testAddOrderNStates ()
	{
		Pipe p = makeSpacePredictionPipe ();

		InstanceList instances = new InstanceList (p);
		instances.addThruPipe (new ArrayIterator(data));
		InstanceList[] lists = instances.split (new java.util.Random (678), new double[]{.5, .5});

		// Compare 3 CRFs trained with addOrderNStates, and make sure
		// that having more features leads to a higher likelihood

		CRF crf1 = new CRF(p.getDataAlphabet(), p.getTargetAlphabet());
		crf1.addOrderNStates (lists [0],
				new int[] { 1, },
				new boolean[] { false, },
				"START",
				null,
				null,
				false);
		new CRFTrainerByLabelLikelihood(crf1).trainIncremental(lists[0]);


		CRF crf2 = new CRF(p.getDataAlphabet(), p.getTargetAlphabet());
		crf2.addOrderNStates (lists [0],
				new int[] { 1, 2, },
				new boolean[] { false, true },
				"START",
				null,
				null,
				false);
		new CRFTrainerByLabelLikelihood(crf2).trainIncremental(lists[0]);


		CRF crf3 = new CRF(p.getDataAlphabet(), p.getTargetAlphabet());
		crf3.addOrderNStates (lists [0],
				new int[] { 1, 2, },
				new boolean[] { false, false },
				"START",
				null,
				null,
				false);
		new CRFTrainerByLabelLikelihood(crf3).trainIncremental(lists[0]);

		// Prevent cached values
		double lik1 = getLikelihood (crf1, lists[0]);
		double lik2 = getLikelihood (crf2, lists[0]);
		double lik3 = getLikelihood (crf3, lists[0]);

		System.out.println("CRF1 likelihood "+lik1);

		assertTrue ("Final zero-order likelihood <"+lik1+"> greater than first-order <"+lik2+">",
				lik1 < lik2);
		assertTrue ("Final defaults-only likelihood <"+lik2+"> greater than full first-order <"+lik3+">",
				lik2 < lik3);

		assertEquals (-167.2234457483949, lik1, 0.0001);
		assertEquals (-165.81326484466342, lik2, 0.0001);
		assertEquals (-90.37680146432787, lik3, 0.0001);
	}

	double getLikelihood (CRF crf, InstanceList data) {
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		Optimizable.ByGradientValue mcrf = crft.getOptimizableCRF (data);
		// Do this elaborate thing so that crf.cachedValueStale is forced true
		double[] params = new double [mcrf.getNumParameters()];
		mcrf.getParameters (params);
		mcrf.setParameters (params);
		return mcrf.getValue ();
	}

	public void testFrozenWeights ()
	{
		Pipe p = makeSpacePredictionPipe ();

		InstanceList instances = new InstanceList (p);
		instances.addThruPipe (new ArrayIterator (data));

		CRF crf1 = new CRF (p.getDataAlphabet (), p.getTargetAlphabet ());
		crf1.addFullyConnectedStatesForLabels ();
		CRFTrainerByLabelLikelihood crft1 = new CRFTrainerByLabelLikelihood (crf1);
		crft1.trainIncremental (instances);

		CRF crf2 = new CRF (p.getDataAlphabet (), p.getTargetAlphabet ());
		crf2.addFullyConnectedStatesForLabels ();
		// Freeze some weights, before training
		for (int i = 0; i < crf2.getWeights ().length; i += 2)
			crf2.freezeWeights (i);
		CRFTrainerByLabelLikelihood crft2 = new CRFTrainerByLabelLikelihood (crf2);
		crft2.trainIncremental (instances);

		SparseVector[] w = crf2.getWeights ();
		double[] b = crf2.getDefaultWeights ();
		for (int i = 0; i < w.length; i += 2) {
			assertEquals (0.0, b[i], 1e-10);
			for (int loc = 0; loc < w[i].numLocations (); loc++) {
				assertEquals (0.0, w[i].valueAtLocation (loc), 1e-10);
			}
		}

		// Check that the frozen weights has worse likelihood
		Optimizable.ByGradientValue optable1 = crft1.getOptimizableCRF (instances);
		Optimizable.ByGradientValue optable2 = crft2.getOptimizableCRF (instances);
		double val1 = optable1.getValue();
		double val2 = optable2.getValue ();
		assertTrue ("Error: Freezing weights does not harm log-likelihood!  Full "+val1+", Frozen "+val2, val1 > val2);
	}


	public void testDenseTrain ()
	{
		doTestSpacePrediction (false, false, false);
	}
	
	public void testTrainStochasticGradient ()
	{
		Pipe p = makeSpacePredictionPipe ();
		Pipe p2 = new TestCRF2String();

		InstanceList instances = new InstanceList(p);
		instances.addThruPipe(new ArrayIterator(data));
		InstanceList[] lists = instances.split(new double[]{.5, .5});
		CRF crf = new CRF(p, p2);
		crf.addFullyConnectedStatesForLabels();
		crf.setWeightsDimensionAsIn(lists[0], false);
		CRFTrainerByStochasticGradient crft = new CRFTrainerByStochasticGradient (crf, 0.0001);
		System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
		System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
		System.out.println("Training...");
		// either fixed learning rate or selected on a sample
		crft.setLearningRateByLikelihood(lists[0]);
		// crft.setLearningRate(0.01);
		crft.train(lists[0], 100);
		crf.print();
		System.out.println("Training Accuracy after training = " + crf.averageTokenAccuracy(lists[0]));
		System.out.println("Testing  Accuracy after training = " + crf.averageTokenAccuracy(lists[1]));
	}


	public void testSerialization()
	{
		doTestSpacePrediction(false, true, true);
	}

	public void testDenseSerialization ()
	{
		doTestSpacePrediction(false, true, false);
	}

	public void testTokenAccuracy ()
	{
		Pipe p = makeSpacePredictionPipe ();

		InstanceList instances = new InstanceList(p);
		instances.addThruPipe(new ArrayIterator(data));
		InstanceList[] lists = instances.split (new Random (777), new double[]{.5, .5});

		CRF crf = new CRF(p.getDataAlphabet(), p.getTargetAlphabet());
		crf.addFullyConnectedStatesForLabels();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crft.setUseSparseWeights (true);

		crft.trainIncremental (lists[0]);

		TokenAccuracyEvaluator eval = new TokenAccuracyEvaluator (lists, new String[] {"Train", "Test"});
		eval.evaluateInstanceList(crft, lists[1], "Test");

		assertEquals (0.9409, eval.getAccuracy ("Test"), 0.001);

	}


	public void testPrint ()
	{
		Pipe p = new SerialPipes (new Pipe[] {
				new CharSequence2TokenSequence("."),
				new TokenText(),
				new TestCRFTokenSequenceRemoveSpaces(),
				new TokenSequence2FeatureVectorSequence(),
				new PrintInputAndTarget(),
		});
		InstanceList one = new InstanceList (p);
		String[] data = new String[] { "ABCDE", };
		one.addThruPipe (new ArrayIterator (data));
		CRF crf = new CRF (p, null);
		crf.addFullyConnectedStatesForThreeQuarterLabels(one);
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crf.setWeightsDimensionAsIn (one, false);
		Optimizable mcrf = crft.getOptimizableCRF(one);
		double[] params = new double[mcrf.getNumParameters()];
		for (int i = 0; i < params.length; i++) {
			params [i] = i;
		}
		mcrf.setParameters (params);
		crf.print ();
	}

	public void testCopyStatesAndWeights ()
	{
		Pipe p = new SerialPipes (new Pipe[] {
				new CharSequence2TokenSequence("."),
				new TokenText(),
				new TestCRFTokenSequenceRemoveSpaces(),
				new TokenSequence2FeatureVectorSequence(),
				new PrintInputAndTarget(),
		});
		InstanceList one = new InstanceList (p);
		String[] data = new String[] { "ABCDE", };
		one.addThruPipe (new ArrayIterator (data));
		CRF crf = new CRF (p, null);
		crf.addFullyConnectedStatesForLabels();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crf.setWeightsDimensionAsIn (one, false);
		Optimizable.ByGradientValue mcrf = crft.getOptimizableCRF(one);
		double[] params = new double[mcrf.getNumParameters()];
		for (int i = 0; i < params.length; i++) {
			params [i] = i;
		}
		mcrf.setParameters (params);

		StringWriter out = new StringWriter ();
		crf.print (new PrintWriter (out, true));
		System.out.println ("------------- CRF1 -------------");
		crf.print();

		// Make a copy of this CRF
		CRF crf2 = new CRF (crf);
		
		StringWriter out2 = new StringWriter ();
		crf2.print (new PrintWriter (out2, true));
		System.out.println ("------------- CRF2 -------------");
		crf2.print();

		assertEquals (out.toString(), out2.toString ());

		double val1 = mcrf.getValue ();
		CRFTrainerByLabelLikelihood crft2 = new CRFTrainerByLabelLikelihood (crf2);
		double val2 = crft2.getOptimizableCRF(one).getValue ();
		assertEquals (val1, val2, 1e-5);
	}

	static String toy = "A a\nB b\nC c\nD d\nB b\nC c\n";

	public void testStartState ()
	{
		Pipe p = new SerialPipes (new Pipe[]{
				new LineGroupString2TokenSequence (),
				new TokenSequenceMatchDataAndTarget (Pattern.compile ("^(\\S+) (.*)"), 2, 1),
				new TokenSequenceParseFeatureString (false),
				new TokenText (),
				new TokenSequence2FeatureVectorSequence (true, false),
				new Target2LabelSequence (),
				new PrintInputAndTarget (),
		});

		InstanceList data = new InstanceList (p);
		data.addThruPipe (new LineGroupIterator (new StringReader (toy), Pattern.compile ("\n"), true));

		CRF crf = new CRF (p, null);
		crf.print();
		crf.addStatesForLabelsConnectedAsIn (data);
		crf.addStartState ();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);

		Optimizable.ByGradientValue maxable = crft.getOptimizableCRF (data);
		assertEquals (-1.3862, maxable.getValue (), 1e-4);

		crf = new CRF (p, null);
		crf.addOrderNStates (data, new int[] { 1 }, null, "A", null, null, false);
		crf.print();
		crft = new CRFTrainerByLabelLikelihood (crf);

		maxable = crft.getOptimizableCRF (data);
		assertEquals (-3.09104245335831, maxable.getValue (), 1e-4);
	}

	// Tests that setWeightsDimensionDensely respects featureSelections
	public void testDenseFeatureSelection ()
	{
		Pipe p = makeSpacePredictionPipe ();

		InstanceList instances = new InstanceList (p);
		instances.addThruPipe (new ArrayIterator(data));

		// Test that dense observations wights aren't added for "default-feature" edges.
		CRF crf1 = new CRF (p, null);
		crf1.addOrderNStates (instances, new int[] { 0 }, null, "start", null, null, true);
		CRFTrainerByLabelLikelihood crft1 = new CRFTrainerByLabelLikelihood (crf1);
		crft1.setUseSparseWeights (false);
		crft1.train (instances, 1); // Set weights dimension
		int nParams1 = crft1.getOptimizableCRF (instances).getNumParameters ();

		CRF crf2 = new CRF (p, null);
		crf2.addOrderNStates (instances, new int[] { 0, 1 }, new boolean[] {false, true}, "start", null, null, true);
		CRFTrainerByLabelLikelihood crft2 = new CRFTrainerByLabelLikelihood (crf2);
		crft2.setUseSparseWeights (false);
		crft2.train (instances, 1); // Set weights dimension
		int nParams2 = crft2.getOptimizableCRF (instances).getNumParameters ();

		assertEquals (nParams2, nParams1 + 4);

	}

	public void testXis ()
	{
		Pipe p = makeSpacePredictionPipe ();

		InstanceList instances = new InstanceList (p);
		instances.addThruPipe (new ArrayIterator(data));

		CRF crf1 = new CRF (p, null);
		crf1.addFullyConnectedStatesForLabels ();
		CRFTrainerByLabelLikelihood crft1 = new CRFTrainerByLabelLikelihood (crf1);
		crft1.train (instances, 10); // Let's get some parameters

		Instance inst = instances.get (0);
		Sequence input = (Sequence)inst.getData();
		SumLatticeDefault lattice = new SumLatticeDefault (crf1, input, (Sequence)inst.getTarget(), null, true);
		for (int ip = 0; ip < lattice.length()-1; ip++) {
			for (int i = 0; i < crf1.numStates (); i++) {
				Transducer.State state = crf1.getState (i);
				Transducer.TransitionIterator it = state.transitionIterator (input, ip);
				double gamma = lattice.getGammaProbability (ip, state);
				double xiSum = 0;
				while (it.hasNext()) {
					Transducer.State dest = it.nextState ();
					double xi = lattice.getXiProbability (ip, state, dest);
					xiSum += xi;
				}
				assertEquals (gamma, xiSum, 1e-5);
			}
		}
	}

	public static Test suite ()
	{
		return new TestSuite (TestCRF.class);
	}

	public void testStateAddWeights ()
	{
		Pipe p = makeSpacePredictionPipe (); // This used to be MEMM.makeSpacePredictionPipe(), but I don't know why -akm 12/2007
		InstanceList training = new InstanceList (p);
		training.addThruPipe (new ArrayIterator (data)); // This used to be MEMM.data, but I don't know why -akm 12/2007

		CRF crf = new CRF (p, null);
		crf.addFullyConnectedStatesForLabels ();
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
		crft.trainIncremental (training);

		// Check that the notstart state is used at test time
		Sequence input = (Sequence) training.get (0).getData ();
		Sequence output = new MaxLatticeDefault(crf, input).bestOutputSequence();

		boolean notstartFound = false;
		for (int i = 0; i < output.size(); i++) {
			if (output.get(i).toString().equals ("notstart")) {
				notstartFound = true;
			}
		}
		System.err.println (output.toString());
		assertTrue (notstartFound);

		// Now add -infinite weight onto a transition, and make sure that it's honored.
		CRF.State state = crf.getState ("notstart");
		int widx = crf.getWeightsIndex ("BadBad");
		int numFeatures = crf.getInputAlphabet().size();
		SparseVector w = new SparseVector (new double[numFeatures]);
		w.setAll (Double.NEGATIVE_INFINITY);
		crf.setWeights (widx, w);

		state.addWeight (0, "BadBad");
		state.addWeight (1, "BadBad");

		// Verify that this effectively prevents the notstart state from being used
		output = new MaxLatticeDefault(crf, input).bestOutputSequence();
		notstartFound = false;
		for (int i = 0; i < output.size() - 1; i++) {
			if (output.get(i).toString().equals ("notstart")) {
				notstartFound = true;
			}
		}
		assertTrue (!notstartFound);
	}

	private static String oldCrfFile = "test/edu/umass/cs/mallet/base/fst/crf.cnl03.ser.gz";
	private static String testString = "John NNP B-NP O\nDoe NNP I-NP O\nsaid VBZ B-VP O\nhi NN B-NP O\n";

	public void skiptestOldCrf ()
	{
		CRF crf = (CRF) FileUtils.readObject (new File (oldCrfFile));
		Instance inst = crf.getInputPipe().instanceFrom(new Instance (testString, null, null, null));
		Sequence output = crf.transduce ((Sequence) inst.getData ());
		String std = output.toString ();
		assertEquals (" B-PER I-PER O O", std);
	}

	public static void main(String[] args)
	{
		TestSuite theSuite;
		if (args.length > 0) {
			theSuite = new TestSuite();
			for (int i = 0; i < args.length; i++) {
				theSuite.addTest (new TestCRF (args [i]));
			}
		} else {
			theSuite = (TestSuite) suite();
		}

		junit.textui.TestRunner.run (theSuite);
	}

}
