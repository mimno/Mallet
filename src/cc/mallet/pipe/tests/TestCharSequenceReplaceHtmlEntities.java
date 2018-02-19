/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */
package cc.mallet.pipe.tests;

import java.util.ArrayList;
import java.util.List;

import cc.mallet.pipe.CharSequenceReplaceHtmlEntities;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import junit.framework.TestCase;

public class TestCharSequenceReplaceHtmlEntities extends TestCase {

  public void testReplaceAlphaEntities() {
    assertEquals("J'ai \"sorti\" le <strong>chien</strong> tout à l'heure", stringCleansing(
        "J'ai &quot;sorti&quot; le &lt;strong&gt;chien&lt;/strong&gt; tout &amp;agrave; l'heure"));
    assertEquals("Monsieur le Curé of the «Notre-Dame-de-Grâce» neighborhood", stringCleansing(
        "Monsieur le Cur&eacute; of the &laquo;Notre-Dame-de-Gr&acirc;ce&raquo; neighborhood"));
  }

  public void testReplaceDigitEntities() {
    assertEquals("I will display ✂", stringCleansing("I will display &#9986;"));
  }

  private String stringCleansing(String str) {

    Instance instance = new Instance(str, null, null, null);
    InstanceList instances = new InstanceList(createPipes());
    instances.addThruPipe(instance);
    return (String) instance.getData();
  }

  private SerialPipes createPipes() {

    List<Pipe> pipes = new ArrayList<>();
    pipes.add(new CharSequenceReplaceHtmlEntities());

    // Print out the features and the label
    // pipes.add(new PrintInputAndTarget());

    // Construct a new instance list, passing it the pipe we want to use to process instances.
    return new SerialPipes(pipes);
  }
}
