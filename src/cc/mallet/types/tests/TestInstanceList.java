package cc.mallet.types.tests;

import org.junit.Assert;
import org.junit.Test;
import cc.mallet.pipe.Noop;
import cc.mallet.types.Alphabet;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;

public class TestInstanceList {

  @Test
  public void testSplit() {

    LabelAlphabet labelDict = new LabelAlphabet();
    labelDict.lookupIndex("0", true);
    labelDict.lookupIndex("1", true);

    /* The labels of the instances we are going to create, in that order */
    String labelsString = "001011100100100";
    String[] instLabels = new String[labelsString.length()];
    for (int i = 0; i < instLabels.length; i++) {
      instLabels[i] = Character.toString(labelsString.charAt(i));
    }

    double[] data = new double[0];

    InstanceList ilist = new InstanceList(new Noop(null, labelDict));
    for (int i = 0; i < instLabels.length; i++) {
      Label instLabel = labelDict.lookupLabel(instLabels[i], false);
      ilist.add(new Instance(data, instLabel, "i" + i, null));
    }

    int numFolds = 3;
    double foldRatio = 1.0 / numFolds;
    double[] proportions = new double[numFolds];
    for (int i = 0; i < numFolds; i++) {
      proportions[i] = foldRatio;
    }

    InstanceList[] instSplits = ilist.split(proportions);
    Assert.assertTrue(instSplits.length == 3);
    for (int i = 0; i < instSplits.length; i++) {
      InstanceList splitList = instSplits[i];
      Assert.assertTrue(splitList.size() == (labelsString.length() / 3));
    }
  }


  @Test
  public void testStratifiedSplit() {

    /* The labels of the instances we are going to create, in that order */
    String labelsString = "001011100100100";
    testTemplate(labelsString, 3);
    System.out.println();

    testTemplate(labelsString, 5);
    System.out.println();

    testTemplate(labelsString, 6);
    System.out.println();

    testTemplate(labelsString, 15);
    System.out.println();
  }


  private void testTemplate( String labelsString, int numFolds ){

    LabelAlphabet labelDict = new LabelAlphabet();

    /* The labels of the instances we are going to create, in that order */
    String[] instLabels = new String[labelsString.length()];
    for (int i = 0; i < instLabels.length; i++) {
      instLabels[i] = Character.toString(labelsString.charAt(i));
      labelDict.lookupIndex(instLabels[i], true);
    }

    double[] data = new double[0]; // Just arbitrary, non-null data

    InstanceList ilist = new InstanceList(null, labelDict);
    for (int i = 0; i < instLabels.length; i++) {
      Label instLabel = labelDict.lookupLabel(instLabels[i], false);
      ilist.add(new Instance(data, instLabel, "i" + i, null));
    }

    double foldRatio = 1.0 / numFolds;
    double[] proportions = new double[numFolds];
    for (int i = 0; i < numFolds; i++) {
      proportions[i] = foldRatio;
    }

    /* Generate the splits with the stratified technique */
    InstanceList[] instSplits = ilist.stratifiedSplitInOrder(proportions);
    Assert.assertTrue(instSplits.length == numFolds);
    for (int i = 0; i < instSplits.length; i++) {
      InstanceList splitList = instSplits[i];
      Assert.assertTrue(splitList.size() == (labelsString.length() / numFolds));
    }
  }
}
