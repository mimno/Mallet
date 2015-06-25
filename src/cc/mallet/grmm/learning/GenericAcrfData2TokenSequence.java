/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.learning;


import java.util.ArrayList;
import java.util.regex.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import cc.mallet.extract.StringSpan;
import cc.mallet.extract.StringTokenization;
import cc.mallet.grmm.util.LabelsAssignment;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;

/**
 * Generic pipe that takes a linegroup of the form:
 * <pre>
 *  LABEL1 LABEL2 ... LABELk word feature1 feature2 ... featuren
 * </pre>
 * and converts it into an input FeatureVectorSequence and target LabelsSequence.
 * <p>
 * If the number of labels at each sequence position could vary, then use this format instead:
 *  <pre>
 *  LABEL1 LABEL2 ... LABELk ---- word feature1 feature2 ... featuren
 *  </pre>
 * The four dashes ---- must be there to separate the features from the labels.
 * Whitespace is ignored.
 * The difference between this pipe and {@link edu.umass.cs.iesl.casutton.experiments.dcrf.GenericDcrfPipe} is that this pipe
 *  allows for a different number of labels at each sequence position.
 * <p>
 * Explicitly specifying which word is the token allows the use of the HTML output from
 *  the extract package.
 *
 * Created: Aug 22, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: GenericAcrfData2TokenSequence.java,v 1.1 2007/10/22 21:37:43 mccallum Exp $
 */
public class GenericAcrfData2TokenSequence extends Pipe {

  private ArrayList labelDicts = new ArrayList ();
  private int numLabels = -1;
  private boolean includeTokenText = true;
  private String textFeaturePrefix = "WORD=";
  private boolean featuresIncludeToken = true;
  private boolean labelsAtEnd = false;

  public GenericAcrfData2TokenSequence ()
  {
    super (new Alphabet(), new LabelAlphabet());
		labelDicts.add (getTargetAlphabet());
  }

  public GenericAcrfData2TokenSequence (int numLabels)
  {
    super (new Alphabet(), new LabelAlphabet());
    this.numLabels = numLabels;
		labelDicts.add (getTargetAlphabet());
  }

  public void setIncludeTokenText (boolean includeTokenText)
  {
    this.includeTokenText = includeTokenText;
  }

  /**
   * If true, then the first feature in the list is considered to be the token's text.
   * If false, then no feature is designated as the token text.
   * @param featuresIncludeToken
   */
  public void setFeaturesIncludeToken (boolean featuresIncludeToken)
  {
    this.featuresIncludeToken = featuresIncludeToken;
  }

  public boolean getFeaturesIncludeToken ()
  {
    return featuresIncludeToken;
  }

  public void setTextFeaturePrefix (String textFeaturePrefix)
  {
    this.textFeaturePrefix = textFeaturePrefix;
  }

  public LabelAlphabet getLabelAlphabet (int lvl)
  {
    return (LabelAlphabet) labelDicts.get (lvl);
  }

  public int numLevels ()
  {
    return labelDicts.size();
  }

  public Instance pipe (Instance carrier)
  {
    String input;
    if (carrier.getData () instanceof CharSequence) {
      input = String.valueOf(carrier.getData ());
    } else {
      throw new ClassCastException("Needed a String; got "+carrier.getData());
    }
    
    String[] lines = input.split ("\n");

    StringSpan[] spans = new StringSpan[lines.length];
    Labels[] lbls = new Labels[lines.length];
    StringBuffer buf = new StringBuffer ();

    Alphabet dict = getDataAlphabet ();

    Pattern r = Pattern.compile("^(.*=)?([+-]?\\d*\\.?\\d+([eE][+-]?\\d+)?)(@[+-]?\\d+)?"); // Match numeric feature.  Supports named features, offset features, and features in scientific notation.

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String[] toks = line.split ("\\s+");

      int j = 0;
      ArrayList thisLabels = new ArrayList ();
      if (!labelsAtEnd) {
        while (!isLabelSeparator (toks, j)) {
          thisLabels.add (labelForTok (toks[j], j));
          j++;
        }
        if ((j < toks.length) && toks[j].equals ("----")) j++;
        lbls[i] = new Labels ((Label[]) thisLabels.toArray (new Label[thisLabels.size ()]));
      }

      int maxFeatureIdx = (labelsAtEnd) ? toks.length - numLabels : toks.length;

      String text = "*???*";
      if (featuresIncludeToken) {
        if (j < maxFeatureIdx) {
          text = toks [j++];
        }
      }

      int start = buf.length ();
      buf.append (text);
      int end = buf.length ();
      buf.append (" ");

      StringSpan span = new StringSpan (buf, start, end);

      while (j < maxFeatureIdx) {
        // Set the feature's value.  If the feature is not numeric, set the value to 1
        // http://t47962.ai-mallet-development.aitalk.info/continuous-observations-for-dcrf-t47962.html
        Matcher m = r.matcher(toks[j]);
        if (m.matches ()) {// if use .find, use $ in regex
          span.setFeatureValue (toks[j].intern (), Double.parseDouble(m.group(2)));
        } else {
          span.setFeatureValue (toks[j].intern (), 1.0);
        }
        j++;
      }

      if (includeTokenText) {
        span.setFeatureValue ((textFeaturePrefix+text).intern(), 1.0);
      }

      if (labelsAtEnd) {
        int firstLblIdx = j;
        while (j < toks.length) {
          thisLabels.add (labelForTok (toks[j], j - firstLblIdx));
          j++;
        }
        lbls[i] = new Labels ((Label[]) thisLabels.toArray (new Label[thisLabels.size ()]));
      }

      spans[i] = span;

    }

    StringTokenization tokenization = new StringTokenization (buf);
    tokenization.addAll (spans);
    carrier.setData (tokenization);

    carrier.setTarget (new LabelsAssignment (new LabelsSequence (lbls)));
    return carrier;
  }

  private Label labelForTok (String tok, int lvl)
  {
    while (labelDicts.size() <= lvl) {
      labelDicts.add (new LabelAlphabet ());
    }
    LabelAlphabet dict = (LabelAlphabet) labelDicts.get (lvl);
    return dict.lookupLabel (tok);
  }

  private boolean isLabelSeparator (String[] toks, int j)
  {
    if (numLabels > 0) {
      // if fixed numLabels, just return whether we have enough.
      return j >= numLabels;
    } else {
      // otherwise, use the dynamic labels separator
      return toks[j].equals ("----");
    }
  }

  // Serialization garbage

  // version 1.0 == returned a feature vector sequence
  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 2;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
    if (version <= 1) {
      featuresIncludeToken = true;
    }
  }

  public boolean isLabelsAtEnd ()
  {
    return labelsAtEnd;
  }

  public void setLabelsAtEnd (boolean labelsAtEnd)
  {
    this.labelsAtEnd = labelsAtEnd;
  }
}
