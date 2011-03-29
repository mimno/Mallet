/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;
/**
 *
 * Created: Aug 23, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: GenericAcrfTui.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */

import bsh.EvalError;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.pipe.iterator.PipeInputIterator;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Instance;
import cc.mallet.util.*;

public class GenericAcrfTui {

  private static CommandOption.File modelFile = new CommandOption.File
          (GenericAcrfTui.class, "model-file", "FILENAME", true, null, "Text file describing model structure.", null);

  private static CommandOption.File trainFile = new CommandOption.File
          (GenericAcrfTui.class, "training", "FILENAME", true, null, "File containing training data.", null);

  private static CommandOption.File testFile = new CommandOption.File
          (GenericAcrfTui.class, "testing", "FILENAME", true, null, "File containing testing data.", null);

  private static CommandOption.Integer numLabelsOption = new CommandOption.Integer
  (GenericAcrfTui.class, "num-labels", "INT", true, -1,
          "If supplied, number of labels on each line of input file." +
                  "  Otherwise, the token ---- must separate labels from features.", null);

  private static CommandOption.String inferencerOption = new CommandOption.String
          (GenericAcrfTui.class, "inferencer", "STRING", true, "TRP",
                  "Specification of inferencer.", null);

  private static CommandOption.String maxInferencerOption = new CommandOption.String
          (GenericAcrfTui.class, "max-inferencer", "STRING", true, "TRP.createForMaxProduct()",
                  "Specification of inferencer.", null);

  private static CommandOption.String evalOption = new CommandOption.String
          (GenericAcrfTui.class, "eval", "STRING", true, "LOG",
                  "Evaluator to use.  Java code grokking performed.", null);

  static CommandOption.Boolean cacheUnrolledGraph = new CommandOption.Boolean
          (GenericAcrfTui.class, "cache-graphs", "true|false", true, false,
                  "Whether to use memory-intensive caching.", null);

  static CommandOption.Boolean useTokenText = new CommandOption.Boolean
          (GenericAcrfTui.class, "use-token-text", "true|false", true, false,
                  "Set this to true if first feature in every list is should be considered the text of the " +
                          "current token.  This is used for NLP-specific debugging and error analysis.", null);

  static CommandOption.Integer randomSeedOption = new CommandOption.Integer
	(GenericAcrfTui.class, "random-seed", "INTEGER", true, 0,
	 "The random seed for randomly selecting a proportion of the instance list for training", null);


  private static BshInterpreter interpreter = setupInterpreter ();

  public static void main (String[] args) throws IOException, EvalError
  {
    doProcessOptions (GenericAcrfTui.class, args);
    Timing timing = new Timing ();

    GenericAcrfData2TokenSequence basePipe;
    if (!numLabelsOption.wasInvoked ()) {
      basePipe = new GenericAcrfData2TokenSequence ();
    } else {
      basePipe = new GenericAcrfData2TokenSequence (numLabelsOption.value);
    }

    basePipe.setFeaturesIncludeToken(useTokenText.value);
    basePipe.setIncludeTokenText(useTokenText.value);
    
    Pipe pipe = new SerialPipes (new Pipe[] {
        basePipe,
        new TokenSequence2FeatureVectorSequence (true, true),
    });

    Iterator<Instance> trainSource = new LineGroupIterator (new FileReader (trainFile.value), Pattern.compile ("^\\s*$"), true);
    Iterator<Instance> testSource;
    if (testFile.wasInvoked ()) {
      testSource = new LineGroupIterator (new FileReader (testFile.value), Pattern.compile ("^\\s*$"), true);
    } else {
      testSource = null;
    }

    InstanceList training = new InstanceList (pipe);
    training.addThruPipe (trainSource);
    InstanceList testing = new InstanceList (pipe);
    testing.addThruPipe (testSource);

    ACRF.Template[] tmpls = parseModelFile (modelFile.value);
    ACRFEvaluator eval = createEvaluator (evalOption.value);

    Inferencer inf = createInferencer (inferencerOption.value);
    Inferencer maxInf = createInferencer (maxInferencerOption.value);

    ACRF acrf = new ACRF (pipe, tmpls);
    acrf.setInferencer (inf);
    acrf.setViterbiInferencer (maxInf);

    ACRFTrainer trainer = new DefaultAcrfTrainer ();
    trainer.train (acrf, training, null, testing, eval, 9999);
    timing.tick ("Training");

    FileUtils.writeGzippedObject (new File ("acrf.ser.gz"), acrf);
    timing.tick ("Serializing");

    System.out.println ("Total time (ms) = " + timing.elapsedTime ());
  }

