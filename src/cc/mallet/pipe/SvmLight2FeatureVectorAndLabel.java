/* Copyright (C) 2010 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;

/**
 * This Pipe converts a line in SVMLight format to 
 * a Mallet instance with FeatureVector data and 
 * Label target.  The expected format is
 * 
 * target feature:value feature:value ...
 * 
 * targets and features can be indices, as in 
 * SVMLight, or Strings.
 * 
 * Note that if targets and features are indices,
 * their indices in the data and target Alphabets
 * may be different, though the data will be
 * equivalent.  
 * 
 * @author Gregory Druck
 *
 */
public class SvmLight2FeatureVectorAndLabel extends Pipe {

  private static final long serialVersionUID = 1L;
  
  public SvmLight2FeatureVectorAndLabel () {
    super (new Alphabet(), new LabelAlphabet());
  }
  
  // There is no guarantee that the feature indices in the text
  // file will be the same as in the pipe.  The data should be
  // exactly the same, however, just permuted.  
  @Override public Instance pipe(Instance carrier) {
    // we expect the data for each instance to be
    // a line from the SVMLight format text file    
    String dataStr = (String)carrier.getData();

    // ignore comments at the end
    if (dataStr.contains("#")) {
      dataStr = dataStr.substring(0, dataStr.indexOf('#'));
    }

    String[] terms = dataStr.split("\\s+");
    
    String classStr = terms[0];
    // In SVMLight +1 and 1 are the same label.  
    // Adding a special case to normalize...
    if (classStr.equals("+1")) {
    	classStr = "1";
    }
    Label label = ((LabelAlphabet)getTargetAlphabet()).lookupLabel(classStr, true);
    carrier.setTarget(label);
    
    // the rest are feature-value pairs
    int numFeatures = terms.length - 1;
    int[] indices = new int[numFeatures];
    double[] values = new double[numFeatures];
    for (int termIndex = 1; termIndex < terms.length; termIndex++) {
      if (!terms[termIndex].equals("")) {
        String[] s = terms[termIndex].split(":");
        if (s.length != 2) {
          throw new RuntimeException("invalid format: " + terms[termIndex] + " (should be feature:value)");
        }
        String feature = s[0];
        indices[termIndex-1] = getDataAlphabet().lookupIndex(feature, true);       
        values[termIndex-1] = Double.parseDouble(s[1]);
      }
    }
    
    FeatureVector fv = new FeatureVector(getDataAlphabet(), indices, values);
    carrier.setData(fv);
    return carrier;
  }
}