/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.inference;


import java.util.*;

import cc.mallet.grmm.types.*;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.Multinomial;

/**
 * Utility class for generating many useful kinds of random graphical
 *  models.
 * Created: Mar 26, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: RandomGraphs.java,v 1.1 2007/10/22 21:37:49 mccallum Exp $
 */
public class RandomGraphs {


  public static double[] generateAttractivePotentialValues (Random r, double edgeWeight)
  {
    double b = Math.abs (r.nextGaussian ()) * edgeWeight;
    double eB = Math.exp (b);
    double eMinusB = Math.exp (-b);
    return new double[] { eB, eMinusB, eMinusB, eB };
  }

  public static double[] generateMixedPotentialValues (Random r, double edgeWeight)
  {
    double b = r.nextGaussian () * edgeWeight;
    double eB = Math.exp (b);
    double eMinusB = Math.exp (-b);
    return new double[] { eB, eMinusB, eMinusB, eB };
  }

  /**
   * Constructs a square grid of a given size with random attractive potentials.
   *  Graphs are generated as follows:
   * <p>
   * We use a spin (i.e., {-1, 1}) representation.  For each edge st,
   *  a single edge weight <tt>w_st</tt> is generated uniformly in (0,d).
   *  Then exponential parameters for the BM representation are chosen by
   * <pre>
   *   theta_st = 4 * w_st
   *   theta_s = 2 (\sum(t in N(s)) w_st)
   * </pre>
   *
   * @param size The length on one edge of the grid.
   * @param edgeWeight A positive number giving the maximum potential strength
   * @param r Object for generating random numbers.
   * @return A randomly-generated undirected model.
   */
  public static UndirectedGrid randomAttractiveGrid (int size, double edgeWeight, Random r)
  {
    UndirectedGrid mdl = new UndirectedGrid (size, size, 2);

    // Do grid from top left down....
    for (int i = 0; i < size-1; i++) {
      for (int j = 0; j < size-1; j++) {
        Variable v = mdl.get (i, j);
        Variable vRight = mdl.get (i + 1, j);
        Variable vDown = mdl.get (i, j + 1);
        mdl.addFactor (v, vRight, generateAttractivePotentialValues (r, edgeWeight));
        mdl.addFactor (v, vDown, generateAttractivePotentialValues (r, edgeWeight));
      }
    }

    // and bottom edge
    for (int i = 0; i < size-1; i++) {
      Variable v = mdl.get (i, size - 1);
      Variable vRight = mdl.get (i + 1, size - 1);
      mdl.addFactor (v, vRight, generateAttractivePotentialValues (r, edgeWeight));
    }

    // and finally right edge
    for (int i = 0; i < size-1; i++) {
      Variable v = mdl.get (size - 1, i);
      Variable vDown = mdl.get (size - 1, i + 1);
      mdl.addFactor (v, vDown, generateAttractivePotentialValues (r, edgeWeight));
    }

    // and node potentials
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        double a = r.nextGaussian () * 0.0625;
        double[] b = new double[] { Math.exp (a), Math.exp (-a) };
        TableFactor ptl = new TableFactor (mdl.get (i, j), b);
        mdl.addFactor (ptl);
      }
    }

    return mdl;
  }


  /**
   * Constructs a square grid of a given size with random repulsive potentials.
   *  This means that if a node takes on a value, its neighbors are more likely
   *  to take opposite values.
   *  Graphs are generated as follows:
   * <p>
   * We use a spin (i.e., {-1, 1}) representation.  For each edge st,
   *  a single edge weight <tt>w_st</tt> is generated uniformly in (0,d).
   *  Then exponential parameters for the BM representation are chosen by
   * <pre>
   *   theta_st = 4 * w_st
   *   theta_s = 2 (\sum(t in N(s)) w_st)
   * </pre>
   *
   * @param size The length on one edge of the grid.
   * @param edgeWeight A positive number giving the maximum ansolute potential strength
   * @param r Object for generating random numbers.
   * @return A randomly-generated undirected model.
   */
  public static UndirectedGrid randomRepulsiveGrid (int size, double edgeWeight, Random r)
  {
    return randomAttractiveGrid (size, -edgeWeight, r);
  }

  /**
   * Constructs a square grid of a given size with random frustrated potentials.
   *  This means that some potentials will be attractive (want to make their
   *  neighbors more like them) and some will be repulsive (want to make their
   *  neighbors different).
   *  Graphs are generated as follows:
   * <p>
   * We use a spin (i.e., {-1, 1}) representation.  For each edge st,
   *  a single edge weight <tt>w_st</tt> is generated uniformly in (0,d).
   *  Then exponential parameters for the BM representation are chosen by
   * <pre>
   *   theta_st = 4 * w_st
   *   theta_s = 2 (\sum(t in N(s)) w_st)
   * </pre>
   *
   * @param size The length on one edge of the grid.
   * @param edgeWeight A positive number giving the maximum potential strength
   * @param r Object for generating random numbers.
   * @return A randomly-generated undirected model.
   */
  public static UndirectedGrid randomFrustratedGrid (int size, double edgeWeight, Random r)
  {
    UndirectedGrid mdl = new UndirectedGrid (size, size, 2);

    // Do grid from top left down....
    for (int i = 0; i < size-1; i++) {
      for (int j = 0; j < size-1; j++) {
        Variable v = mdl.get(i,j);
        Variable vRight = mdl.get(i+1,j);
        Variable vDown = mdl.get(i,j+1);
        mdl.addFactor (v, vRight, generateMixedPotentialValues (r, edgeWeight));
        mdl.addFactor (v, vDown, generateMixedPotentialValues (r, edgeWeight));
      }
    }

    // and bottom edge
    for (int i = 0; i < size-1; i++) {
      Variable v = mdl.get (i, size - 1);
      Variable vRight = mdl.get (i + 1, size - 1);
      mdl.addFactor (v, vRight, generateMixedPotentialValues (r, edgeWeight));
    }

    // and finally right edge
    for (int i = 0; i < size-1; i++) {
      Variable v = mdl.get (size - 1, i);
      Variable vDown = mdl.get (size - 1, i + 1);
      mdl.addFactor (v, vDown, generateMixedPotentialValues (r, edgeWeight));
    }

    // and node potentials
    addRandomNodePotentials (r, mdl);

    return mdl;
  }

  public static UndirectedModel randomFrustratedTree (int size, int maxChildren, double edgeWeight, Random r)
  {
    UndirectedModel mdl = new UndirectedModel ();
    List leaves = new ArrayList ();

    Variable root = new Variable (2);
    leaves.add (root);

    while (mdl.numVariables () < size) {
      Variable parent = (Variable) removeRandomElement (leaves, r);
      int numChildren = r.nextInt (maxChildren) + 1;
      for (int ci = 0; ci < numChildren; ci++) {
        Variable child = new Variable (2);
        double[] vals = generateMixedPotentialValues (r, edgeWeight);
        mdl.addFactor (parent, child, vals);
        leaves.add (child);
      }
    }

    addRandomNodePotentials (r, mdl);

    return mdl;
  }

  private static Object removeRandomElement (List l, Random r)
  {
    int idx = r.nextInt (l.size ());
    Object obj = l.get (idx);
    l.remove (idx);
    return obj;
  }

  public static void addRandomNodePotentials (Random r, FactorGraph mdl)
  {
    int size = mdl.numVariables ();
    for (int i = 0; i < size; i++) {
        Variable var = mdl.get (i);
        TableFactor ptl = randomNodePotential (r, var);
        mdl.addFactor (ptl);
    }
  }

  public static TableFactor randomNodePotential (Random r, Variable var)
  {
    double a = r.nextGaussian ();
    double[] b = new double[] { Math.exp(a), Math.exp(-a) };
    TableFactor ptl = new TableFactor (var, b);
    return ptl;
  }

  public static FactorGraph createUniformChain (int length)
  {
    Variable[] vars = new Variable[length];
    for (int i = 0; i < length; i++)
      vars[i] = new Variable (2);

    FactorGraph mdl = new UndirectedModel (vars);
    for (int i = 0; i < length - 1; i++) {
      double[] probs = new double[4];
      Arrays.fill (probs, 1.0);
      mdl.addFactor (vars[i], vars[i + 1], probs);
    }

    return mdl;
  }

  public static FactorGraph createUniformGrid (int length)
  {
    return createGrid (new UniformFactorGenerator (), length);
  }

  public static FactorGraph createRandomChain (cc.mallet.util.Randoms r, int length)
  {
    Variable[] vars = new Variable[length];
    for (int i = 0; i < length; i++)
      vars[i] = new Variable (2);

    Dirichlet dirichlet = new Dirichlet (new double[] { 1, 1, 1, 1 });

    FactorGraph mdl = new FactorGraph (vars);
    for (int i = 0; i < length - 1; i++) {
      Multinomial m = dirichlet.randomMultinomial (r);
      double[] probs = m.getValues ();
      mdl.addFactor (vars[i], vars[i + 1], probs);
    }

    return mdl;
  }

  public static interface FactorGenerator {
    Factor nextFactor (VarSet vars);
  }

  public static class UniformFactorGenerator implements FactorGenerator {
      public Factor nextFactor (VarSet vars)
      {
        double[] probs = new double [vars.weight ()];
        Arrays.fill (probs, 1.0);
        return new TableFactor (vars, probs);
      }
  }

  public static UndirectedModel createGrid (FactorGenerator gener, int size)
  {
    UndirectedGrid grid = new UndirectedGrid (size, size, 2);
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size - 1; y++) {
        Variable v1 = grid.get(x, y);
        Variable v2 = grid.get(x, y+1);
        VarSet vars = new HashVarSet (new Variable[] { v1, v2 });
        Factor factor = gener.nextFactor (vars);
        grid.addFactor (factor);
      }
    }

    // add left-right edges
    for (int x = 0; x < size - 1; x++) {
      for (int y = 0; y < size; y++) {
        Variable v1 = grid.get(x, y);
        Variable v2 = grid.get(x+1,y);
        VarSet vars = new HashVarSet (new Variable[] { v1, v2 });
        Factor factor = gener.nextFactor (vars);
        grid.addFactor (factor);
      }
    }

    return grid;
  }

  public static FactorGraph createGridWithObs (FactorGenerator gridGener, FactorGenerator obsGener, int size)
  {
    List allVars = new ArrayList (2 * size * size);
    Variable[][] gridVars = new Variable[size][size];
    Variable[][] obsVars = new Variable[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        gridVars[i][j] = new Variable (2);
        gridVars[i][j].setLabel ("GRID["+i+"]["+j+"]");
        obsVars[i][j] = new Variable (2);
        obsVars[i][j].setLabel ("OBS["+i+"]["+j+"]");

        allVars.add (gridVars[i][j]);
        allVars.add (obsVars[i][j]);
      }
    }

    FactorGraph mdl = new FactorGraph ((Variable[]) allVars.toArray (new Variable[0]));

    // add grid edges
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        Variable var0 = gridVars[i][j];

        if (i < size-1) {
          Variable varR = gridVars[i + 1][j];
          HashVarSet clique = new HashVarSet (new Variable[] { var0, varR });
          Factor factor = gridGener.nextFactor (clique);
          mdl.addFactor (factor);
        }

        if (j < size-1) {
          Variable varD = gridVars[i][j + 1];
          HashVarSet clique = new HashVarSet (new Variable[] { var0, varD });
          Factor factor = gridGener.nextFactor (clique);
          mdl.addFactor (factor);
        }
      }
    }

    // add obs edges
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        Variable gridVar = gridVars[i][j];
        Variable obsVar = obsVars[i][j];
        HashVarSet clique = new HashVarSet (new Variable[] { gridVar, obsVar });
        Factor factor = obsGener.nextFactor (clique);
        mdl.addFactor (factor);
      }
    }

    return mdl;
  }

}
