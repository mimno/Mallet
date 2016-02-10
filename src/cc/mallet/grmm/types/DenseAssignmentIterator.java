/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;



class DenseAssignmentIterator extends AbstractAssignmentIterator implements AssignmentIterator {

  private int current = 0;

  DenseAssignmentIterator (VarSet verts)
	{
    super (verts);
  }

  DenseAssignmentIterator (VarSet verts, int index)
	{
    super (verts);
    current = index;
    if (current >= max) {
      throw new IllegalArgumentException ("No assigment # "+index +" for "+this+".  Max is "+max);
    }
  }

  public void advance()
  {
    current++;
  }

  //xxx wise to make public?
  public int indexOfCurrentAssn () { return current; }

  public boolean hasNext() {
    return current < max;
  }

}
