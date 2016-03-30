/*
 * Copyright (C) 2016 Univ. of Massachusetts Amherst, Computer Science Dept. This file is
 * part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 * http://www.cs.umass.edu/~mccallum/mallet This software is provided under the terms of
 * the Common Public License, version 1.0, as published by http://www.opensource.org. For
 * further information, see the file `LICENSE' included with this distribution.
 */

package cc.mallet.types.tests;

import java.util.ArrayList;
import java.util.List;

import cc.mallet.types.Alphabet;
import cc.mallet.types.BiNormalSeparation;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.RankedFeatureVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Clint Burford
 */
public class TestBiNormalSeparation extends TestCase {

  class BinaryTestData {

    InstanceList iList;
    Alphabet dataAlphabet;
    Label posLabel;
    Label negLabel;

    BinaryTestData(int numFeatures) {
      LabelAlphabet labelAlphabet = new LabelAlphabet();
      posLabel = labelAlphabet.lookupLabel("pos", true);
      negLabel = labelAlphabet.lookupLabel("neg", true);
      List<String> featureNames = new ArrayList<String>();
      for (int i = 0; i < numFeatures; i++) {
        featureNames.add(Integer.toString(i));
      }
      dataAlphabet = new Alphabet(featureNames.toArray());
      iList = new InstanceList(dataAlphabet, labelAlphabet);
    }

    void addInstance(int[] features, boolean positive) {
      FeatureVector featureVector = new FeatureVector(dataAlphabet, features);
      Instance instance = new Instance(featureVector, positive ? posLabel : negLabel,
          null, null);
      iList.add(instance);
    }

    InstanceList getInstanceList() {
      return iList;
    }
  }

  public TestBiNormalSeparation(String name) {
    super(name);
  }

  public static Test suite() {
    return new TestSuite(TestBiNormalSeparation.class);
  }

  public void testBiNormalSeparation() {
    BinaryTestData binaryTestData = new BinaryTestData(4);
    binaryTestData.addInstance(new int[] {0, 1}, true);
    binaryTestData.addInstance(new int[] {0, 2}, true);
    binaryTestData.addInstance(new int[] {2, 3}, false);
    binaryTestData.addInstance(new int[] {3}, false);
    InstanceList iList = binaryTestData.getInstanceList();
    RankedFeatureVector rankedFeatureVector = new BiNormalSeparation.Factory()
        .newRankedFeatureVector(iList);
    assertEquals(6.58, rankedFeatureVector.getValueAtRank(0), 0.005);
    assertEquals(3.29, rankedFeatureVector.getValueAtRank(2), 0.005);
    assertEquals(0, rankedFeatureVector.getValueAtRank(3), 0);
    assertEquals(6.58, rankedFeatureVector.getValueAtRank(1), 0.005);
    assertEquals(2, rankedFeatureVector.getIndexAtRank(3));
    assertEquals(1, rankedFeatureVector.getIndexAtRank(2));
  }
}
