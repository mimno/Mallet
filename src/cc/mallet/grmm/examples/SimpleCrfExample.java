/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.examples;


import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;

import cc.mallet.grmm.learning.ACRF;
import cc.mallet.grmm.learning.ACRFTrainer;
import cc.mallet.grmm.learning.DefaultAcrfTrainer;
import cc.mallet.grmm.learning.GenericAcrfData2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureVectorSequence;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.InstanceList;
import cc.mallet.util.FileUtils;

/**
 * $Id: SimpleCrfExample.java,v 1.1 2007/10/22 21:38:02 mccallum Exp $
 */
public class SimpleCrfExample {

  public static void main (String[] args) throws FileNotFoundException
  {
    File trainFile = new File (args[0]);
    File testFile = new File (args[1]);
    File crfFile = new File (args[2]);

    Pipe pipe = new SerialPipes (new Pipe[] {
        new GenericAcrfData2TokenSequence (2),
        new TokenSequence2FeatureVectorSequence (true, true),
    });

    InstanceList training = new InstanceList (pipe);
    training.addThruPipe (new LineGroupIterator (new FileReader (trainFile),
                                         Pattern.compile ("\\s*"),
                                         true));

    InstanceList testing = new InstanceList (pipe);
    training.addThruPipe (new LineGroupIterator (new FileReader (testFile),
                                         Pattern.compile ("\\s*"),
                                         true));

    ACRF.Template[] tmpls = new ACRF.Template[] {
            new ACRF.BigramTemplate (0),
            new ACRF.BigramTemplate (1),
            new ACRF.PairwiseFactorTemplate (0,1),
            new CrossTemplate1 (0,1)
    };

    ACRF acrf = new ACRF (pipe, tmpls);

    ACRFTrainer trainer = new DefaultAcrfTrainer ();
    trainer.train (acrf, training, null, testing, 99999);

    FileUtils.writeGzippedObject (crfFile, acrf);
  }
}

