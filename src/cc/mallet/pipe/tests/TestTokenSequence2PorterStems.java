/*
 * Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept. This file is part of
 * "MALLET" (MAchine Learning for LanguagE Toolkit). http://www.cs.umass.edu/~mccallum/mallet This
 * software is provided under the terms of the Common Public License, version 1.0, as published by
 * http://www.opensource.org. For further information, see the file `LICENSE' included with this
 * distribution.
 */

package cc.mallet.pipe.tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2PorterStems;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
import cc.mallet.util.CharSequenceLexer;

import junit.framework.*;

public class TestTokenSequence2PorterStems extends TestCase {

  public void testVector() {

    String vector =
        "exampl do you realli think it is weak that yield to temptat i tell you that there ar "
            + "terribl temptat which it requir strength strength and courag to yield to oscar wild";
    SerialPipes pipes = createPipes();

    Instance instance =
        new Instance(
            "Example:\n"
                + "Do you really think it is weakness that yields to temptation? I tell you that there are "
                + "terrible temptations which it requires strength, strength and courage to yield to ~ "
                + "Oscar Wilde", null, null, null);
    InstanceList instances = new InstanceList(pipes);
    instances.addThruPipe(instance);
    TokenSequence tokenSequence = (TokenSequence) instance.getData();

    assertEquals(32, tokenSequence.size());
    assertEquals(vector, tokenSequence2String(tokenSequence));
  }

  public void testStep1a() {
    assertEquals("caress", oneWordStem("caresses"));
    assertEquals("poni", oneWordStem("ponies"));
    assertEquals("ti", oneWordStem("ties"));
    assertEquals("caress", oneWordStem("caress"));
    assertEquals("cat", oneWordStem("cats"));
  }

  public void testStep1b() {
    assertEquals("feed", oneWordStem("feed"));
    assertEquals("agre", oneWordStem("agreed"));
    assertEquals("plaster", oneWordStem("plastered"));
    assertEquals("bled", oneWordStem("bled"));
    assertEquals("motor", oneWordStem("motoring"));
    assertEquals("sing", oneWordStem("sing"));
    assertEquals("conflat", oneWordStem("conflated"));
    assertEquals("troubl", oneWordStem("troubled"));
    assertEquals("size", oneWordStem("sized"));
    assertEquals("hop", oneWordStem("hopping"));
    assertEquals("tan", oneWordStem("tanned"));
    assertEquals("fall", oneWordStem("falling"));
    assertEquals("hiss", oneWordStem("hissing"));
    assertEquals("fizz", oneWordStem("fizzed"));
    assertEquals("fail", oneWordStem("failing"));
    assertEquals("file", oneWordStem("filing"));
  }

  public void testStep1c() {
    assertEquals("happi", oneWordStem("happy"));
    assertEquals("sky", oneWordStem("sky"));
  }

  public void testStep2() {
    assertEquals("relat", oneWordStem("relational"));
    assertEquals("condit", oneWordStem("conditional"));
    assertEquals("ration", oneWordStem("rational"));
    assertEquals("valenc", oneWordStem("valenci"));
    assertEquals("hesit", oneWordStem("hesitanci"));
    assertEquals("digit", oneWordStem("digitizer"));
    assertEquals("conform", oneWordStem("conformabli"));
    assertEquals("radic", oneWordStem("radicalli"));
    assertEquals("differ", oneWordStem("differentli"));
    assertEquals("vile", oneWordStem("vileli"));
    assertEquals("analog", oneWordStem("analogousli"));
    assertEquals("vietnam", oneWordStem("vietnamization"));
    assertEquals("predic", oneWordStem("predication"));
    assertEquals("oper", oneWordStem("operator"));
    assertEquals("feudal", oneWordStem("feudalism"));
    assertEquals("decis", oneWordStem("decisiveness"));
    assertEquals("hope", oneWordStem("hopefulness"));
    assertEquals("callous", oneWordStem("callousness"));
    assertEquals("formal", oneWordStem("formaliti"));
    assertEquals("sensit", oneWordStem("sensitiviti"));
    assertEquals("sensibl", oneWordStem("sensibiliti"));
  }

  public void testStep3() {
    assertEquals("triplic", oneWordStem("triplicate"));
    assertEquals("form", oneWordStem("formative"));
    assertEquals("formal", oneWordStem("formalize"));
    assertEquals("electr", oneWordStem("electriciti"));
    assertEquals("electr", oneWordStem("electrical"));
    assertEquals("hope", oneWordStem("hopeful"));
    assertEquals("good", oneWordStem("goodness"));
  }

  public void testStep4() {
    assertEquals("reviv", oneWordStem("revival"));
    assertEquals("allow", oneWordStem("allowance"));
    assertEquals("infer", oneWordStem("inference"));
    assertEquals("airlin", oneWordStem("airliner"));
    assertEquals("gyroscop", oneWordStem("gyroscopic"));
    assertEquals("adjust", oneWordStem("adjustable"));
    assertEquals("defens", oneWordStem("defensible"));
    assertEquals("irrit", oneWordStem("irritant"));
    assertEquals("replac", oneWordStem("replacement"));
    assertEquals("adjust", oneWordStem("adjustment"));
    assertEquals("depend", oneWordStem("dependent"));
    assertEquals("adopt", oneWordStem("adoption"));
    assertEquals("homolog", oneWordStem("homologou"));
    assertEquals("commun", oneWordStem("communism"));
    assertEquals("activ", oneWordStem("activate"));
    assertEquals("angular", oneWordStem("angulariti"));
    assertEquals("homolog", oneWordStem("homologous"));
    assertEquals("effect", oneWordStem("effective"));
    assertEquals("bowdler", oneWordStem("bowdlerize"));
  }

  public void testStep5a() {
    assertEquals("probat", oneWordStem("probate"));
    assertEquals("rate", oneWordStem("rate"));
    assertEquals("ceas", oneWordStem("cease"));
  }

  public void testStep5b() {
    assertEquals("control", oneWordStem("controll"));
    assertEquals("roll", oneWordStem("roll"));
  }

  private String oneWordStem(String str) {

    Instance instance = new Instance(str, null, null, null);
    InstanceList instances = new InstanceList(createPipes());
    instances.addThruPipe(instance);
    return tokenSequence2String((TokenSequence) instance.getData());
  }

  private String tokenSequence2String(TokenSequence tokenSequence) {

    StringBuilder builder = new StringBuilder();
    Iterator<Token> tokens = tokenSequence.iterator();

    while (tokens.hasNext()) {
      Token token = tokens.next();
      builder.append(token.getText()).append(' ');
    }
    return builder.toString().trim();
  }

  private SerialPipes createPipes() {

    List<Pipe> pipes = new ArrayList<>();

    // Convert string to lowercase
    pipes.add(new CharSequenceLowercase());

    // Tokenize string
    pipes.add(new CharSequence2TokenSequence(CharSequenceLexer.LEX_WORDS));

    // Apply Porter's stem algorithm
    pipes.add(new TokenSequence2PorterStems());

    // Print out the features and the label
    // pipes.add(new PrintInputAndTarget());

    // Construct a new instance list, passing it the pipe we want to use to process instances.
    return new SerialPipes(pipes);
  }
}
