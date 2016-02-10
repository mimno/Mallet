/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning.extract;


import java.util.Iterator;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;
import java.io.File;

import cc.mallet.extract.Extraction;
import cc.mallet.extract.TokenizationFilter;
import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.grmm.learning.*;
import cc.mallet.grmm.util.RememberTokenizationPipe;
import cc.mallet.grmm.util.PipedIterator;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PipeUtils;
import cc.mallet.pipe.iterator.PipeInputIterator;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Instance;
import cc.mallet.util.CollectionUtils;
import cc.mallet.util.FileUtils;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Timing;

/**
 * Created: Mar 31, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: ACRFExtractorTrainer.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class ACRFExtractorTrainer {

  private static final Logger logger = MalletLogger.getLogger (ACRFExtractorTrainer.class.getName());

  private int numIter = 99999;
  protected ACRF.Template[] tmpls;
  protected InstanceList training;
  protected InstanceList testing;
  private Iterator<Instance> testIterator;
  private Iterator<Instance> trainIterator;
  ACRFTrainer trainer = new DefaultAcrfTrainer ();
  protected Pipe featurePipe;
  protected Pipe tokPipe;
  protected ACRFEvaluator evaluator = new DefaultAcrfTrainer.LogEvaluator ();
  TokenizationFilter filter;
  private Inferencer inferencer;
  private Inferencer viterbiInferencer;
  private int numCheckpointIterations = -1;
  private File checkpointDirectory = null;
  private boolean usePerTemplateTrain = false;
  private int perTemplateIterations = 100;
  private boolean cacheUnrolledGraphs;

  // For data subsets
  private Random r;
  private double trainingPct = -1;
  private double testingPct = -1;

  // Using cascaded setter idiom

  public ACRFExtractorTrainer setTemplates (ACRF.Template[] tmpls)
  {
    this.tmpls = tmpls;
    return this;
  }

  public ACRFExtractorTrainer setDataSource (Iterator<Instance> trainIterator, Iterator<Instance> testIterator)
  {
    this.trainIterator = trainIterator;
    this.testIterator = testIterator;
    return this;
  }

  public ACRFExtractorTrainer setData (InstanceList training, InstanceList testing)
  {
    this.training = training;
    this.testing = testing;
    return this;
  }

  public ACRFExtractorTrainer setNumIterations (int numIter)
  {
    this.numIter = numIter;
    return this;
  }

  public int getNumIter ()
  {
    return numIter;
  }

  public ACRFExtractorTrainer setPipes (Pipe tokPipe, Pipe featurePipe)
  {
    RememberTokenizationPipe rtp = new RememberTokenizationPipe ();
    this.featurePipe = PipeUtils.concatenatePipes (rtp, featurePipe);
    this.tokPipe = tokPipe;
    return this;
  }

  public ACRFExtractorTrainer setEvaluator (ACRFEvaluator evaluator)
  {
    this.evaluator = evaluator;
    return this;
  }

  public ACRFExtractorTrainer setTrainingMethod (ACRFTrainer acrfTrainer)
  {
    trainer = acrfTrainer;
    return this;
  }

  public ACRFExtractorTrainer setTokenizatioFilter (TokenizationFilter filter)
  {
    this.filter = filter;
    return this;
  }

  public ACRFExtractorTrainer setCacheUnrolledGraphs (boolean cacheUnrolledGraphs)
  {
    this.cacheUnrolledGraphs = cacheUnrolledGraphs;
    return this;
  }

  public ACRFExtractorTrainer setNumCheckpointIterations (int numCheckpointIterations)
  {
    this.numCheckpointIterations = numCheckpointIterations;
    return this;
  }

  public ACRFExtractorTrainer setCheckpointDirectory (File checkpointDirectory)
  {
    this.checkpointDirectory = checkpointDirectory;
    return this;
  }

  public ACRFExtractorTrainer setUsePerTemplateTrain (boolean usePerTemplateTrain)
  {
    this.usePerTemplateTrain = usePerTemplateTrain;
    return this;
  }

  public ACRFExtractorTrainer setPerTemplateIterations (int numIter)
  {
    this.perTemplateIterations = numIter;
    return this;
  }

  public ACRFTrainer getTrainer ()
  {
    return trainer;
  }

  public TokenizationFilter getFilter ()
  {
    return filter;
  }
  //  Main methods

  public ACRFExtractor trainExtractor ()
  {
    ACRF acrf = (usePerTemplateTrain) ? perTemplateTrain() : trainAcrf ();

    ACRFExtractor extor = new ACRFExtractor (acrf, tokPipe, featurePipe);
    if (filter != null) extor.setTokenizationFilter (filter);

    return extor;
  }

  private ACRF perTemplateTrain ()
  {
    Timing timing = new Timing ();
    boolean hasConverged = false;

    ACRF miniAcrf = null;
    if (training == null) setupData ();
    for (int ti = 0; ti < tmpls.length; ti++) {
      ACRF.Template[] theseTmpls = new ACRF.Template[ti+1];
      System.arraycopy (tmpls, 0, theseTmpls, 0, theseTmpls.length);
      logger.info ("***PerTemplateTrain: Round "+ti+"\n  Templates: "+
              CollectionUtils.dumpToString (Arrays.asList (theseTmpls), " "));
      miniAcrf = new ACRF (featurePipe, theseTmpls);
      setupAcrf (miniAcrf);
      ACRFEvaluator eval = setupEvaluator ("tmpl"+ti);
      hasConverged = trainer.train (miniAcrf, training, null, testing, eval, perTemplateIterations);
      timing.tick ("PerTemplateTrain round "+ti);
    }

    // finish by training to convergence
    ACRFEvaluator eval = setupEvaluator ("full");
    if (!hasConverged)
        trainer.train (miniAcrf, training, null, testing, eval, numIter);

    // the last acrf is the one to go with;
    return miniAcrf;
  }

  /**
   * Trains a new ACRF object with the given settings.  Subclasses may override this method
   *  to implement alternative training procedures.
   * @return a trained ACRF
   */
  public ACRF trainAcrf ()
  {
    if (training == null) setupData ();
    ACRF acrf = new ACRF (featurePipe, tmpls);
    setupAcrf (acrf);
    ACRFEvaluator eval = setupEvaluator ("");

    trainer.train (acrf, training, null, testing, eval, numIter);

    return acrf;
  }

  private void setupAcrf (ACRF acrf)
  {
    if (cacheUnrolledGraphs) acrf.setCacheUnrolledGraphs (true);
    if (inferencer != null) acrf.setInferencer (inferencer);
    if (viterbiInferencer != null) acrf.setViterbiInferencer (viterbiInferencer);
  }

  private ACRFEvaluator setupEvaluator (String checkpointPrefix)
  {
    ACRFEvaluator eval = evaluator;
    if (numCheckpointIterations > 0) {
      List evals = new ArrayList ();
      evals.add (evaluator);
      evals.add (new CheckpointingEvaluator (checkpointDirectory, numCheckpointIterations, tokPipe, featurePipe));
      eval = new AcrfSerialEvaluator (evals);
    }
    return eval;
  }

  protected void setupData ()
  {
    Timing timing = new Timing ();
    training = new InstanceList (featurePipe);
    training.addThruPipe (new PipedIterator (trainIterator, tokPipe));
    if (trainingPct > 0) training = subsetData (training, trainingPct);

    if (testIterator != null) {
      testing = new InstanceList (featurePipe);
      testing.addThruPipe (new PipedIterator (testIterator, tokPipe));
      if (testingPct > 0) testing = subsetData (testing, trainingPct);
    }

    timing.tick ("Data loading");
  }

  private InstanceList subsetData (InstanceList data, double pct)
  {
    InstanceList[] lsts = data.split (r, new double[] { pct, 1 - pct });
    return lsts[0];
  }

  public InstanceList getTrainingData ()
  {
    if (training == null) setupData ();
    return training;
  }

  public InstanceList getTestingData ()
  {
    if (testing == null) setupData ();
    return testing;
  }

  public Extraction extractOnTestData (ACRFExtractor extor)
  {
    return extor.extract (testing);
  }

  public ACRFExtractorTrainer setInferencer (Inferencer inferencer)
  {
    this.inferencer = inferencer;
    return this;
  }

  public ACRFExtractorTrainer setViterbiInferencer (Inferencer viterbiInferencer)
  {
    this.viterbiInferencer = viterbiInferencer;
    return this;
  }

  public ACRFExtractorTrainer setDataSubsets (Random random, double trainingPct, double testingPct)
  {
    r = random;
    this.trainingPct = trainingPct;
    this.testingPct = testingPct;
    return this;
  }

  // checkpointing

  private static class CheckpointingEvaluator extends ACRFEvaluator {

    private File directory;
    private int interval;
    private Pipe tokPipe;
    private Pipe featurePipe;

    public CheckpointingEvaluator (File directory, int interval, Pipe tokPipe, Pipe featurePipe)
    {
      this.directory = directory;
      this.interval = interval;
      this.tokPipe = tokPipe;
      this.featurePipe = featurePipe;
    }

    public boolean evaluate (ACRF acrf, int iter, InstanceList training, InstanceList validation, InstanceList testing)
    {
      if (iter > 0 && iter % interval == 0) {
        ACRFExtractor extor = new ACRFExtractor (acrf, tokPipe, featurePipe);
        FileUtils.writeGzippedObject (new File (directory, "extor."+iter+".ser.gz"), extor);
      }
      return true;
    }

    public void test (InstanceList gold, List returned, String description) { }
  }
}
