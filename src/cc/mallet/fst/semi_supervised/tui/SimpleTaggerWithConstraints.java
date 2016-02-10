/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised.tui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cc.mallet.fst.CRF;
import cc.mallet.fst.MaxLatticeDefault;
import cc.mallet.fst.MultiSegmentationEvaluator;
import cc.mallet.fst.NoopTransducerTrainer;
import cc.mallet.fst.SimpleTagger.SimpleTaggerSentence2FeatureVectorSequence;
import cc.mallet.fst.TokenAccuracyEvaluator;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.TransducerEvaluator;
import cc.mallet.fst.TransducerTrainer;
import cc.mallet.fst.semi_supervised.CRFTrainerByGE;
import cc.mallet.fst.semi_supervised.FSTConstraintUtil;
import cc.mallet.fst.semi_supervised.constraints.GEConstraint;
import cc.mallet.fst.semi_supervised.constraints.OneLabelKLGEConstraints;
import cc.mallet.fst.semi_supervised.constraints.OneLabelL2RangeGEConstraints;
import cc.mallet.fst.semi_supervised.pr.CRFTrainerByPR;
import cc.mallet.fst.semi_supervised.pr.constraints.OneLabelL2IndPRConstraints;
import cc.mallet.fst.semi_supervised.pr.constraints.PRConstraint;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Maths;

/**
 * Version of SimpleTagger that trains CRFs with expectation constraints
 * rather than labeled data.
 * 
 * This class's main method trains, tests, or runs a generic CRF-based
 * sequence tagger.
 * <p>
 * Training and test files consist of blocks of lines, one block for each instance, 
 * separated by blank lines. Each block of lines should have the first form 
 * specified for the input of {@link SimpleTaggerSentence2FeatureVectorSequence}. 
 * A variety of command line options control the operation of the main program, as
 * described in the comments for {@link #main main}.
 *
 * @author Gregory Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 * @version 1.0
 */
public class SimpleTaggerWithConstraints
{
  private static Logger logger =
    MalletLogger.getLogger(SimpleTaggerWithConstraints.class.getName());

  /**
   * No <code>SimpleTagger</code> objects allowed.
   */
  private SimpleTaggerWithConstraints() {}

  private static final CommandOption.Double gaussianVarianceOption = new CommandOption.Double
    (SimpleTaggerWithConstraints.class, "gaussian-variance", "DECIMAL", true, 10.0,
     "The gaussian prior variance used for training.", null);

  private static final CommandOption.Double qGaussianVarianceOption = new CommandOption.Double
  (SimpleTaggerWithConstraints.class, "q-gaussian-variance", "DECIMAL", true, 10.0,
   "The gaussian prior variance used in the E-step for PR training.", null);
  
  private static final CommandOption.Boolean trainOption = new CommandOption.Boolean
    (SimpleTaggerWithConstraints.class, "train", "true|false", true, false,
     "Whether to train", null);

  private static final CommandOption.String testOption = new CommandOption.String
    (SimpleTaggerWithConstraints.class, "test", "lab or seg=start-1.continue-1,...,start-n.continue-n",
     true, null,
     "Test measuring labeling or segmentation (start-i, continue-i) accuracy", null);

  private static final CommandOption.File modelOption = new CommandOption.File
    (SimpleTaggerWithConstraints.class, "model-file", "FILENAME", true, null,
     "The filename for reading (train/run) or saving (train) the model.", null);

  private static final CommandOption.Double trainingFractionOption = new CommandOption.Double
    (SimpleTaggerWithConstraints.class, "training-proportion", "DECIMAL", true, 0.5,
     "Fraction of data to use for training in a random split.", null);

  private static final CommandOption.Integer randomSeedOption = new CommandOption.Integer
    (SimpleTaggerWithConstraints.class, "random-seed", "INTEGER", true, 0,
     "The random seed for randomly selecting a proportion of the instance list for training", null);

  private static final CommandOption.IntegerArray ordersOption = new CommandOption.IntegerArray
    (SimpleTaggerWithConstraints.class, "orders", "COMMA-SEP-DECIMALS", true, new int[]{1},
     "List of label Markov orders (main and backoff) ", null);

  private static final CommandOption.String forbiddenOption = new CommandOption.String(
      SimpleTaggerWithConstraints.class, "forbidden", "REGEXP", true,
      "\\s", "label1,label2 transition forbidden if it matches this", null);

