/* Copyright (C) 2011 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.fst.semi_supervised;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;

import cc.mallet.types.InstanceList;

/**
 * Expectation constraint utilities for fst package.
 *
 * @author Gregory Druck
 */

public class FSTConstraintUtil {

  public static HashMap<Integer,double[][]> loadGEConstraints(Reader fileReader, InstanceList data) { 
    HashMap<Integer,double[][]> constraints = new HashMap<Integer,double[][]>();
    
    for (int li = 0; li < data.getTargetAlphabet().size(); li++) {
      System.err.println(data.getTargetAlphabet().lookupObject(li));
    }
    
    try {
      BufferedReader reader = new BufferedReader(fileReader);
      String line = reader.readLine();
      while (line != null) {
        String[] split = line.split("\\s+");
        
        // assume the feature name has no spaces
        String featureName = split[0];
        int featureIndex = data.getDataAlphabet().lookupIndex(featureName,false);
        if (featureIndex == -1) { 
          throw new RuntimeException("Feature " + featureName + " not found in the alphabet!");
        }
        
        double[][] probs = new double[data.getTargetAlphabet().size()][2];
        for (int i = 0; i < probs.length; i++) Arrays.fill(probs[i ],Double.NEGATIVE_INFINITY);
        for (int index = 1; index < split.length; index++) {
          String[] labelSplit = split[index].split(":");   
          
          int li = data.getTargetAlphabet().lookupIndex(labelSplit[0],false);
          assert (li != -1) : labelSplit[0];
          
          if (labelSplit[1].contains(",")) {
            String[] rangeSplit = labelSplit[1].split(",");
            double lower = Double.parseDouble(rangeSplit[0]);
            double upper = Double.parseDouble(rangeSplit[1]);
            probs[li][0] = lower;
            probs[li][1] = upper;
          }
          else {
            double prob = Double.parseDouble(labelSplit[1]);
            probs[li][0] = prob;
            probs[li][1] = prob;
          }
        }
        constraints.put(featureIndex, probs);
        line = reader.readLine();
      }
    }
    catch (Exception e) {  
      e.printStackTrace();
      System.exit(1);
    }
    return constraints;
  }
}
