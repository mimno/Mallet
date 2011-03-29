/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;

import gnu.trove.TObjectIntHashMap;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;

import cc.mallet.grmm.inference.Utils;
import cc.mallet.types.Matrixn;
import cc.mallet.types.SparseMatrixn;
import cc.mallet.util.Randoms;


/**
 * An assignment to a bunch of variables.
 * <p/>
 * Note that outcomes are always integers.  If you
 * want them to be something else, then the Variables
 * all have outcome Alphabets; for example, see
 * {@link Variable#lookupOutcome}.
 * <p/>
 * Created: Tue Oct 21 15:11:11 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: Assignment.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Assignment extends AbstractFactor implements Serializable {

  /* Maps from vars => indicies */
  transient TObjectIntHashMap var2idx;

  /* List of Object[].  Each array represents one configuration. */
  transient ArrayList values;

  double scale = 1.0;

  /**
   * Creates an empty assignment.
   */
  public Assignment ()
  {
    super (new HashVarSet ());
    var2idx = new TObjectIntHashMap ();
    values = new ArrayList();
  }

  public Assignment (Variable var, int outcome)
  {
    this ();
    addRow (new Variable[] { var }, new int[] { outcome });
  }

  public Assignment (Variable var, double outcome)
  {
    this ();
    addRow (new Variable[] { var }, new double[] { outcome });
  }

  /**
   * Creates an assignemnt for the given variables.
   */
  public Assignment (Variable[] vars, int[] outcomes)
  {
    var2idx = new TObjectIntHashMap (vars.length);
    values = new ArrayList ();
    addRow (vars, outcomes);
  }

  /**
   * Creates an assignemnt for the given variables.
   */
  public Assignment (Variable[] vars, double[] outcomes)
  {
    var2idx = new TObjectIntHashMap (vars.length);
    values = new ArrayList ();
    addRow (vars, outcomes);
  }

  /**
   * Creates an assignemnt for the given variables.
   */
  public Assignment (List vars, int[] outcomes)
  {
    var2idx = new TObjectIntHashMap (vars.size ());
    values = new ArrayList ();
    addRow ((Variable[]) vars.toArray (new Variable[0]), outcomes);
  }

  /**
   * Creates an assignment over all Variables in a model.
   * The assignment will assign outcomes[i] to the variable
   * <tt>mdl.get(i)</tt>
   */
  public Assignment (FactorGraph mdl, int[] outcomes)
  {
    var2idx = new TObjectIntHashMap (mdl.numVariables ());
    values = new ArrayList ();
    Variable[] vars = new Variable [mdl.numVariables ()];
    for (int i = 0; i < vars.length; i++) vars[i] = mdl.get (i);
    addRow (vars, outcomes);
  }

  /**
   * Returns the union of two Assignments.  That is, the value of a variable in the returned Assignment
   *  will be as specified in one of the given assignments.
   * <p>
   * If the assignments share variables, the value in the new Assignment for those variables in
   *  undefined.
   *
   * @param assn1 One assignment.
   * @param assn2 Another assignment.
   * @return A newly-created Assignment.
   */
  public static Assignment union (Assignment assn1, Assignment assn2)
  {
    Assignment ret = new Assignment ();
    VarSet vars = new HashVarSet ();
    vars.addAll (assn1.vars);
    vars.addAll (assn2.vars);
    Variable[] varr = vars.toVariableArray ();

    if (assn1.numRows () == 0) {
      return (Assignment) assn2.duplicate ();
    } else if (assn2.numRows () == 0) {
      return (Assignment) assn1.duplicate ();
    } else if (assn1.numRows () != assn2.numRows ()) {
      throw new IllegalArgumentException ("Number of rows not equal.");
    }

    for (int ri = 0; ri < assn2.numRows (); ri++) {

      Object[] row = new Object [vars.size()];
      for (int vi = 0; vi < vars.size(); vi++) {
        Variable var = varr[vi];
        if (!assn1.containsVar (var)) {
          row[vi] = assn2.getObject (var);
        } else if (!assn2.containsVar (var)) {
          row[vi] = assn1.getObject (var);
        } else {
          Object val1 = assn1.getObject (var);
          Object val2 = assn2.getObject (var);
          if (!val1.equals (val2)) {
            throw new IllegalArgumentException ("Assignments don't match on intersection.\n  ASSN1["+var+"] = "+val1+"\n  ASSN2["+var+"] = "+val2);
          }
          row[vi] = val1;
        }
      }

      ret.addRow (varr, row);
    }

    return ret;
  }

  /**
   * Returns a new assignment which only assigns values to those variabes in a given clique.
   * @param assn A large assignment
   * @param varSet Which variables to restrict assignment o
   * @return A newly-created Assignment
   * @deprecated marginalize
   */
  public static Assignment restriction (Assignment assn, VarSet varSet)
  {
    return (Assignment) assn.marginalize (varSet);
  }

  public Assignment getRow (int ridx)
  {
    Assignment assn = new Assignment ();
    assn.var2idx = (TObjectIntHashMap) this.var2idx.clone ();
    assn.vars = new UnmodifiableVarSet (vars);
    assn.addRow ((Object[]) values.get (ridx));
    return assn;
  }

  public void addRow (Variable[] vars, int[] values) { addRow (vars, boxArray (values)); }

  public void addRow (Variable[] vars, double[] values) { addRow (vars, boxArray (values)); }

  public void addRow (Variable[] vars, Object[] values)
  {
    checkAssignmentsMatch (vars);
    addRow (values);
  }

  public void addRow (Object[] row)
  {
    if (row.length != numVariables ())
      throw new IllegalArgumentException ("Wrong number of variables when adding to "+this+"\nwas:\n");
    this.values.add (row);
  }

  public void addRow (Assignment other)
  {
    checkAssignmentsMatch (other);
    for (int ridx = 0; ridx < other.numRows(); ridx++) {
      Object[] otherRow = (Object[]) other.values.get (ridx);
      Object[] row = new Object [otherRow.length];
      for (int vi = 0; vi < row.length; vi++) {
        Variable var = this.getVariable (vi);
        row[vi] = other.getObject (ridx, var);
      }
      this.addRow (row);
    }
  }

  private void checkAssignmentsMatch (Assignment other)
  {
    if (numVariables () == 0) {
      setVariables (other.vars.toVariableArray ());
    } else {

      if (numVariables () != other.numVariables ())
        throw new IllegalArgumentException ("Attempt to add row with non-matching variables.\n" +
                "  This has vars: " + varSet () + "\n  Other has vars:" + other.varSet ());

      for (int vi = 0; vi < numVariables (); vi++) {
        Variable var = this.getVariable (vi);
        if (!other.containsVar (var)) {
          throw new IllegalArgumentException ("Attempt to add row with non-matching variables");
        }
      }
    }
  }

  private void checkAssignmentsMatch (Variable[] vars)
  {
    if (numRows () == 0) {
      setVariables (vars);
    } else {
      checkVariables (vars);
    }
  }

  private void checkVariables (Variable[] vars)
  {
    for (int i = 0; i < vars.length; i++) {
      Variable v1 = vars[i];
      Variable v2 = (Variable) this.vars.get (i);
      if (v1 != v2)
        throw new IllegalArgumentException ("Attempt to add row with incompatible variables.");
    }
  }

  private void setVariables (Variable[] varr)
  {
    vars.addAll (Arrays.asList (varr));
    for (int i = 0; i < varr.length; i++) {
      Variable v = varr[i];
      var2idx.put (v, i);
    }
  }

  private Object[] boxArray (int[] values)
  {
    Object[] ret = new Object[values.length];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new Integer (values[i]);
    }
    return ret;
  }

  private Object[] boxArray (double[] values)
  {
    Object[] ret = new Object[values.length];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new Double (values[i]);
    }
    return ret;
  }

  public int numRows () { return values.size (); }

  public int get (Variable var)
  {
    if (numRows() != 1) throw new IllegalArgumentException ("Attempt to call get() with no row specified: "+this);
    return get (0, var);
  }

  public double getDouble (Variable var)
  {
    if (numRows() != 1) throw new IllegalArgumentException ("Attempt to call getDouble() with no row specified: "+this);
    return getDouble (0, var);
  }

  public Object getObject (Variable var)
  {
    if (numRows() != 1) throw new IllegalArgumentException ("Attempt to call getObject() with no row specified: "+this);
    return getObject (0, var);
  }

  /**
   * Returns the value of var in this assigment.
   */
  public int get (int ridx, Variable var)
  {
    int idx = colOfVar (var, false);
    if (idx == -1)
      throw new IndexOutOfBoundsException
              ("Assignment does not give a value for variable " + var);

    Object[] row = (Object[]) values.get (ridx);
    Integer integer = (Integer) row[idx];
    return integer.intValue ();
  }

  /**
   * Returns the value of var in this assigment.
   *   This will be removed when we switch to Java 1.5.
   */
  public double getDouble (int ridx, Variable var)
  {
    int idx = colOfVar (var, false);
    if (idx == -1)
      throw new IndexOutOfBoundsException
              ("Assignment does not give a value for variable " + var);

    Object[] row = (Object[]) values.get (ridx);
    Double dbl = (Double) row[idx];
    return dbl.doubleValue ();
  }


  public Object getObject (int ri, Variable var)
  {
    Object[] row = (Object[]) values.get (ri);
    int ci = colOfVar (var, false);
    if (ci < 0) throw new IllegalArgumentException ("Variable "+var+" does not exist in this assignment.");
    return row[ci];
  }

  public Variable getVariable (int i)
  {
    return (Variable) vars.get (i);
  }

  /** Returns all variables which are assigned to. */
  public Variable[] getVars () {
    return (Variable[]) vars.toArray (new Variable [0]);
  }

  public int size ()
  {
    return numVariables ();
  }

  public static Assignment makeFromSingleIndex (VarSet clique, int idx)
  {
    int N = clique.size ();
    Variable[] vars = clique.toVariableArray ();
    int[] idxs = new int [N];
    int[] szs = new int [N];

    // compute sizes
    for (int i = 0; i < N; i++) {
      Variable var = vars[i];
      szs[i] = var.getNumOutcomes ();
    }

    Matrixn.singleToIndices (idx, idxs, szs);
    return new Assignment (vars, idxs);
  }

  /**
   * Converts this assignment into a unique integer.
   * All different assignments to the same variables are guaranteed to
   * have unique integers.  The range of the index will be between
   * 0 (inclusive) and M (exclusive), where M is the product of all
   * cardinalities of all variables in this assignment.
   *
   * @return An integer
   */
  public int singleIndex ()
  {
    int nr = numRows ();
    if (nr == 0) {
      return -1;
    } else if (nr > 1) {
      throw new IllegalArgumentException ("No row specified.");
    } else {
      return singleIndex (0);
    }
  }

  private void checkIsSingleRow () {if (numRows () != 1) throw new IllegalArgumentException ("No row specified.");}

  public int singleIndex (int row)
  {
    // these could be cached
    int[] szs = new int [numVariables ()];
    for (int i = 0; i < numVariables (); i++) {
      Variable var = (Variable) vars.get (i);
      szs[i] = var.getNumOutcomes ();
    }

    int[] idxs = toIntArray (row);
    return Matrixn.singleIndex (szs, idxs);
  }

  public int numVariables () {return vars.size ();}

  private int[] toIntArray (int ridx)
  {
    int[] idxs = new int [numVariables ()];
    Object[] row = (Object[]) values.get (ridx);
    for (int i = 0; i < row.length; i++) {
      Integer val = (Integer) row [i];
      idxs[i] = val.intValue ();
    }
    return idxs;
  }

  public double[] toDoubleArray (int ridx)
  {
    double[] idxs = new double [numVariables ()];
    Object[] row = (Object[]) values.get (ridx);
    for (int i = 0; i < row.length; i++) {
      Double val = (Double) row [i];
      idxs[i] = val.doubleValue ();
    }
    return idxs;
  }

  public Factor duplicate ()
  {
    Assignment ret = new Assignment ();
    ret.vars = new HashVarSet (vars);
    ret.var2idx = (TObjectIntHashMap) var2idx.clone ();
    ret.values = new ArrayList (values.size ());
    for (int ri = 0; ri < values.size(); ri++) {
      Object[] vals = (Object[]) values.get (ri);
      ret.values.add (vals.clone ());
    }
    ret.scale = scale;
    return ret;
  }

  public void dump ()
  {
    dump (new PrintWriter (new OutputStreamWriter (System.out), true));
  }

  public void dump (PrintWriter out)
  {
    out.print ("ASSIGNMENT ");
    out.println (varSet ());

    for (int vi = 0; vi < var2idx.size (); vi++) {
      Variable var = vars.get (vi);
      out.print (var);
      out.print (" ");
    }
    out.println ();

    for (int ri = 0; ri < numRows (); ri++) {
      for (int vi = 0; vi < var2idx.size (); vi++) {
        Variable var = vars.get (vi);
        Object obj = getObject (ri, var);
        out.print (obj);
        out.print (" ");
      }
      out.println ();
    }
  }


  public void dumpNumeric ()
  {
    for (int i = 0; i < var2idx.size (); i++) {
      Variable var = (Variable) vars.get (i);
      int outcome = get (var);
      System.out.println (var + " " + outcome);
    }
  }

  /** Returns true if this assignment specifies a value for <tt>var</tt> */
  public boolean containsVar (Variable var)
  {
    int idx = colOfVar (var, false);
    return (idx != -1);
  }

  public void setValue (Variable var, int value)
  {
    checkIsSingleRow ();
    setValue (0, var, value);
  }

  public void setValue (int ridx, Variable var, int value)
  {
    int ci = colOfVar (var, true);
    Object[] row = (Object[]) values.get (ridx);
    row[ci] = new Integer (value);
  }

  public void setDouble (int ridx, Variable var, double value)
  {
    int ci = colOfVar (var, true);
    Object[] row = (Object[]) values.get (ridx);
    row[ci] = new Double (value);
  }

  private int colOfVar (Variable var, boolean doAdd)
  {
		if (var2idx.containsKey (var)) {
			return var2idx.get (var);
		} else {
			if (doAdd) {
				return addVar (var);
			} else {
				return -1;
			}
		}
  }

  private int addVar (Variable var)
  {
    int ci = vars.size ();
    vars.add (var);
    var2idx.put (var, ci);

    // expand all rows
    for (int i = 0; i < numRows (); i++) {
      Object[] oldVal = (Object[]) values.get (i);
      Object[] newVal = new Object[ci+1];
      System.arraycopy (oldVal, 0, newVal, 0, ci);
      values.set (i, newVal);
    }

    return ci;
  }

  public void setRow (int ridx, Assignment other)
  {
    checkAssignmentsMatch (other);
    Object[] row = (Object[]) other.values.get (ridx);
    values.set (ridx, row.clone());
  }

  public void setRow (int ridx, int[] vals)
  {
    values.set (ridx, boxArray (vals));
  }


  protected Factor extractMaxInternal (VarSet varSet)
  {
    return asTable ().extractMax (varSet);
  }

  protected double lookupValueInternal (int assnIdx)
  {
    int val = 0;
    for (int ri = 0; ri < numRows (); ri++) {
      if (singleIndex (ri) == assnIdx) {
        val++;
      }
    }
    return val * scale;
  }

  protected Factor marginalizeInternal (VarSet varsToKeep)
  {
    Assignment ret = new Assignment ();
    Variable[] vars = varsToKeep.toVariableArray ();

    for (int ri = 0; ri < this.numRows (); ri++) {
      Object[] row = new Object [vars.length];
      for (int vi = 0; vi < varsToKeep.size (); vi++) {
        Variable var = varsToKeep.get (vi);
        row[vi] = this.getObject (ri, var);
      }
      ret.addRow (vars, row);
    }

    ret.scale = scale;
    return ret;
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    return asTable ().almostEquals (p, epsilon);
  }

  public boolean isNaN ()
  {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public Factor normalize ()
  {
    scale = 1.0 / numRows ();
    return this;
  }

  public Assignment sample (Randoms r)
  {
    int ri = r.nextInt (numRows ());
    Object[] vals = (Object[]) values.get (ri);
    Assignment assn = new Assignment ();
    Variable[] varr = (Variable[]) vars.toArray (new Variable [numVariables ()]);
    assn.addRow (varr, vals);
    return assn;
  }

  public String dumpToString ()
  {
    StringWriter writer = new StringWriter ();
    dump (new PrintWriter (writer));
    return writer.toString ();
  }

  // todo: think about the semantics of this
  public Factor slice (Assignment assn)
  {
    throw new UnsupportedOperationException ();
  }

  public AbstractTableFactor asTable ()
  {
    Variable[] varr = (Variable[]) vars.toArray (new Variable [0]);
    int[] idxs = new int[numRows ()];
    double[] vals = new double[numRows ()];
    for (int ri = 0; ri < numRows (); ri++) {
      idxs[ri] = singleIndex (ri);
      vals[ri]++;
    }
    SparseMatrixn matrix = new SparseMatrixn (Utils.toSizesArray (varr), idxs, vals);
    return new TableFactor (varr, matrix);
  }

  /** Returns a list of single-row assignments, one for each row in this assignment. */
  public List asList ()
  {
    List lst = new ArrayList (numRows ());
    for (int ri = 0; ri < numRows (); ri++) {
      lst.add (getRow (ri));
    }
    return lst;
  }

  public Assignment subAssn (int start, int end)
  {
    Assignment other = new Assignment ();
    for (int ri = start; ri < end; ri++) {
      other.addRow (getRow (ri));
    }
    return other;
  }

  public int[] getColumnInt (Variable x1)
  {
    int[] ret = new int [numRows ()];
    for (int ri = 0; ri < ret.length; ri++) {
      ret[ri] = get (ri, x1);
    }
    return ret;
  }

  private static final long serialVersionUID = 1;
  private static final int SERIAL_VERSION = 2;

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
//    in.defaultReadObject ();
    int version = in.readInt ();  // version

    int numVariables = in.readInt ();
    var2idx = new TObjectIntHashMap (numVariables);
    for (int vi = 0; vi < numVariables; vi++) {
      Variable var = (Variable) in.readObject ();
      var2idx.put (var, vi);
    }

    int numRows = in.readInt ();
    values = new ArrayList (numRows);
    for (int ri = 0; ri < numRows; ri++) {
      Object[] row = (Object[]) in.readObject ();
      values.add (row);
    }

    scale = (version >= 2) ? in.readDouble () : 1.0;
  }

  private void writeObject (ObjectOutputStream out) throws IOException
  {
//    out.defaultWriteObject ();
    out.writeInt (SERIAL_VERSION);
    out.writeInt (numVariables ());
    for (int vi = 0; vi < numVariables (); vi++) {
      out.writeObject (getVariable (vi));
    }
    out.writeInt (numRows ());
    for (int ri = 0; ri < numRows (); ri++) {
      Object[] row = (Object[]) values.get (ri);
      out.writeObject (row);
    }
    out.writeDouble (scale);
  }

}