  private static final CommandOption.String allowedOption = new CommandOption.String(
      SimpleTaggerWithConstraints.class, "allowed", "REGEXP", true,
      ".*", "label1,label2 transition allowed only if it matches this", null);

  private static final CommandOption.String defaultOption = new CommandOption.String(
      SimpleTaggerWithConstraints.class, "default-label", "STRING", true, "O",
      "Label for initial context and uninteresting tokens", null);
  
  private static final CommandOption.String penaltyOption = new CommandOption.String(
      SimpleTaggerWithConstraints.class, "penalty", "kl|l2", true, "l2",
      "penalty function for constraint violation.", null);
  
  private static final CommandOption.String learningOption = new CommandOption.String(
      SimpleTaggerWithConstraints.class, "learning", "ge|pr", true, "ge",
      "Learning method to use.", null);

  private static final CommandOption.Integer iterationsOption = new CommandOption.Integer(
      SimpleTaggerWithConstraints.class, "iterations", "INTEGER", true, 500,
      "Number of training iterations", null);

  private static final CommandOption.Boolean viterbiOutputOption = new CommandOption.Boolean(
      SimpleTaggerWithConstraints.class, "viterbi-output", "true|false", true, false,
      "Print Viterbi periodically during training", null);

  /*
  private static final CommandOption.Boolean connectedOption = new CommandOption.Boolean(
      SemiSupSimpleTagger.class, "fully-connected", "true|false", true, true,
      "Include all allowed transitions, even those not in training data", null);
  */
  
  /*
  private static final CommandOption.String weightsOption = new CommandOption.String(
      SemiSupSimpleTagger.class, "weights", "sparse|some-dense|dense", true, "some-dense",
      "Use sparse, some dense (using a heuristic), or dense features on transitions.", null);
  */

  private static final CommandOption.Boolean continueTrainingOption = new CommandOption.Boolean(
      SimpleTaggerWithConstraints.class, "continue-training", "true|false", false, false,
      "Continue training from model specified by --model-file", null);

  private static final CommandOption.Integer nBestOption = new CommandOption.Integer(
      SimpleTaggerWithConstraints.class, "n-best", "INTEGER", true, 1,
      "How many answers to output", null);

  private static final CommandOption.Integer cacheSizeOption = new CommandOption.Integer(
      SimpleTaggerWithConstraints.class, "cache-size", "INTEGER", true, 100000,
      "How much state information to memoize in n-best decoding", null);

  private static final CommandOption.Boolean includeInputOption = new CommandOption.Boolean(
          SimpleTaggerWithConstraints.class, "include-input", "true|false", true, false,
     "Whether to include the input features when printing decoding output", null);
  
  private static final CommandOption.Integer numThreads = new CommandOption.Integer(
      SimpleTaggerWithConstraints.class, "threads", "INTEGER", true, 1,
      "Number of threads to use for CRF training.", null);
  
  private static final CommandOption.Integer numResets = new CommandOption.Integer(
      SimpleTaggerWithConstraints.class, "resets", "INTEGER", true, 4,
      "Number of L-BFGS resets to use.", null);


  private static final CommandOption.List commandOptions =
    new CommandOption.List (
        "Training, testing and running a generic tagger.",
        new CommandOption[] {
          gaussianVarianceOption,
          qGaussianVarianceOption,
          trainOption,
          iterationsOption,
          testOption,
          trainingFractionOption,
          modelOption,
          randomSeedOption,
          ordersOption,
          forbiddenOption,
          allowedOption,
          defaultOption,
          viterbiOutputOption,
          //connectedOption,
          //weightsOption,
          penaltyOption,
          learningOption,
          continueTrainingOption,
          nBestOption,
          cacheSizeOption,
          includeInputOption,
          numThreads,
          numResets
        });

  /**
   * Create and train a CRF model from the given training data,
   * optionally testing it on the given test data.
   *
   * @param training training data
   * @param testing test data (possibly <code>null</code>)
   * @param constraints constraints
   * @param crf model
   * @param eval accuracy evaluator (possibly <code>null</code>)
   * @param iterations number of training iterations
   * @param var Gaussian prior variance
   * @param resets Number of resets.
   * @return the trained model
   */
  public static CRF trainGE(InstanceList training, InstanceList testing,
      ArrayList<GEConstraint> constraints, CRF crf,
      TransducerEvaluator eval, int iterations, double var, int resets) {

    logger.info("Training on " + training.size() + " instances");
    if (testing != null)
      logger.info("Testing on " + testing.size() + " instances");
    
    assert(numThreads.value > 0);
    
    CRFTrainerByGE trainer = new CRFTrainerByGE(crf,constraints,numThreads.value);
    if (eval != null) trainer.addEvaluator(eval);
    trainer.setGaussianPriorVariance(var);
    trainer.setNumResets(resets);
    trainer.train(training,iterations);

    return crf;
  }
  
