/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe;

import java.util.List;
import java.util.ArrayList;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.CharSequenceNoDiacritics;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import junit.framework.TestCase;


public class TestCharSequenceNoDiacritics extends TestCase {

  public void testAccentAigu() {
    assertEquals("medecin", oneWordCleansing("médecin"));
    assertEquals("etouffer", oneWordCleansing("étouffer"));
    assertEquals("marche", oneWordCleansing("marché"));
  }

  public void testAccentGrave() {
    assertEquals("tres", oneWordCleansing("très"));
    assertEquals("deuxieme", oneWordCleansing("deuxième"));
  }

  public void testAccentCirconflexe() {
    assertEquals("foret", oneWordCleansing("forêt"));
    assertEquals("hotel", oneWordCleansing("hôtel"));
  }

  public void testCedille() {
    assertEquals("garcon", oneWordCleansing("garçon"));
    assertEquals("francais", oneWordCleansing("français"));
  }

  public void testTrema() {
    assertEquals("jamaique", oneWordCleansing("Jamaïque"));
    assertEquals("coincidence", oneWordCleansing("coïncidence"));
  }

  public void testRussianWords() {
    assertEquals("замок", oneWordCleansing("замо́к"));
    assertEquals("замок", oneWordCleansing("за́мок"));
    assertEquals("уже", oneWordCleansing("у́же"));
    assertEquals("слово", oneWordCleansing("сло́во"));
    assertEquals("компьютер", oneWordCleansing("компью́тер"));
    assertEquals("бумага", oneWordCleansing("бума́га"));
    assertEquals("экран", oneWordCleansing("экра́н"));
    assertEquals("сообщение", oneWordCleansing("сообще́ние"));
  }

  private String oneWordCleansing(String str) {

    Instance instance = new Instance(str, null, null, null);
    InstanceList instances = new InstanceList(createPipes());
    instances.addThruPipe(instance);
    return (String) instance.getData();
  }

  private SerialPipes createPipes() {

    List<Pipe> pipes = new ArrayList<Pipe>(); 

    // Convert string to lowercase
    pipes.add(new CharSequenceLowercase());

    // Remove diacritics
    pipes.add(new CharSequenceNoDiacritics());

    // Print out the features and the label
    // pipes.add(new PrintInputAndTarget());

    // Construct a new instance list, passing it the pipe we want to use to process instances.
    return new SerialPipes(pipes);
  }
}
