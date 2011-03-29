/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;

import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectObjectProcedure;
import gnu.trove.TIntIntHashMap;

import java.io.*;
import java.util.*;

import cc.mallet.grmm.inference.ExactSampler;
import cc.mallet.grmm.inference.VariableElimination;
import cc.mallet.grmm.util.CSIntInt2ObjectMultiMap;
import cc.mallet.grmm.util.Models;
import cc.mallet.util.Randoms;
import cc.mallet.util.*;



/**
 * Class for undirected graphical models.
 *
 * Created: Mon Sep 15 15:18:30 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: FactorGraph.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class FactorGraph implements Factor {

  final private List factors = new ArrayList ();

  /**
	 * Set of clique potential for this graph.
	 *   Ordinarily will map Cliques to DiscretePotentials.
	 */
	final private THashMap clique2ptl = new THashMap ();

  private Universe universe;
  private TIntIntHashMap projectionMap;
  private int[] my2global;

  private BidirectionalIntObjectMap factorsAlphabet;

  /**
	 * Duplicate indexing of factors for vertices and edges.  These
	 *  arrays are indexed by their Variable's index (see @link{Variable#index})
	 */
	transient private List[] vertexPots;

	transient private CSIntInt2ObjectMultiMap pairwiseFactors;

  transient private List[] factorsByVar;

	int numNodes;

	public FactorGraph () {
		super();
		numNodes = 0;
		setCachesCapacity (0);
    factorsAlphabet = new BidirectionalIntObjectMap ();
  }

	/**
	 * Create a model with the variables given.  This is much faster
	 * than adding the variables one at a time.
	 */
	public FactorGraph (Variable[] vars) {
		this();
		setCachesCapacity (vars.length);
		for (int i = 0; i < vars.length; i++) {
			cacheVariable (vars [i]);
		}
	}

  public FactorGraph (Factor[] factors)
  {
    this ();
    for (int i = 0; i < factors.length; i++) {
      addFactor (factors[i]);
    }
  }

  public FactorGraph (Collection factors)
  {
    this ();
    for (Iterator it = factors.iterator (); it.hasNext ();) {
       addFactor ((Factor) it.next ());
    }
  }

  /**
	 * Create a model with the given capacity (i.e., capacityin terms of number of variable nodes).
   *   It can expand later, but declaring the capacity in advance if you know it makes many things
   *   more efficient.
	 */
	public FactorGraph (int capacity)
	{
		this ();
		setCachesCapacity (capacity);
	}


  /**************************************************************************
   *  CACHING
   **************************************************************************/

  private void clearCaches ()
  {
    setCachesCapacity (numNodes);
    pairwiseFactors.clear ();
    projectionMap.clear ();
  }

  // Increases the size of all the caching arrays that need to be increased when a node is added.
  //  This can also be called before he caches have been se up.
  private void setCachesCapacity (int n)
	{
    factorsByVar = new List [n];
    for (int i = 0; i < n; i++) { factorsByVar[i] = new ArrayList (); }
		vertexPots = new List [n];
    my2global = new int [n];

    if (projectionMap == null) {
      projectionMap = new TIntIntHashMap (n);
      // projectionMap.setDefaultValue (-1);
    } else {
      projectionMap.ensureCapacity (n);
    }
    
    // no need to recreate edgePots if it exists, since it's a HashMap.
    if (pairwiseFactors == null) pairwiseFactors = new CSIntInt2ObjectMultiMap ();
  }

  private void removeFactor (Factor factor)
  {
    factors.remove (factor);
    clique2ptl.remove (factor.varSet ());
    regenerateCaches ();
  }

  private void removeFactorsOfVariable (final Variable var)
	{
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      if (ptl.varSet ().contains (var)) {
        it.remove ();
      }
    }

    clique2ptl.retainEntries(new TObjectObjectProcedure () {
			public boolean execute (Object clique, Object ptl) {
				return !((VarSet) clique).contains (var);
			}
		});
	}

	private void removeFromVariableCaches (Variable victim)
	{
    Set survivors = new THashSet (variablesSet ());
    survivors.remove (victim);

    int vi = 0;
    TIntIntHashMap dict = new TIntIntHashMap (survivors.size ());
    // dict.setDefaultValue (-1);  No longer supported, but this.getIndex() written to avoid need for this.
    my2global = new int[survivors.size ()];

    for (Iterator it = survivors.iterator (); it.hasNext();) {
      Variable var = (Variable) it.next ();
      int gvi = var.getIndex ();
      dict.put (gvi, vi);
      my2global [vi] = gvi;
    }

    projectionMap = dict;
    numNodes--;  // do this at end b/c it affects getVertexSet()
  }

	private void recacheFactors ()
	{
    numNodes = 0;
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      VarSet vs = ptl.varSet ();
      addVarsIfNecessary (vs);
      cacheFactor (vs, ptl);
    }
	}

	private void regenerateCaches ()
	{
    clearCaches ();
		recacheFactors ();
	}

	private void updateFactorCaches ()
	{
		assert numNodes == numVariables ();
		if (vertexPots == null) {
			setCachesCapacity (numNodes);
		} else if (numNodes > vertexPots.length) {
			List[] oldVertexPots = vertexPots;
		  CSIntInt2ObjectMultiMap oldEdgePots = pairwiseFactors;
      List[] oldFactorsByVar = factorsByVar;
      int[] oldM2G = my2global;

      setCachesCapacity (2*numNodes);
      assert (oldEdgePots != null);
      System.arraycopy (oldVertexPots, 0, vertexPots, 0, oldVertexPots.length);
      System.arraycopy (oldM2G, 0, my2global, 0, oldM2G.length);


      for (int i = 0; i < oldFactorsByVar.length; i++) {
        factorsByVar[i].addAll (oldFactorsByVar[i]);
      }
		}
	}

  private void cacheVariable (Variable var)
  {
    numNodes++;
    updateFactorCaches ();

    int gvi = var.getIndex ();
    int myvi = numNodes - 1;
    projectionMap.put (gvi, myvi);
    my2global[myvi] = gvi;
  }

  private void cacheFactor (VarSet varSet, Factor factor)
  {
    switch (varSet.size()) {
      case 1:
        int vidx = getIndex (varSet.get(0));
        cacheVariableFactor (vidx, factor);
        factorsByVar[vidx].add (factor);
        break;

      case 2:
        int idx1 = getIndex (varSet.get(0));
        int idx2 = getIndex (varSet.get(1));
        cachePairwiseFactor (idx1, idx2, factor);
        break;

      default:
        for (Iterator it = varSet.iterator (); it.hasNext ();) {
          Variable var = (Variable) it.next ();
          int idx = getIndex (var);
          factorsByVar[idx].add (factor);
        }

        break;
    }
  }

  private void cacheVariableFactor (int vidx, Factor factor)
  {
    if (vertexPots[vidx] == null) {
      vertexPots[vidx] = new ArrayList (2);
    }
    vertexPots[vidx].add (factor);
  }

  private void cachePairwiseFactor (int idx1, int idx2, Factor ptl)
  {
    pairwiseFactors.add (idx1, idx2, ptl);
    pairwiseFactors.add (idx2, idx1, ptl);
    factorsByVar[idx1].add (ptl);
    factorsByVar[idx2].add (ptl);
  }



  /**************************************************************************
   *  ACCESSORS
   **************************************************************************/

  /** Returns the number of variable nodes in the graph. */
  public int numVariables () { return numNodes; }

  public Set variablesSet () {
    return new AbstractSet () {
      public Iterator iterator () { return variablesIterator (); }
      public int size () { return numNodes; }
    };
  }

  public Iterator variablesIterator ()
  {
    return new Iterator () {
      private int i = 0;
      public boolean hasNext() { return i < numNodes; }
      public Object next() { return get(i++); }
      public void remove() { throw new UnsupportedOperationException (); }
    };
  }

  /**
   * Returns all variables that are adjacent to a given variable in
   *  this graph---that is, the set of all variables that share a
   *  factor with this one.
   */
  //xxx inefficient. perhaps cache this.
  public VarSet getAdjacentVertices (Variable var)
  {
    HashVarSet c = new HashVarSet ();
    List adjFactors = allFactorsContaining (var);
    for (Iterator it = adjFactors.iterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      c.addAll (factor.varSet ());
    }
    return c;
  }

  /**
	 * Returns collection that contains factors in this model.
	 */
	public Collection factors () {
		return Collections.unmodifiableCollection (factors);
	}

	/**
	 * Returns an iterator of all the factors in the graph.
	 */
 	public Iterator factorsIterator ()
	{
		return factors ().iterator();
	}

	/**
	 * Returns an iterator over all assignments to all variables of this
	 *  graphical model.
	 * @see Assignment
	 */
	public AssignmentIterator assignmentIterator ()
	{
    return new DenseAssignmentIterator (varSet ());
	}

	/**
	 * Returns an iterator of all the VarSets in the graph
	 *  over which factors are defined.
	 */
	public Iterator varSetIterator ()
	{
		return clique2ptl.keySet().iterator();
	}

	/**
	 *  Returns a unique numeric index for a variable in this model.
	 *   Every UndirectedModel <tt>mdl</tt> maintains a mapping between its
	 *   variables and the integers 0...size(mdl)-1 , which is suitable
	 *   for caching the variables in an array.
	 *  <p>
	 *  <tt>getIndex</tt> and <tt>get</tt> are inverses.  That is, if
	 *  <tt>idx == getIndex (var)</tt>, then <tt>get(idx)</tt> will
	 *  return <tt>var</tt>.
	 * @param var A variable contained in this graphical model
	 * @return The numeric index of var
	 * @see #get(int)
	 */
	public int getIndex (Variable var)
	{
	    int idx = var.getIndex();
	    if (projectionMap.containsKey(idx)) {
		return projectionMap.get(idx);
	    }
	    else {
		return -1;
	    }
	}

  public int getIndex (Factor factor)
	{
		return factorsAlphabet.lookupIndex (factor, false);
	}

	/**
	 *  Returns a variable from this model with a given index.
	 *   Every UndirectedModel <tt>mdl</tt> maintains a mapping between its
	 *   variables and the integers 0...size(mdl)-1 , which is suitable
	 *   for caching the variables in an array.
	 *  <P>
	 *  <tt>getIndex</tt> and <tt>get</tt> are inverses.  That is, if
	 *  <tt>idx == getIndex (var)</tt>, then <tt>get(idx)</tt> will
	 *  return <tt>var</tt>.
	 *  @see #getIndex(Variable)
	 */
	public Variable get (int index)
	{
    int globalIdx = my2global[index];
    return universe.get (globalIdx);
	}


  public Factor getFactor (int i)
  {
    return (Factor) factorsAlphabet.lookupObject (i);
  }

  /** Returns the degree of a given variable in this factor graph,
   *   that is, the number of factors in which the variable is
   *   an argument.
   */
  public int getDegree (Variable var)
  {
    return allFactorsContaining (var).size ();
  }

  /**
	 *  Searches this model for a variable with a given name.
	 *  @param name Name to find.
	 *  @return A variable <tt>var</tt> such that <tt>var.getLabel().equals (name)</tt>
	 */
	public Variable findVariable (String name)
	{
		Iterator it = variablesIterator ();
		while (it.hasNext()) {
			Variable var = (Variable) it.next();
			if (var.getLabel().equals(name)) {
				return var;
			}
		}
		return null;
	}

  /**
   * Returns the factor in this graph, if any, whose domain is a given clique.
   * @return The factor defined over this clique.  Returns null if
   * no such factor exists.  Will not return
   * potential defined over subsets or supersets of this clique.
   * @see #addFactor(Factor)
   * @see #factorOf(Variable,Variable)
   * @see #factorOf(Variable)
   */
  public Factor factorOf (VarSet varSet)
  {
    switch (varSet.size ()) {
      case 1: return factorOf (varSet.get (0));
      case 2: return factorOf (varSet.get (0), varSet.get (1));
      default: return factorOf ((Collection) varSet);
    }
  }

  /**
   *  Returns the factor defined over a given pair of variables.
   *  <P>
   *   This method is equivalent to calling {@link #factorOf}
   *   with a VarSet that contains only <tt>v1</tt> and <tt>v2</tt>.
   * <P>
   *  @param var1  One variable of the pair.
   *  @param var2  The other variable of the pair.
   *  @return The factor defined over the pair <tt>(v1, v2)</tt>
   *   Returns null if no such potential exists.
   */
  public Factor factorOf (Variable var1, Variable var2)
  {
    List ptls = allEdgeFactors (var1, var2);
    Factor ptl = firstIfSingleton (ptls, var1+" "+var2);

    if (ptl != null) {
      assert ptl.varSet().size() == 2;
      assert ptl.containsVar (var1);
      assert ptl.containsVar (var2);
    }
    return ptl;
  }

  private List allEdgeFactors (Variable var1, Variable var2)
  {
    return pairwiseFactors.get (getIndex (var1), getIndex (var2));
  }


  /** Returns a collection of all factors that involve only the given variables.
   *   That is, all factors whose domain is a subset of the given collection.
   */
  public Collection allFactorsContaining (Collection vars)
  {
    THashSet factors = new THashSet ();
    for (Iterator it = factorsIterator (); it.hasNext ();) {
      Factor ptl = (Factor) it.next ();
      if (vars.containsAll (ptl.varSet ()))
        factors.add (ptl);
    }
    return factors;
  }

  public List allFactorsContaining (Variable var)
  {
    return factorsByVar [getIndex (var)];
  }


  /** Returns a list of all factors in the graph whose domain is exactly the specified var. */
  public List allFactorsOf (Variable var)
  {
    int idx = getIndex (var);
    if (idx == -1) {
      return new ArrayList ();
    } else {
      return vertexPots [idx];
    }
  }

  /** Returns a list of all factors in the graph whose domain is exactly the specified Collection of Variables. */
  public List allFactorsOf (Collection c)
  {
    // Rather than iterating over all factors, just iterate over ones that we know contain c.get(0)
    //  (could possibly make more efficient by picking the var with smallest degree).
    Variable v0 = (Variable) c.iterator ().next ();
    List factors = factorsByVar[getIndex (v0)];

    List ret = new ArrayList ();
    for (Iterator it = factors.iterator(); it.hasNext();) {
      Factor f = (Factor) it.next ();
      VarSet varSet = f.varSet ();
      if (varSet.size() == c.size ()) {
        if (c.containsAll (varSet) && varSet.containsAll (c)) {
          ret.add (f);
        }
      }
    }

    return ret;
  }

  /**************************************************************************
   *  MUTATORS
   **************************************************************************/

	/**
	 * Removes a variable from this model, along with all of its factors.
   */
  public void remove (Variable var)
	{
    removeFromVariableCaches (var);
    removeFactorsOfVariable (var);
		regenerateCaches ();
	}

  /**
   * Removes a Collection of variables from this model, along with all of its factors.
   *  This is equivalent to calling remove(Variable) on each element of the collection, but
   *  because of the caching performed elsewhere in this class, this method is vastly
   *  more efficient.
   */
  public void remove (Collection vars)
	{
		for (Iterator it = vars.iterator (); it.hasNext();) {
			Variable var = (Variable) it.next ();
			removeFactorsOfVariable (var);
		}

		numNodes -= vars.size ();
		regenerateCaches ();
	}

	/**
	 * Returns whether two variables are adjacent in the model's graph.
	 *  @param v1 A variable in this model
	 *  @param v2 Another variable in this model
	 *  @return Whether there is an edge connecting them
	 */
  public boolean isAdjacent (Variable v1, Variable v2)
	{
		List factors = allFactorsContaining (v1);
		Iterator it = factors.iterator ();
		while (it.hasNext()) {
			Factor ptl = (Factor) it.next ();
      if (ptl.varSet ().contains (v2)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether this variable is part of the model.
	 *  @param v1 Any Variable object
	 *  @return true if this variable is contained in the moel.
	 */
	public boolean containsVar (Variable v1)
	{
		return variablesSet ().contains (v1);
	}

  public void addFactor (Variable var1, Variable var2, double[] probs)
  {
    Variable[] vars = new Variable[] { var1, var2 };
    TableFactor pot = new TableFactor (vars, probs);
    addFactor (pot);
  }

  /**
	 * Adds a factor to the model.
	 * <P>
	 *  If a factor has already been added for the variables in the
	 *   given clique, the effects of this method are (currently)
	 * undefined.
   * <p>
   * All convenience methods for adding factors eventually call through
   *  to this one, so this is the method for subclasses to override if they
   *  wish to perform additional actions when a factor is added to the graph.
	 *
	 *  @param factor A factor over the variables in clique.
   */
	public void addFactor (Factor factor)
	{
    beforeFactorAdd (factor);
    VarSet varSet = factor.varSet ();
    addVarsIfNecessary (varSet);
    factors.add (factor);
    factorsAlphabet.lookupIndex (factor);
    addToListMap (clique2ptl, varSet, factor);
    // cache the factor
    cacheFactor (varSet, factor);
    afterFactorAdd (factor);
  }


  /** Performs checking of a factor before it is added to the model.
   *   This method should throw an unchecked exception if there is a problem.
   *   This implementation does nothing, but it may be overridden by subclasses.
   *  @param factor Factor that is about to be added
   */
  protected void beforeFactorAdd (Factor factor) {}

  /** Performs operations on a factor after it has been added to the model,
   *   such as caching.
   *   This implementation does nothing, but it may be overridden by subclasses.
   *  @param factor Factor that has just been added
   */
  protected void afterFactorAdd (Factor factor) {}

  private void addToListMap (Map map, Object key, Object value)
  {
    List lst = (List) map.get (key);
    if (lst == null) {
      lst = new ArrayList ();
      map.put (key, lst);
    }
    lst.add (value);
  }

  private void addVarsIfNecessary (VarSet varSet)
  {
    for (int i = 0; i < varSet.size(); i++) {
      Variable var = varSet.get (i);
      if (universe == null) { universe = var.getUniverse (); }
      if (getIndex (var) < 0) {
        cacheVariable (var);
      }
    }
  }

	/**
	 * Removes all potentias from this model.
	 */
	public void clear ()
	{
    factorsAlphabet = new BidirectionalIntObjectMap ();
    factors.clear ();
    clique2ptl.clear ();
    clearCaches ();
    numNodes = 0;
  }

	/**
	 * Returns the unnormalized probability for an assignment to the
	 * model.  That is, the value return is
	 * <pre>
  \prod_C \phi_C (assn)
</pre>
* where C ranges over all cliques for which factors have been defined.
	 *
	 * @param assn An assignment for all variables in this model.
	 * @return The unnormalized probability
	 */
    public double factorProduct (Assignment assn)
    {
	Iterator ptlIter = factorsIterator ();
	double ptlProd = 1;

	while (ptlIter.hasNext())
	{
	    ptlProd *= ((Factor)ptlIter.next()).value (assn);
	}

	return ptlProd;

    }



  /**
   *  Returns the factor for a given node.  That is, this method returns the
   *   factor whose domain is exactly this node.
   *  <P>
   *   This method is equivalent to calling {@link #factorOf}
   *   with a clique object that contains only <tt>v</tt>.
   * <P>
   *  @param var which the factor is over.
   *  @throws RuntimeException If the model contains more than one factor over the given variable.  Use allFactorsOf in this case.
   *  @return The factor defined over the edge <tt>v</tt>
   *    (such as by {@link #addFactor(Factor)}).  Returns null if
   *    no such factor exists.
   */
  public Factor factorOf (Variable var)
  {
    List lst = allFactorsOf (var);
    return firstIfSingleton (lst, var.toString ());
  }

  private Factor firstIfSingleton (List lst, String desc)
  {
    if (lst == null) return null;
    int sz = lst.size ();
    if (sz > 1) {
      throw new RuntimeException ("Multiple factors over "+desc+":\n"+ CollectionUtils.dumpToString (lst, " "));
    } else if (sz == 0) {
      return null;
    } else {
      return (Factor) lst.get (0);
    }
  }

  /**
   * Searches the graphical model for a factor over the given
   * collection of variables.
   * @return The factor defined over the given collection.  Returns null if
   * no such factor exists.  Will not return
   * factors defined over subsets or supersets of the given collection.
   * @throws RuntimeException If multiple factors exist over the given collection.
   * @see #allFactorsOf(java.util.Collection)
   * @see #addFactor(Factor)
   * @see #factorOf(VarSet)
   */
  public Factor factorOf (Collection c)
  {
    List factors = allFactorsOf (c);
    return firstIfSingleton (factors, c.toString ());
  }



	/**
	 * Returns a copy of this model.  The variable objects are shared
	 * between this model and its copy, but the factor objects are deep-copied.
	 */
	public Factor duplicate ()
	{
		FactorGraph dup = new FactorGraph (numVariables ());
		try {
			for (Iterator it = variablesSet ().iterator(); it.hasNext();) {
				Variable var = (Variable) it.next();
				dup.cacheVariable (var);
			}
			for (Iterator it = factorsIterator (); it.hasNext();) {
				Factor pot = (Factor) it.next();
				dup.addFactor (pot.duplicate ());
			}
		} catch (Exception e) {
			e.printStackTrace ();
		}
	 
		return dup;
	}

	/**
	 * Dumps all the variables and factors of the model to
	 * <tt>System.out</tt> in human-readable text.
	 */
	public void dump ()
  {
    dump (new PrintWriter (new OutputStreamWriter (System.out), true));
  }

  public void dump (PrintWriter out)
	{
		out.println(this);
		out.println("Factors = "+clique2ptl);
		for (Iterator it = factors.iterator(); it.hasNext();) {
			Factor pot = (Factor) it.next();
			out.println(pot.dumpToString ());
		}
	}

  public String dumpToString ()
  {
    StringWriter out = new StringWriter ();
    dump (new PrintWriter (out));
    return out.toString ();
  }


  /**************************************************************************
   *  FACTOR IMPLEMENTATION
   **************************************************************************/

  public double value (Assignment assn)
  {
    return Math.exp (logValue (assn));
  }

  public double value (AssignmentIterator it)
  {
    return value (it.assignment ());
  }

  // uses brute-force algorithm
  public Factor normalize ()
  {
    VariableElimination inf = new VariableElimination ();
    double Z = inf.computeNormalizationFactor (this);
    addFactor (new ConstantFactor (1.0/Z));
    return this;
  }

  public Factor marginalize (Variable[] vars)
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  public Factor marginalize (Collection vars)
  {
    if (numVariables () < 5) {
      return asTable ().marginalize (vars);
    } else {
      throw new UnsupportedOperationException ("not yet implemented");
    }
  }

  public Factor marginalize (Variable var)
  {
    VariableElimination inf = new VariableElimination ();
    return inf.unnormalizedMarginal (this, var);
  }

  public Factor marginalizeOut (Variable var)
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  public Factor marginalizeOut (VarSet varset)
  {
    throw new UnsupportedOperationException ("not yet implemented");    
  }

  public Factor extractMax (Collection vars)
  {
    if (numVariables () < 5) {
      return asTable ().extractMax (vars);
    } else {
      throw new UnsupportedOperationException ("not yet implemented");
    }
  }

  public Factor extractMax (Variable var)
  {
    if (numVariables () < 5) {
      return asTable ().extractMax (var);
    } else {
      throw new UnsupportedOperationException ("not yet implemented");
    }
  }

  public Factor extractMax (Variable[] vars)
  {
    if (numVariables () < 5) {
      return asTable ().extractMax (vars);
    } else {
      throw new UnsupportedOperationException ("not yet implemented");
    }
  }

  // xxx should return an Assignment
  public int argmax ()
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  // Assumes that structure of factor graph is continous --> discrete
  public Assignment sample (Randoms r)
  {
    Variable[] contVars = Factors.continuousVarsOf (this);
    if ((contVars.length == 0) || (contVars.length == numVariables ())) {
      return sampleInternal (r);
    } else {
      Assignment paramAssn = sampleContinuousVars (contVars, r);
      FactorGraph discreteSliceFg = (FactorGraph) this.slice (paramAssn);
      Assignment discreteAssn = discreteSliceFg.sampleInternal (r);
      return Assignment.union (paramAssn, discreteAssn);
    }
  }

  /** Samples the continuous variables in this factor graph. */
  public Assignment sampleContinuousVars (Randoms r)
  {
    Variable[] contVars = Factors.continuousVarsOf (this);
    return sampleContinuousVars (contVars, r);
  }

  private Assignment sampleContinuousVars (Variable[] contVars, Randoms r)
  {
    Collection contFactors = allFactorsContaining (Arrays.asList (contVars));
    FactorGraph contFg = new FactorGraph (contVars);
    for (Iterator it = contFactors.iterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      contFg.multiplyBy (factor);
    }

    return contFg.sampleInternal (r);
  }

  private Assignment sampleInternal (Randoms r)
  {
    ExactSampler sampler = new ExactSampler (r);
    return sampler.sample (this, 1);
  }

  public double sum ()
  {
    VariableElimination inf = new VariableElimination ();
    return inf.computeNormalizationFactor (this);
  }

  public double entropy ()
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  public Factor multiply (Factor dist)
  {
    FactorGraph fg = (FactorGraph) duplicate ();
    fg.addFactor (dist);
    return fg;
  }

  public void multiplyBy (Factor pot)
  {
    addFactor (pot);
  }

  public void exponentiate (double power)
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  public void divideBy (Factor pot)
  {
    if (factors.contains (pot)) {
      removeFactor (pot);
    } else {
      throw new UnsupportedOperationException ("not yet implemented");
    }
  }

  public VarSet varSet ()
  {
    return new HashVarSet (variablesSet());
  }

  public boolean almostEquals (Factor p)
  {
    throw new UnsupportedOperationException ();
  }

  public boolean almostEquals (Factor p, double epsilon)
  {
    throw new UnsupportedOperationException ("not yet implemented");
  }

  public boolean isNaN ()
  {
    for (int fi = 0; fi < factors.size (); fi++) {
      if (getFactor (fi).isNaN ())
        return true;
    }
    return false;
  }

  public double logValue (AssignmentIterator it)
  {
    return logValue (it.assignment ());
  }

  public double logValue (int loc)
  {
    throw new UnsupportedOperationException ();
  }

  public Variable getVariable (int i)
  {
    return get (i);
  }

  // todo: merge this in
  public Factor slice (Assignment assn)
  {
    return slice (assn, null);
  }

  public Factor slice (Assignment assn, Map toSlicedMap)
  {
    return Models.addEvidence (this, assn, toSlicedMap);
  }

  /**************************************************************************
   *  CACHING FACILITY FOR THE USE OF INFERENCE ALGORITHMS
   **************************************************************************/

	transient THashMap inferenceCaches = new THashMap();

	/** 
	 * Caches some information about this graph that is specific to
	 *  a given type of inferencer (e.g., a junction tree).
	 * @param inferencer Class of inferencer that can use this
	 * information
	 * @param info The information to cache.
	 * @see #getInferenceCache
	 */
  public void setInferenceCache (Class inferencer, Object info) 
	{
		inferenceCaches.put (inferencer, info);
  }

	/** 
	 * Caches some information about this graph that is specific to
	 *  a given type of inferencer (e.g., a junction tree).
	 * @param inferencer Class of inferencer which wants the information
	 * @return Whatever object was previously cached for inferencer
	 * using setInferenceCache.  Returns null if no object has been cached.
	 * @see #setInferenceCache
	 */
  public Object getInferenceCache (Class inferencer)
  {
    return inferenceCaches.get (inferencer);
  }

  public void logify ()
  {
    List oldFactors = new ArrayList (factors);
    clear ();
    for (Iterator it = oldFactors.iterator (); it.hasNext ();) {
      AbstractTableFactor factor = (AbstractTableFactor) it.next ();
      addFactor (new LogTableFactor (factor));
    }
  }

  public double logValue (Assignment assn)
  {
    Iterator ptlIter = factorsIterator ();
    double ptlProd = 0;

    while (ptlIter.hasNext())
    {
        ptlProd += ((Factor)ptlIter.next()).logValue (assn);
    }

    return ptlProd;
  }

  public AbstractTableFactor asTable ()
  {
    return TableFactor.multiplyAll (factors).asTable ();
  }

  public String prettyOutputString() { return toString(); }

  public String toString ()
  {
    StringBuffer buf = new StringBuffer ();
    buf.append ("FactorGraph: Variables ");
    for (int i = 0; i < numNodes; i++) {
      Variable var = get (i);
      buf.append (var);
      buf.append (",");
    }
    buf.append ("\n");

    buf.append ("Factors: ");
    for (Iterator it = factors.iterator (); it.hasNext ();) {
      Factor factor = (Factor) it.next ();
      buf.append ("[");
      buf.append (factor.varSet ());
      buf.append ("],");
    }
    buf.append ("\n");

    return buf.toString ();
  }
  public void printAsDot (PrintWriter out)
  {
    out.println ("graph model {");
    outputEdgesAsDot (out);
    out.println ("}");
  }

  private static final String[] colors = { "red", "green", "blue", "yellow" };

  public void printAsDot (PrintWriter out, Assignment assn)
  {
    out.println ("graph model {");
    outputEdgesAsDot (out);
    for (Iterator it = variablesIterator (); it.hasNext();) {
      Variable var = (Variable) it.next ();
      int value = assn.get(var);
      String color = colors[value];
      out.println (var.getLabel ()+" [style=filled fillcolor="+color+"];");
    }
    out.println ("}");
  }

  private void outputEdgesAsDot (PrintWriter out)
  {
    int ptlIdx = 0;
    for (Iterator it = factors ().iterator(); it.hasNext();) {
      Factor ptl = (Factor) it.next ();
      VarSet vars = ptl.varSet ();
      for (Iterator varIt = vars.iterator (); varIt.hasNext ();) {
        Variable var = (Variable) varIt.next ();
        out.print ("PTL"+ptlIdx+" -- "+var.getLabel ());
        out.println (";\n");
      }
      ptlIdx++;
    }
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
    in.readInt ();  // int version = ...
    regenerateCaches ();
  }

}