  /**
   * Create and train a CRF model from the given training data,
   * optionally testing it on the given test data.
   *
   * @param training training data
   * @param testing test data (possibly <code>null</code>)
   * @param constraints constraints
   * @param crf model
   * @param eval accuracy evaluator (possibly <code>null</code>)
   * @param iterations number of training iterations
   * @param var Gaussian prior variance
   * @return the trained model
   */
  public static CRF trainPR(InstanceList training, InstanceList testing,
      ArrayList<PRConstraint> constraints, CRF crf,
      TransducerEvaluator eval, int iterations, double var) {

    logger.info("Training on " + training.size() + " instances");
    if (testing != null)
      logger.info("Testing on " + testing.size() + " instances");
    
    assert(numThreads.value > 0);
    
    CRFTrainerByPR trainer = new CRFTrainerByPR(crf,constraints,numThreads.value);
    trainer.addEvaluator(eval);
    trainer.setPGaussianPriorVariance(var);
    trainer.train(training,iterations,iterations);

    return crf;
  }
  
  public static CRF getCRF(InstanceList training, int[] orders, String defaultLabel, String forbidden, String allowed, boolean connected) { 
    Pattern forbiddenPat = Pattern.compile(forbidden);
    Pattern allowedPat = Pattern.compile(allowed);
    CRF crf = new CRF(training.getPipe(), (Pipe)null);
    String startName = crf.addOrderNStates(training, orders, null,
        defaultLabel, forbiddenPat, allowedPat, connected);
    for (int i = 0; i < crf.numStates(); i++)
      crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
    crf.getState(startName).setInitialWeight(0.0);
    crf.setWeightsDimensionDensely();
    return crf;
  }

  /**
   * Test a transducer on the given test data, evaluating accuracy
   * with the given evaluator
   *
   * @param model a <code>Transducer</code>
   * @param eval accuracy evaluator
   * @param testing test data
   */
  public static void test(TransducerTrainer tt, TransducerEvaluator eval,
      InstanceList testing)
  {
    eval.evaluateInstanceList(tt, testing, "Testing");
  }

  /**
   * Apply a transducer to an input sequence to produce the k highest-scoring
   * output sequences.
   *
   * @param model the <code>Transducer</code>
   * @param input the input sequence
   * @param k the number of answers to return
   * @return array of the k highest-scoring output sequences
   */
  public static Sequence[] apply(Transducer model, Sequence input, int k)
  {
    Sequence[] answers;
    if (k == 1) {
      answers = new Sequence[1];
      answers[0] = model.transduce (input);
    }
    else {
      MaxLatticeDefault lattice =
              new MaxLatticeDefault (model, input, null, cacheSizeOption.value());

      answers = lattice.bestOutputSequences(k).toArray(new Sequence[0]);
    }
    return answers;
  }

