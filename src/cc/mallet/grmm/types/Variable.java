/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;


import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;

import cc.mallet.types.LabelAlphabet;
import cc.mallet.util.PropertyList;


/**
 *  Class for a discrete random variable in a graphical model.
 *
 * Created: Thu Sep 18 09:32:25 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: Variable.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Variable implements Comparable, Serializable {

  private String label;  // name of this variable
  private LabelAlphabet outcomes;

  /** Number of outcomes for a continous variable. */
  public static final int CONTINUOUS = -1;

  private static int counter = 0;
  private Universe universe;
  private int index;

  /**
   * Creates a new variable with the given outcomes.
   */
  public Variable (LabelAlphabet outs)
  {
    this (Universe.DEFAULT, outs);
  }

  public Variable (Universe universe, LabelAlphabet outs)
  {
    this.universe = universe;
    this.outcomes = outs;
    if (outs.size() < 1) {
      throw new IllegalArgumentException
        ("Attempt to create variable with "+outs.size()+" outcomes.");
    }
    setName ();
    index = universe.add (this);
  }


  public Variable (int numOutcomes)
  {
    this (Universe.DEFAULT, numOutcomes);
  }

  public Variable (Universe universe, int numOutcomes)
  {
    this.universe = universe;
    if (numOutcomes > 0) outcomes = createBlankAlphabet (numOutcomes);
    setName ();
    index = universe.add (this); 
  }


  private static LabelAlphabet createBlankAlphabet (int numOutcomes)
  {
    if (numOutcomes > 0) {
      LabelAlphabet outcomes = new LabelAlphabet ();
      /* Setup default outcomes */
      for (int i = 0; i < numOutcomes; i++) {
        outcomes.lookupIndex (new Integer (i));
      }
      return outcomes;
    } else return null;
  }

  private void setName ()
  {
    setLabel ("VAR" + (counter++));
  }


  public String getLabel ()
  {
    return label;
  }

  public void setLabel (String label)
  {
    this.label = label;
  }

  public int getNumOutcomes () {
    if (outcomes == null) {
      // we're continuous
      return CONTINUOUS;
    } else {
      return outcomes.size();
    }
  }

  public Object lookupOutcome (int i) {
    return outcomes.lookupObject (i);
  }

  public LabelAlphabet getLabelAlphabet ()
  {
    return outcomes;
  }

  public int compareTo(Object o)
  {
    /*
    Variable var = (Variable) o;
    return getLabel().compareTo (var.getLabel());
    */
    int index = this.index;
    int index2 = ((Variable)o).index;

    if (index == index2) {
      return 0;
    } else if (index < index2) {
      return -1;
    } else {
      return 1;
    }
    /**/
  }

  transient private PropertyList properties = null;

  public void setNumericProperty (String key, double value)
  {
    properties = PropertyList.add (key, value, properties);
  }

  public double getNumericProperty (String key)
  {
    return properties.lookupNumber (key);
  }

  public String toString ()
  {
    return label;
  }

  /** Returns the index of this variable in its universe */
  public int getIndex ()
  {
    return index;
  }

  public Universe getUniverse ()
  {
    return universe;
  }


  // Serialization garbage

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    in.readInt ();
  }

  public boolean isContinuous ()
  {
    return outcomes == null;
  }
}