  private static BshInterpreter setupInterpreter ()
  {
    BshInterpreter interpreter = CommandOption.getInterpreter ();
    try {
      interpreter.eval ("import cc.mallet.base.extract.*");
      interpreter.eval ("import cc.mallet.grmm.inference.*");
      interpreter.eval ("import cc.mallet.grmm.learning.*");
      interpreter.eval ("import cc.mallet.grmm.learning.templates.*");
    } catch (EvalError e) {
      throw new RuntimeException (e);
    }

    return interpreter;
  }

  public static ACRFEvaluator createEvaluator (String spec) throws EvalError
  {
    if (spec.indexOf ('(') >= 0) {
      // assume it's Java code, and don't screw with it.
      return (ACRFEvaluator) interpreter.eval (spec);
    } else {
      LinkedList toks = new LinkedList (Arrays.asList (spec.split ("\\s+")));
      return createEvaluator (toks);
    }
  }

  private static ACRFEvaluator createEvaluator (LinkedList toks)
  {
    String type = (String) toks.removeFirst ();

    if (type.equalsIgnoreCase ("SEGMENT")) {
      int slice = Integer.parseInt ((String) toks.removeFirst ());
      if (toks.size() % 2 != 0)
        throw new RuntimeException ("Error in --eval "+evalOption.value+": Every start tag must have a continue.");
      int numTags = toks.size () / 2;
      String[] startTags = new String [numTags];
      String[] continueTags = new String [numTags];

      for (int i = 0; i < numTags; i++) {
        startTags[i] = (String) toks.removeFirst ();
        continueTags[i] = (String) toks.removeFirst ();
      }

      return new MultiSegmentationEvaluatorACRF (startTags, continueTags, slice);

    } else if (type.equalsIgnoreCase ("LOG")) {
      return new DefaultAcrfTrainer.LogEvaluator ();

    } else if (type.equalsIgnoreCase ("SERIAL")) {
      List evals = new ArrayList ();
      while (!toks.isEmpty ()) {
        evals.add (createEvaluator (toks));
      }
      return new AcrfSerialEvaluator (evals);

    } else {
      throw new RuntimeException ("Error in --eval "+evalOption.value+": illegal evaluator "+type);
    }
  }

  private static Inferencer createInferencer (String spec) throws EvalError
  {
    String cmd;
    if (spec.indexOf ('(') >= 0) {
      // assume it's Java code, and don't screw with it.
      cmd = spec;
    } else {
      cmd = "new "+spec+"()";
    }

    // Return whatever the Java code says to
    Object inf = interpreter.eval (cmd);

    if (inf instanceof Inferencer)
      return (Inferencer) inf;

    else throw new RuntimeException ("Don't know what to do with inferencer "+inf);
  }


  public static void doProcessOptions (Class childClass, String[] args)
  {
    CommandOption.List options = new CommandOption.List ("", new CommandOption[0]);
    options.add (childClass);
    options.process (args);
    options.logOptions (Logger.getLogger (""));
  }

  private static ACRF.Template[] parseModelFile (File mdlFile) throws IOException, EvalError
  {
    BufferedReader in = new BufferedReader (new FileReader (mdlFile));

    List tmpls = new ArrayList ();
    String line = in.readLine ();
    while (line != null) {
      Object tmpl = interpreter.eval (line);
      if (!(tmpl instanceof ACRF.Template)) {
        throw new RuntimeException ("Error in "+mdlFile+" line "+in.toString ()+":\n  Object "+tmpl+" not a template");
      }
      tmpls.add (tmpl);
      line = in.readLine ();
    }

    return (ACRF.Template[]) tmpls.toArray (new ACRF.Template [0]);
  }

}
