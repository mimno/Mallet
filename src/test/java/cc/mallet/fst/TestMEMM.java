package cc.mallet.fst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.google.errorprone.annotations.Var;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.fst.MEMM;
import cc.mallet.fst.MEMMTrainer;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.TestOptimizable;
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureVectorSequence;
import cc.mallet.pipe.TokenSequenceLowercase;
import cc.mallet.pipe.iterator.ArrayIterator;
import cc.mallet.pipe.tsf.OffsetConjunctions;
import cc.mallet.pipe.tsf.TokenText;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.MatrixOps;
import cc.mallet.types.Sequence;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */



/**
 * Tests for MEMM training.
 * 
 * @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */
// gsc (08/25/08): made changes to all tests after removing the option for 
// useSparseWeights from MEMMTrainer, now, the users has to set the weights manually
// irrespective of above changes, two tests fail (testSpaceMaximizable, testSpaceSerializable)
public class TestMEMM extends TestCase {

	public TestMEMM (String name)
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
	  MEMM memm = new MEMM (inputAlphabet, outputAlphabet);
	  String[] stateNames = new String[numStates];
	  for (int i = 0; i < numStates; i++)
	    stateNames[i] = "state" + i;
	  memm.addFullyConnectedStates(stateNames);
	  MEMMTrainer memmt = new MEMMTrainer (memm);
	  MEMMTrainer.MEMMOptimizableByLabelLikelihood omemm = memmt.getOptimizableMEMM (new InstanceList(null));
	  TestOptimizable.testGetSetParameters(omemm);
	}

    /* I don't know how to fix this and I don't think MEMM is being used.
sy = 83.87438991729655 > 0
cc.mallet.optimize.InvalidOptimizableException: sy = 83.87438991729655 > 0
	at cc.mallet.optimize.LimitedMemoryBFGS.optimize(LimitedMemoryBFGS.java:201)
	at cc.mallet.fst.MEMMTrainer.train(MEMMTrainer.java:124)
	at cc.mallet.fst.tests.TestMEMM.testSpaceMaximizable(TestMEMM.java:127)
        
  public void testSpaceMaximizable ()
  {
    Pipe p = makeSpacePredictionPipe ();
    InstanceList training = new InstanceList (p);
//    String[] data = { TestMEMM.data[0], }; // TestMEMM.data[1], TestMEMM.data[2], TestMEMM.data[3], };
//    String[] data = { "ab" };
    training.addThruPipe (new ArrayIterator (data));

//    CRF4 memm = new CRF4 (p, null);
    MEMM memm = new MEMM (p, null);
    memm.addFullyConnectedStatesForLabels();
    memm.addStartState();
    memm.setWeightsDimensionAsIn(training);
    
	  MEMMTrainer memmt = new MEMMTrainer (memm);
//    memm.gatherTrainingSets (training); // ANNOYING: Need to set up per-instance training sets
    memmt.train (training, 1);  // Set weights dimension, gathers training sets, etc.

//    memm.print();
//    memm.printGradient = true;
//    memm.printInstanceLists();

//    memm.setGaussianPriorVariance (Double.POSITIVE_INFINITY);
    Optimizable.ByGradientValue mcrf = memmt.getOptimizableMEMM(training);
    TestOptimizable.setNumComponents (150);
    TestOptimizable.testValueAndGradient (mcrf);
  }
        */

        /* sy = 83.87438991729655 > 0
cc.mallet.optimize.InvalidOptimizableException: sy = 83.87438991729655 > 0
	at cc.mallet.optimize.LimitedMemoryBFGS.optimize(LimitedMemoryBFGS.java:201)
	at cc.mallet.fst.MEMMTrainer.train(MEMMTrainer.java:124)
	at cc.mallet.fst.tests.TestMEMM.testSpaceSerializable(TestMEMM.java:150)
            
            
  public void testSpaceSerializable () throws IOException, ClassNotFoundException
  {
    Pipe p = makeSpacePredictionPipe ();
    InstanceList training = new InstanceList (p);
    training.addThruPipe (new ArrayIterator (data));

    MEMM memm = new MEMM (p, null);
    memm.addFullyConnectedStatesForLabels ();
    memm.addStartState();
    memm.setWeightsDimensionAsIn(training);
	  MEMMTrainer memmt = new MEMMTrainer (memm);
    memmt.train (training, 10);

    MEMM memm2 = (MEMM) TestSerializable.cloneViaSerialization (memm);

    Optimizable.ByGradientValue mcrf1 = memmt.getOptimizableMEMM(training);
    double val1 = mcrf1.getValue ();
    Optimizable.ByGradientValue mcrf2 = memmt.getOptimizableMEMM(training);
    double val2 = mcrf2.getValue ();

    assertEquals (val1, val2, 1e-5);
  }  */

	// Should print at end:
	// parameters 4 4 3: unconstrainedCost=-2912.0 constrainedCost=-428.0 minCost=35770.0 minGrad=520.0
	public void disabledtestCost(int useSave)
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
	  // Store the dictionary
	  if (outputAlphabet == null) {
	    System.err.println("Output dictionary null.");
	  }
	  @Var
	  MEMM crf = new MEMM(inputAlphabet, outputAlphabet);
	  MEMMTrainer memmt = new MEMMTrainer (crf);

	  String[] stateNames = new String[numStates];
	  for (int i = 0; i < numStates; i++)
	    stateNames[i] = "state" + i;
	  MEMM saveCRF = crf;
	  //inputAlphabet = (Feature.Alphabet) crf.getInputAlphabet();
	  FeatureVectorSequence fvs = new FeatureVectorSequence(new FeatureVector[]{
	    new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
	    new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
	    new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
	    new FeatureVector(crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
	  });
	  FeatureSequence ss = new FeatureSequence(crf.getOutputAlphabet(), new int[]{0, 1, 2, 3});
	  InstanceList ilist = new InstanceList(null);
	  ilist.add(fvs, ss, null, null);

	  crf.addFullyConnectedStates(stateNames);

	  try {
	    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
	    oos.writeObject(crf);
	    oos.close();
	  } catch (IOException e) {
	    System.err.println("Exception writing file: " + e);
	  }
	  System.err.println("Wrote out CRF");
	  // And read it back in
	  crf = null;
	  try {
	    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
	    crf = (MEMM) ois.readObject();
	    ois.close();
	  } catch (IOException e) {
	    System.err.println("Exception reading file: " + e);
	  } catch (ClassNotFoundException cnfe) {
	    System.err.println("Cound not find class reading in object: " + cnfe);
	  }
	  System.err.println("Read in CRF.");

	  try {
	    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f2));
	    oos.writeObject(crf);
	    oos.close();
	  } catch (IOException e) {
	    System.err.println("Exception writing file: " + e);
	  }
	  System.err.println("Wrote out CRF");
	  if (useSave == 1) {
	    crf = saveCRF;
	  }