  /**
   * Command-line wrapper to train, test, or run a generic CRF-based tagger.
   *
   * @param args the command line arguments. Options (shell and Java quoting should be added as needed):
   *<dl>
   *<dt><code>--help</code> <em>boolean</em></dt>
   *<dd>Print this command line option usage information.  Give <code>true</code> for longer documentation. Default is <code>false</code>.</dd>
   *<dt><code>--prefix-code</code> <em>Java-code</em></dt>
   *<dd>Java code you want run before any other interpreted code.  Note that the text is interpreted without modification, so unlike some other Java code options, you need to include any necessary 'new's. Default is null.</dd>
   *<dt><code>--gaussian-variance</code> <em>positive-number</em></dt>
   *<dd>The Gaussian prior variance used for training. Default is 10.0.</dd>
   *<dt><code>--train</code> <em>boolean</em></dt>
   *<dd>Whether to train. Default is <code>false</code>.</dd>
   *<dt><code>--iterations</code> <em>positive-integer</em></dt>
   *<dd>Number of training iterations. Default is 500.</dd>
   *<dt><code>--test</code> <code>lab</code> or <code>seg=</code><em>start-1</em><code>.</code><em>continue-1</em><code>,</code>...<code>,</code><em>start-n</em><code>.</code><em>continue-n</em></dt>
   *<dd>Test measuring labeling or segmentation (<em>start-i</em>, <em>continue-i</em>) accuracy. Default is no testing.</dd>
   *<dt><code>--training-proportion</code> <em>number-between-0-and-1</em></dt>
   *<dd>Fraction of data to use for training in a random split. Default is 0.5.</dd>
   *<dt><code>--model-file</code> <em>filename</em></dt>
   *<dd>The filename for reading (train/run) or saving (train) the model. Default is null.</dd>
   *<dt><code>--random-seed</code> <em>integer</em></dt>
   *<dd>The random seed for randomly selecting a proportion of the instance list for training Default is 0.</dd>
   *<dt><code>--orders</code> <em>comma-separated-integers</em></dt>
   *<dd>List of label Markov orders (main and backoff)  Default is 1.</dd>
   *<dt><code>--forbidden</code> <em>regular-expression</em></dt>
   *<dd>If <em>label-1</em><code>,</code><em>label-2</em> matches the expression, the corresponding transition is forbidden. Default is <code>\\s</code> (nothing forbidden).</dd>
   *<dt><code>--allowed</code> <em>regular-expression</em></dt>
   *<dd>If <em>label-1</em><code>,</code><em>label-2</em> does not match the expression, the corresponding expression is forbidden. Default is <code>.*</code> (everything allowed).</dd>
   *<dt><code>--default-label</code> <em>string</em></dt>
   *<dd>Label for initial context and uninteresting tokens. Default is <code>O</code>.</dd>
   *<dt><code>--viterbi-output</code> <em>boolean</em></dt>
   *<dd>Print Viterbi periodically during training. Default is <code>false</code>.</dd>
   *<dt><code>--fully-connected</code> <em>boolean</em></dt>
   *<dd>Include all allowed transitions, even those not in training data. Default is <code>true</code>.</dd>
   *<dt><code>--weights</code> <em>sparse|some-dense|dense</em></dt>
   *<dd>Create sparse, some dense (using a heuristic), or dense features on transitions. Default is <code>some-dense</code>.</dd>
   *<dt><code>--n-best</code> <em>positive-integer</em></dt>
   *<dd>Number of answers to output when applying model. Default is 1.</dd>
   *<dt><code>--include-input</code> <em>boolean</em></dt>
   *<dd>Whether to include input features when printing decoding output. Default is <code>false</code>.</dd>
   *<dt><code>--threads</code> <em>positive-integer</em></dt>
   *<dd>Number of threads for CRF training. Default is 1.</dd>
   *</dl>
   * Remaining arguments:
   *<ul>
   *<li><em>training-data-file</em> if training </li>
   *<li><em>training-and-test-data-file</em>, if training and testing with random split</li>
   *<li><em>training-data-file</em> <em>test-data-file</em> if training and testing from separate files</li>
   *<li><em>test-data-file</em> if testing</li>
   *<li><em>input-data-file</em> if applying to new data (unlabeled)</li>
   *</ul>
   * @exception Exception if an error occurs
   */
  public static void main (String[] args) throws Exception
  {
    long startTime = System.currentTimeMillis();
    
    Reader trainingFile = null, testFile = null;
    Reader constraintsFile = null;
    InstanceList trainingData = null, testData = null;
    int restArgs = commandOptions.processOptions(args);
    if (restArgs == args.length)
    {
      commandOptions.printUsage(true);
      throw new IllegalArgumentException("Missing data file(s)");
    }
    if (trainOption.value)
    {
      trainingFile = new FileReader(new File(args[restArgs]));
      if (testOption.value != null) {
        testFile = new FileReader(new File(args[restArgs+1]));
        constraintsFile = new FileReader(new File(args[restArgs+2]));
      }
      else {
        constraintsFile = new FileReader(new File(args[restArgs+1]));
      }
    } else 
      testFile = new FileReader(new File(args[restArgs]));

    Pipe p = null;
    CRF crf = null;
    TransducerEvaluator eval = null;
    if (continueTrainingOption.value || !trainOption.value) {
      if (modelOption.value == null)
      {
        commandOptions.printUsage(true);
        throw new IllegalArgumentException("Missing model file option");
      }
      ObjectInputStream s =
        new ObjectInputStream(new FileInputStream(modelOption.value));
      crf = (CRF) s.readObject();
      s.close();
      p = crf.getInputPipe();
    }
    else {
      p = new SimpleTaggerSentence2FeatureVectorSequence();
      p.getTargetAlphabet().lookupIndex(defaultOption.value);
    }


    if (trainOption.value)
    {
      p.setTargetProcessing(true);
      trainingData = new InstanceList(p);
      trainingData.addThruPipe(
          new LineGroupIterator(trainingFile,
            Pattern.compile("^\\s*$"), true));
      logger.info
        ("Number of features in training data: "+p.getDataAlphabet().size());
      if (testOption.value != null)
      {
        if (testFile != null)
        {
          testData = new InstanceList(p);
          testData.addThruPipe(
              new LineGroupIterator(testFile,
                Pattern.compile("^\\s*$"), true));
        } else
        {
          Random r = new Random (randomSeedOption.value);
          InstanceList[] trainingLists =
            trainingData.split(
                r, new double[] {trainingFractionOption.value,
                  1-trainingFractionOption.value});
          trainingData = trainingLists[0];
          testData = trainingLists[1];
        }
      }
    } else if (testOption.value != null)
    {
      p.setTargetProcessing(true);
      testData = new InstanceList(p);
      testData.addThruPipe(
          new LineGroupIterator(testFile,
            Pattern.compile("^\\s*$"), true));
    } else
    {
      p.setTargetProcessing(false);
      testData = new InstanceList(p);
      testData.addThruPipe(
          new LineGroupIterator(testFile,
            Pattern.compile("^\\s*$"), true));
    }
    logger.info ("Number of predicates: "+p.getDataAlphabet().size());
    
    
    if (testOption.value != null)
    {
      if (testOption.value.startsWith("lab"))
        eval = new TokenAccuracyEvaluator(new InstanceList[] {trainingData, testData}, new String[] {"Training", "Testing"});
      else if (testOption.value.startsWith("seg="))
      {
        String[] pairs = testOption.value.substring(4).split(",");
        if (pairs.length < 1)
        {
          commandOptions.printUsage(true);
          throw new IllegalArgumentException(
              "Missing segment start/continue labels: " + testOption.value);
        }
        String startTags[] = new String[pairs.length];
        String continueTags[] = new String[pairs.length];
        for (int i = 0; i < pairs.length; i++)
        {
          String[] pair = pairs[i].split("\\.");
          if (pair.length != 2)
          {
            commandOptions.printUsage(true);
            throw new
              IllegalArgumentException(
                  "Incorrectly-specified segment start and end labels: " +
                  pairs[i]);
          }
          startTags[i] = pair[0];
          continueTags[i] = pair[1];
        }
        eval = new MultiSegmentationEvaluator(new InstanceList[] {trainingData, testData}, new String[] {"Training", "Testing"}, 
            startTags, continueTags);
      }
      else
      {
        commandOptions.printUsage(true);
        throw new IllegalArgumentException("Invalid test option: " +
            testOption.value);
      }
    }
    
    
    
    if (p.isTargetProcessing())
    {
      Alphabet targets = p.getTargetAlphabet();
      StringBuffer buf = new StringBuffer("Labels:");
      for (int i = 0; i < targets.size(); i++)
        buf.append(" ").append(targets.lookupObject(i).toString());
      logger.info(buf.toString());
    }
    if (trainOption.value) {
      if (crf == null) {
        crf = getCRF(trainingData, ordersOption.value, defaultOption.value, forbiddenOption.value, allowedOption.value, true);
      }

      HashMap<Integer,double[][]> constraints = FSTConstraintUtil.loadGEConstraints(constraintsFile,trainingData);
      
      if (learningOption.value.equalsIgnoreCase("ge")) {
        ArrayList<GEConstraint> constraintsList = new  ArrayList<GEConstraint>();
        if (penaltyOption.value.equalsIgnoreCase("kl")) {
          OneLabelKLGEConstraints geConstraints = new OneLabelKLGEConstraints();
          for (int fi : constraints.keySet()) {
            double[][] dist = constraints.get(fi);
            
            boolean allSame = true;
            double sum = 0;
            
            double[] prob = new double[dist.length];
            for (int li = 0; li < dist.length; li++) {
              prob[li] = dist[li][0];
              if (!Maths.almostEquals(dist[li][0],dist[li][1])) {
                allSame = false;
                break;
              }
              else if (Double.isInfinite(prob[li])) {
                prob[li] = 0;
              }
              sum += prob[li];
            }
            
            if (!allSame) {
              throw new RuntimeException("A KL divergence penalty cannot be used with target ranges!");
            }
            if (!Maths.almostEquals(sum, 1)) {
              throw new RuntimeException("Targets must sum to 1 when using a KL divergence penalty!");
            }
            
            geConstraints.addConstraint(fi, prob, 1);
          }
          constraintsList.add(geConstraints);
        }
        else if (penaltyOption.value.equalsIgnoreCase("l2")) {
          OneLabelL2RangeGEConstraints geConstraints = new OneLabelL2RangeGEConstraints();
          for (int fi : constraints.keySet()) {
            double[][] dist = constraints.get(fi);
            for (int li = 0; li < dist.length; li++) {
              if (!Double.isInfinite(dist[li][0])) {
                geConstraints.addConstraint(fi, li, dist[li][0], dist[li][1], 1);
              }
            }
          }
          constraintsList.add(geConstraints);
        }
        else {
          throw new RuntimeException("Unknown penalty " + penaltyOption.value);
        }
        crf = trainGE(trainingData, testData, constraintsList, 
            crf, eval, iterationsOption.value, gaussianVarianceOption.value, numResets.value);
      }
      else if (learningOption.value.equalsIgnoreCase("pr")) {
        ArrayList<PRConstraint> constraintsList = new  ArrayList<PRConstraint>();
        if (penaltyOption.value.equalsIgnoreCase("l2")) {
          OneLabelL2IndPRConstraints prConstraints = new OneLabelL2IndPRConstraints(true);
          
          for (int fi : constraints.keySet()) {
            double[][] dist = constraints.get(fi);
            for (int li = 0; li < dist.length; li++) {
              if (!Double.isInfinite(dist[li][0]) && !Maths.almostEquals(dist[li][0],dist[li][1])) {
                throw new RuntimeException("Support for range constraints in PR in development. "
                  + penaltyOption.value);
              }
              
              if (!Double.isInfinite(dist[li][0])) {
                prConstraints.addConstraint(fi, li, dist[li][0], qGaussianVarianceOption.value);
              }
            }
          }
          constraintsList.add(prConstraints);
        }
        else if (penaltyOption.value.equalsIgnoreCase("kl")) {
          throw new RuntimeException("KL divergence not supported for PR.");
        }
        else {
          throw new RuntimeException("Unknown penalty " + penaltyOption.value);
        }
        crf = trainPR(trainingData, testData, constraintsList, crf, eval, iterationsOption.value, gaussianVarianceOption.value);
      }
      else {
        throw new RuntimeException("Unknown learning algorithm " + learningOption.value);
      }

      if (modelOption.value != null) {
        ObjectOutputStream s =
          new ObjectOutputStream(new FileOutputStream(modelOption.value));
        s.writeObject(crf);
        s.close();
      }
    }
    else
    {
      if (crf == null)
      {
        if (modelOption.value == null)
        {
          commandOptions.printUsage(true);
          throw new IllegalArgumentException("Missing model file option");
        }
        ObjectInputStream s =
          new ObjectInputStream(new FileInputStream(modelOption.value));
        crf = (CRF) s.readObject();
        s.close();
      }
      if (eval != null)
        test(new NoopTransducerTrainer(crf), eval, testData);
      else
      {
        boolean includeInput = includeInputOption.value();
        for (int i = 0; i < testData.size(); i++)
        {
          Sequence input = (Sequence)testData.get(i).getData();
          Sequence[] outputs = apply(crf, input, nBestOption.value);
          int k = outputs.length;
          boolean error = false;
          for (int a = 0; a < k; a++) {
            if (outputs[a].size() != input.size()) {
              logger.info("Failed to decode input sequence " + i + ", answer " + a);
              error = true;
            }
          }
          if (!error) {
            for (int j = 0; j < input.size(); j++)
            {
               StringBuffer buf = new StringBuffer();
              for (int a = 0; a < k; a++)
                 buf.append(outputs[a].get(j).toString()).append(" ");
              if (includeInput) {
                FeatureVector fv = (FeatureVector)input.get(j);
                buf.append(fv.toString(true));                
              }
              System.out.println(buf.toString());
            }
            System.out.println();
          }
        }
      }
    }
    long time = (System.currentTimeMillis() - startTime) / 1000;
    System.err.println("took " + time + " seconds");
  }
}