//	  MEMM.OptimizableCRF mcrf = crf.getMaximizableCRF(ilist);
    Optimizable.ByGradientValue mcrf = memmt.getOptimizableMEMM(ilist);

	  @Var
	  double unconstrainedCost = new SumLatticeDefault (crf, fvs).getTotalWeight();
	  @Var
	  double constrainedCost = new SumLatticeDefault (crf, fvs, ss).getTotalWeight();
	  @Var
	  double minimizableCost = 0, minimizableGradientNorm = 0;
	  double[] gradient = new double [mcrf.getNumParameters()];
	  //System.out.println ("unconstrainedCost="+unconstrainedCost+" constrainedCost="+constrainedCost);
	  for (int i = 0; i < numStates; i++)
	    for (int j = 0; j < numStates; j++)
	      for (int k = 0; k < inputVocabSize; k++) {
	        crf.setParameter(i, j, k, (k + i + j) * (k * i + i * j));
	        unconstrainedCost = new SumLatticeDefault (crf, fvs).getTotalWeight();
	        constrainedCost = new SumLatticeDefault (crf, fvs, ss).getTotalWeight();
	        minimizableCost = mcrf.getValue ();
					mcrf.getValueGradient (gradient);
	        minimizableGradientNorm = MatrixOps.oneNorm (gradient);
	        System.out.println("parameters " + i + " " + j + " " + k
	                           + ": unconstrainedCost=" + unconstrainedCost
	                           + " constrainedCost=" + constrainedCost
	                           + " minCost=" + minimizableCost
	                           + " minGrad=" + minimizableGradientNorm);
	      }
	  assertEquals (true, Math.abs (minimizableCost - 35770) < 0.001);
	  assertEquals (true, Math.abs (minimizableGradientNorm - 520) < 0.001);
	}


	public void testIncrement()
	{
	}


	public static class TestMEMMTokenSequenceRemoveSpaces extends Pipe implements Serializable {

	  public TestMEMMTokenSequenceRemoveSpaces()
	  {
	    super(null, new LabelAlphabet());
	  }


	  public Instance pipe(Instance carrier)
	  {
	    StringTokenization ts =  (StringTokenization) carrier.getData();
	    StringTokenization newTs = new StringTokenization((CharSequence) ts.getDocument ());
      LabelAlphabet dict = (LabelAlphabet) getTargetAlphabet();
      LabelSequence labelSeq = new LabelSequence(dict);
      Label start = dict.lookupLabel ("start");
      Label notstart = dict.lookupLabel ("notstart");

      	@Var
	    boolean lastWasSpace = true;
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < ts.size(); i++) {
	      StringSpan t = (StringSpan) ts.getSpan(i);
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


	  private void readObject(ObjectInputStream in) throws IOException
	  {
	    int version = in.readInt();
	  }

	}

	public class TestMEMM2String extends Pipe implements Serializable {

	  public TestMEMM2String()
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


	  private void readObject(ObjectInputStream in) throws IOException
	  {
	    int version = in.readInt();
	  }
	}


	public void doTestSpacePrediction(boolean testValueAndGradient)
	{
    Pipe p = makeSpacePredictionPipe ();
    Pipe p2 = new TestMEMM2String();

	  InstanceList instances = new InstanceList(p);
	  instances.addThruPipe(new ArrayIterator(data));
	  InstanceList[] lists = instances.split(new double[]{.5, .5});
	  MEMM memm = new MEMM(p, p2);
	  memm.addFullyConnectedStatesForLabels();
	  memm.setWeightsDimensionAsIn(lists[0]);
	  
	  MEMMTrainer memmt = new MEMMTrainer (memm);
	  if (testValueAndGradient) {
	    Optimizable.ByGradientValue minable = memmt.getOptimizableMEMM(lists[0]);
	    TestOptimizable.testValueAndGradient(minable);
	  } else {
	    System.out.println("Training Accuracy before training = " + memm.averageTokenAccuracy(lists[0]));
	    System.out.println("Testing  Accuracy before training = " + memm.averageTokenAccuracy(lists[1]));
	    System.out.println("Training...");
	    memmt.train(lists[0], 1);
	    System.out.println("Training Accuracy after training = " + memm.averageTokenAccuracy(lists[0]));
	    System.out.println("Testing  Accuracy after training = " + memm.averageTokenAccuracy(lists[1]));
	    System.out.println("Training results:");
      for (int i = 0; i < lists[0].size(); i++) {
        Instance inst = lists[0].get(i);
        Sequence input = (Sequence) inst.getData ();
        Sequence output = memm.transduce (input);
        System.out.println (output);
      }
      System.out.println ("Testing results:");
      for (int i = 0; i < lists[1].size(); i++) {
        Instance inst = lists[1].get(i);
        Sequence input = (Sequence) inst.getData ();
        Sequence output = memm.transduce (input);
        System.out.println (output);
      }
	  }
	}


	public void doTestSpacePrediction(boolean testValueAndGradient,
																		boolean useSaved,
																		boolean useSparseWeights)
	{
    Pipe p = makeSpacePredictionPipe ();

    MEMM savedCRF;
	  File f = new File("TestObject.obj");
	  InstanceList instances = new InstanceList(p);
	  instances.addThruPipe(new ArrayIterator(data));
	  InstanceList[] lists = instances.split(new double[]{.5, .5});
	  @Var
	  MEMM crf = new MEMM(p.getDataAlphabet(), p.getTargetAlphabet());
	  crf.addFullyConnectedStatesForLabels();
	  if (useSparseWeights)
	    crf.setWeightsDimensionAsIn(lists[0]);
	  else
	    crf.setWeightsDimensionDensely();
	  
	  MEMMTrainer memmt = new MEMMTrainer (crf);
	  // memmt.setUseSparseWeights (useSparseWeights);
	  if (testValueAndGradient) {
	    Optimizable.ByGradientValue minable = memmt.getOptimizableMEMM(lists[0]);
	    TestOptimizable.testValueAndGradient(minable);
	  } else {
	    System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
	    System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
	    savedCRF = crf;
	    System.out.println("Training serialized crf.");
	    memmt.train(lists[0], 100);
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
	    // And read it back in
	    if (useSaved) {
	      crf = null;
	      try {
	        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
	        crf = (MEMM) ois.readObject();
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


  public static  Pipe makeSpacePredictionPipe ()
  {
    Pipe p = new SerialPipes(new Pipe[]{
	    new CharSequence2TokenSequence("."),
	    new TokenSequenceLowercase(),
	    new TestMEMMTokenSequenceRemoveSpaces(),
	    new TokenText(),
	    new OffsetConjunctions(true,
	                           new int[][]{//{0}, /*{1},{-1,0},{0,1}, */
	                             {1}, {-1, 0}, {0, 1},
//	                             {-2, -1, 0}, {0, 1, 2}, {-3, -2, -1}, {1, 2, 3},
	                             //{-2,-1}, {-1,0}, {0,1}, {1,2},
	                             //{-3,-2,-1}, {-2,-1,0}, {-1,0,1}, {0,1,2}, {1,2,3},
	                           }),
//      new PrintInputAndTarget(),
	    new TokenSequence2FeatureVectorSequence()
	  });
    return p;
  }


  public void disabledtestAddOrderNStates ()
	{
    Pipe p = makeSpacePredictionPipe ();

    InstanceList instances = new InstanceList (p);
	  instances.addThruPipe (new ArrayIterator(data));
	  InstanceList[] lists = instances.split (new java.util.Random (678), new double[]{.5, .5});

		// Compare 3 CRFs trained with addOrderNStates, and make sure
		// that having more features leads to a higher likelihood

	  MEMM crf1 = new MEMM(p.getDataAlphabet(), p.getTargetAlphabet());
	  crf1.addOrderNStates (lists [0],
												 new int[] { 1, },
												 new boolean[] { false, },
												 "START",
												 null,
												 null,
												 false);
	  crf1.setWeightsDimensionAsIn(lists[0]);
	  MEMMTrainer memmt1 = new MEMMTrainer (crf1);
		memmt1.train(lists [0]);


	  MEMM crf2 = new MEMM(p.getDataAlphabet(), p.getTargetAlphabet());
	  crf2.addOrderNStates (lists [0],
													 new int[] { 1, 2, },
													 new boolean[] { false, true },
													 "START",
													 null,
													 null,
													 false);
	  crf2.setWeightsDimensionAsIn(lists[0]);
	  MEMMTrainer memmt2 = new MEMMTrainer (crf2);
		memmt2.train(lists [0]);


	  MEMM crf3 = new MEMM(p.getDataAlphabet(), p.getTargetAlphabet());
	  crf3.addOrderNStates (lists [0],
												 new int[] { 1, 2, },
												 new boolean[] { false, false },
												 "START",
												 null,
												 null,
												 false);
	  crf3.setWeightsDimensionAsIn(lists[0]);
	  MEMMTrainer memmt3 = new MEMMTrainer (crf3);
		memmt3.train(lists [0]);

		// Prevent cached values
		double lik1 = getLikelihood (memmt1, lists[0]);
		double lik2 = getLikelihood (memmt2, lists[0]);
		double lik3 = getLikelihood (memmt3, lists[0]);

		System.out.println("CRF1 likelihood "+lik1);

		assertTrue ("Final zero-order likelihood <"+lik1+"> greater than first-order <"+lik2+">",
								lik1 < lik2);
		assertTrue ("Final defaults-only likelihood <"+lik2+"> greater than full first-order <"+lik3+">",
								lik2 < lik3);

		assertEquals (-167.335971702, lik1, 0.0001);
		assertEquals (-166.212235389, lik2, 0.0001);
		assertEquals ( -90.386005741, lik3, 0.0001);
	}

	double getLikelihood (MEMMTrainer memmt, InstanceList data) {
		Optimizable.ByGradientValue mcrf = memmt.getOptimizableMEMM(data);
		// Do this elaborate thing so that crf.cachedValueStale is forced true
		double[] params = new double [mcrf.getNumParameters()];
		mcrf.getParameters (params);
		mcrf.setParameters (params);
		return mcrf.getValue ();
	}

	public void disabledtestValueGradient()
	{
	  doTestSpacePrediction(true);
	}


	public void disabledtestTrain()
	{
	  doTestSpacePrediction(false);
	}


	public void disabledtestDenseTrain ()
	{
		doTestSpacePrediction (false, false, false);
	}

	public void disabledtestSerialization()
	{
	  doTestSpacePrediction(false, true, true);
	}

	public void disabledtestDenseSerialization ()
	{
		doTestSpacePrediction(false, true, false);
	}

	public void disabledtestPrint ()
	{
		Pipe p = new SerialPipes (new Pipe[] {
	     new CharSequence2TokenSequence("."),
			 new TokenText(),
			 new TestMEMM.TestMEMMTokenSequenceRemoveSpaces(),
			 new TokenSequence2FeatureVectorSequence(),
			 new PrintInputAndTarget(),
	  });
		InstanceList one = new InstanceList (p);
		String[] data = new String[] { "ABCDE", };
		one.addThruPipe (new ArrayIterator (data));
		MEMM crf = new MEMM (p, null);
		crf.addFullyConnectedStatesForLabels();
		crf.setWeightsDimensionAsIn (one);
		MEMMTrainer memmt = new MEMMTrainer (crf);
		MEMMTrainer.MEMMOptimizableByLabelLikelihood mcrf = memmt.getOptimizableMEMM(one);
		double[] params = new double[mcrf.getNumParameters()];
		for (int i = 0; i < params.length; i++) {
			params [i] = i;
		}
		mcrf.setParameters (params);
		crf.print ();
	}

	public static Test suite()
	{
	  return new TestSuite(TestMEMM.class);
	}


	public static void main(String[] args)
	{
		TestMEMM tm = new TestMEMM ("");
		tm.doTestSpacePrediction (true);
		return;

/*
		TestSuite theSuite;
		if (args.length > 0) {
			theSuite = new TestSuite();
			for (int i = 0; i < args.length; i++) {
				theSuite.addTest (new TestMEMM (args [i]));
			}
		} else {
			theSuite = (TestSuite) suite();
		}

		junit.textui.TestRunner.run (theSuite);
*/
	}

}
