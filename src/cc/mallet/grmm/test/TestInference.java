/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.test;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.*;
import java.util.Random;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;

import cc.mallet.grmm.inference.*;
import cc.mallet.grmm.types.*;
import cc.mallet.grmm.util.GeneralUtils;
import cc.mallet.grmm.util.ModelReader;
import cc.mallet.types.Dirichlet;
import cc.mallet.types.Matrix;
import cc.mallet.types.Matrixn;
import cc.mallet.types.tests.TestSerializable;
import cc.mallet.util.*;
//import cc.mallet.util.Random;

import gnu.trove.list.array.TDoubleArrayList;


/**
 *  Torture tests of inference in GRMM.  Well, actually, they're
 *   not all that torturous, but hopefully they're at least
 *   somewhat disconcerting.
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: TestInference.java,v 1.1 2007/10/22 21:37:40 mccallum Exp $
 */
public class TestInference extends TestCase {

  private static Logger logger = MalletLogger.getLogger(TestInference.class.getName());
  private static double APPX_EPSILON = 0.15;

  final public Class[] algorithms = {
    BruteForceInferencer.class,
    VariableElimination.class,
    JunctionTreeInferencer.class,
  };

  final public Class[] appxAlgs = {
    TRP.class,
    LoopyBP.class,
  };

  // only used for logJoint test for now
  final public Class[] allAlgs = {
//    BruteForceInferencer.class,
    JunctionTreeInferencer.class,
    TRP.class,
//	  VariableElimination.class,
    LoopyBP.class,
  };

  final public Class[] treeAlgs = {
    TreeBP.class,
  };

  List modelsList;
  UndirectedModel[] models;
  FactorGraph[] trees;
  Factor[][] treeMargs;


  public TestInference(String name)
  {
    super(name);
  }


  private static UndirectedModel createChainGraph()
  {
    Variable[] vars = new Variable[5];
    UndirectedModel model = new UndirectedModel();

    try {

      // Add all variables to model
      for (int i = 0; i < 5; i++) {
        vars[i] = new Variable(2);
      }

      // Add some links
      double probs[] = {0.9, 0.1, 0.1, 0.9};
      for (int i = 0; i < 4; i++) {
        Variable[] pair = { vars[i], vars[i + 1], };
        TableFactor pot = new TableFactor (pair, probs);
        model.addFactor (pot);
      }

    } catch (Exception e) {
      e.printStackTrace();
      assertTrue(false);
    }

    return model;
  }


  private static UndirectedModel createTriangle()
  {
    Variable[] vars = new Variable[3];
    for (int i = 0; i < 3; i++) {
      vars[i] = new Variable (2);
    }
    UndirectedModel model = new UndirectedModel (vars);
    double[][] pots = new double[][] { { 0.2, 0.8, 0.1, 0.9 },
            { 0.7, 0.3, 0.5, 0.5 },
            { 0.6, 0.4, 0.8, 0.2 },
            { 0.35, 0.65 } };
    // double[][] pots = new double[] [] { {

    model.addFactor (vars[0], vars[1], pots[0]);
    model.addFactor (vars[1], vars[2], pots[1]);
    model.addFactor (vars[2], vars[0], pots[2]);

    TableFactor pot = new TableFactor (new Variable[] { vars[0] }, pots[3]);
    model.addFactor (pot);

    return model;
  }


  private static TableFactor randomEdgePotential(Random r,
                                                 Variable v1, Variable v2)
  {
    int max1 = v1.getNumOutcomes();
    int max2 = v2.getNumOutcomes();

    Matrix phi = new Matrixn(new int[]{max1, max2});
    for (int i = 0; i < v1.getNumOutcomes(); i++) {
      for (int j = 0; j < v2.getNumOutcomes(); j++) {
        phi.setValue(new int[]{i, j}, r.nextDouble ()); // rescale(r.nextDouble()));
      }
    }
    return new TableFactor
            (new Variable[]{v1, v2}, phi);
  }


  private static TableFactor randomNodePotential(Random r, Variable v)
  {
    int max = v.getNumOutcomes();

    Matrix phi = new Matrixn(new int[]{max});
    for (int i = 0; i < v.getNumOutcomes(); i++) {
      phi.setSingleValue(i, rescale(r.nextDouble()));
    }
    return new TableFactor
            (new Variable[]{v}, phi);
  }


  // scale d into range 0.2..0.8
  private static double rescale(double d)
  {
    return 0.2 + 0.6 * d;
  }


  private static UndirectedModel createRandomGraph(int numV, int numOutcomes, Random r)
  {
    Variable[] vars = new Variable[numV];
    for (int i = 0; i < numV; i++) {
      vars[i] = new Variable(numOutcomes);
    }

    UndirectedModel model = new UndirectedModel(vars);
    for (int i = 0; i < numV; i++) {
      boolean hasOne = false;
      for (int j = i + 1; j < numV; j++) {
        if (r.nextBoolean()) {
          hasOne = true;
          model.addFactor (randomEdgePotential (r, vars[i], vars[j]));
        }
      }

      // If vars [i] has no edge potential, add a node potential
      //  To keep things simple, we'll require the potential to be normalized.
      if (!hasOne) {
        Factor pot = randomNodePotential(r, vars[i]);
        pot.normalize();
        model.addFactor (pot);
      }
    }

    // Ensure exactly one connected component
    for (int i = 0; i < numV; i++) {
      for (int j = i + 1; j < numV; j++) {
        if (!model.isConnected(vars[i], vars[j])) {
          Factor ptl = randomEdgePotential (r, vars[i], vars[j]);
          model.addFactor (ptl);
        }
      }
    }

    return model;
  }


  public static UndirectedModel createRandomGrid(int w, int h, int maxOutcomes, Random r)
  {
    Variable[][] vars = new Variable[w][h];
    UndirectedModel mdl = new UndirectedModel(w * h);
    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        vars[i][j] = new Variable(r.nextInt(maxOutcomes - 1) + 2);
      }
    }

    for (int i = 0; i < w; i++) {
      for (int j = 0; j < h; j++) {
        Factor ptl;
        if (i < w - 1) {
          ptl = randomEdgePotential (r, vars[i][j], vars[i + 1][j]);
          mdl.addFactor (ptl);
        }
        if (j < h - 1) {
          ptl = randomEdgePotential (r, vars[i][j], vars[i][j + 1]);
          mdl.addFactor (ptl);
        }
      }
    }

    return mdl;
  }


  private UndirectedModel createRandomTree(int nnodes, int maxOutcomes, Random r)
  {
    Variable[] vars = new Variable[nnodes];
    UndirectedModel mdl = new UndirectedModel(nnodes);
    for (int i = 0; i < nnodes; i++) {
      vars[i] = new Variable(r.nextInt(maxOutcomes - 1) + 2);
    }
    //  Add some random edges
    for (int i = 0; i < nnodes; i++) {
      for (int j = i + 1; j < nnodes; j++) {
        if (!mdl.isConnected(vars[i], vars[j]) && r.nextBoolean()) {
          Factor ptl = randomEdgePotential (r, vars[i], vars[j]);
          mdl.addFactor (ptl);
        }
      }
    }
    // Ensure exactly one connected component
    for (int i = 0; i < nnodes; i++) {
      for (int j = i + 1; j < nnodes; j++) {
        if (!mdl.isConnected(vars[i], vars[j])) {
          System.out.println ("forced edge: " + i + " " + j);
          Factor ptl = randomEdgePotential (r, vars[i], vars[j]);
          mdl.addFactor (ptl);
        }
      }
    }
    return mdl;
  }


  public static List createTestModels()
  {
    Random r = new Random(42);
    // These models are all small so that we can run the brute force
    // inferencer on them.
    FactorGraph[] mdls = new FactorGraph[]{
      createTriangle(),
      createChainGraph(),
      createRandomGraph(3, 2, r),
      createRandomGraph(3, 3, r),
      createRandomGraph(6, 3, r),
      createRandomGraph(8, 2, r),
      createRandomGrid(3, 2, 4, r),
      createRandomGrid(4, 3, 2, r),
    };
    return new ArrayList(Arrays.asList(mdls));
  }


  public void testUniformJoint () throws Exception
  {
    FactorGraph mdl = RandomGraphs.createUniformChain (3);
    double expected = -Math.log (8);
    for (int i = 0; i < allAlgs.length; i++) {
      Inferencer inf = (Inferencer) allAlgs[i].newInstance ();
      inf.computeMarginals (mdl);
      for (AssignmentIterator it = mdl.assignmentIterator (); it.hasNext ();) {
        Assignment assn = it.assignment ();
        double actual = inf.lookupLogJoint (assn);
        assertEquals ("Incorrect joint for inferencer "+inf, expected, actual, 1e-5);
        it.advance ();
      }
    }
  }

  public void testJointConsistent () throws Exception
  {
    for (int i = 0; i < allAlgs.length; i++) {
//      for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      { int mdlIdx = 13;
        Inferencer inf = (Inferencer) allAlgs[i].newInstance();
        try {
          FactorGraph mdl = models[mdlIdx];
          inf.computeMarginals(mdl);
          Assignment assn = new Assignment (mdl, new int [mdl.numVariables ()]);
          assertEquals (Math.log (inf.lookupJoint (assn)), inf.lookupLogJoint (assn), 1e-5);
        } catch (UnsupportedOperationException e) {
          // LoopyBP only handles edge ptls
          logger.warning("Skipping (" + mdlIdx + "," + i + ")\n" + e);
          throw e;
//          continue;
        }
      }
    }
  }

  public void testFactorizedJoint() throws Exception
  {
    Inferencer[][] infs = new Inferencer[allAlgs.length][models.length];

    for (int i = 0; i < allAlgs.length; i++) {
      for (int mdl = 0; mdl < models.length; mdl++) {
        Inferencer alg = (Inferencer) allAlgs[i].newInstance();
        if (alg instanceof TRP) {
          ((TRP)alg).setRandomSeed (1231234);
        }

        try {
          alg.computeMarginals(models[mdl]);
          infs[i][mdl] = alg;
        } catch (UnsupportedOperationException e) {
          // LoopyBP only handles edge ptls
          logger.warning("Skipping (" + mdl + "," + i + ")\n" + e);
          throw e;
//          continue;
        }
      }
    }

    /* Ensure that lookupLogJoint() consistent */
    int alg1 = 0;  // Brute force
    for (int alg2 = 1; alg2 < allAlgs.length; alg2++) {
      for (int mdl = 0; mdl < models.length; mdl++) {
        Inferencer inf1 = infs[alg1][mdl];
        Inferencer inf2 = infs[alg2][mdl];

        if ((inf1 == null) || (inf2 == null)) {
          continue;
        }

        Iterator it = models[mdl].assignmentIterator();
        while (it.hasNext()) {
          try {
            Assignment assn = (Assignment) it.next();
            double joint1 = inf1.lookupLogJoint(assn);
            double joint2 = inf2.lookupLogJoint(assn);

            logger.finest("logJoint: " + inf1 + " " + inf2
                          + "  Model " + mdl
                          + "  Assn: " + assn
                          + "  INF1: " + joint1 + "\n"
                          + "  INF2: " + joint2 + "\n");

            assertTrue("logJoint not equal btwn " + GeneralUtils.classShortName (inf1) + " "
                       + " and " + GeneralUtils.classShortName (inf2) + "\n"
                       + "  Model " + mdl + "\n"
                       + "  INF1: " + joint1 + "\n"
                       + "  INF2: " + joint2 + "\n",
                       Math.abs(joint1 - joint2) < 0.2);

            double joint3 = inf1.lookupJoint(assn);
            assertTrue("logJoint & joint not consistent\n  "
                       + "Model " + mdl + "\n" + assn,
                       Maths.almostEquals(joint3, Math.exp(joint1)));
          } catch (UnsupportedOperationException e) {
            // VarElim doesn't compute log joints. Let it slide
            logger.warning("Skipping " + inf1 + " -> " + inf2 + "\n" + e);
            continue;
          }
        }
      }
    }
  }


  public void testMarginals() throws Exception
  {
    Factor[][][] joints = new Factor[models.length][][];
    Inferencer[] appxInferencers = constructAllAppxInferencers ();

    int numExactAlgs = algorithms.length;
    int numAppxAlgs = appxInferencers.length;
    int numAlgs = numExactAlgs + numAppxAlgs;

    for (int mdl = 0; mdl < models.length; mdl++) {
      joints[mdl] = new Factor[numAlgs][];
    }

    /* Query every known graph with every known alg. */
    for (int i = 0; i < algorithms.length; i++) {
      for (int mdl = 0; mdl < models.length; mdl++) {
        Inferencer alg = (Inferencer) algorithms[i].newInstance();
        logger.fine("Computing marginals for model " + mdl + " alg " + alg);
        alg.computeMarginals(models[mdl]);
        joints[mdl][i] = collectAllMarginals (models [mdl], alg);
      }
    }

    logger.fine("Checking that results are consistent...");

    /* Now, make sure the exact marginals are consistent for
     *  the same model.                       */
    for (int mdl = 0; mdl < models.length; mdl++) {
      int maxV = models[mdl].numVariables ();
      for (int vrt = 0; vrt < maxV; vrt++) {
        for (int alg1 = 0; alg1 < algorithms.length; alg1++) {
          for (int alg2 = 0; alg2 < algorithms.length; alg2++) {
            Factor joint1 = joints[mdl][alg1][vrt];
            Factor joint2 = joints[mdl][alg2][vrt];
            try {
              // By the time we get here, a joint is null only if
              // there was an UnsupportedOperationException.
              if ((joint1 != null) && (joint2 != null)) {
                assertTrue(joint1.almostEquals(joint2));
              }
            } catch (AssertionFailedError e) {
              System.out.println("\n************************************\nTest FAILED\n\n");
              System.out.println("Model " + mdl + " Vertex " + vrt);
              System.out.println("Algs " + alg1 + " and " + alg2 + " not consistent.");

              System.out.println("MARGINAL from " + alg1);
              System.out.println(joint1);

              System.out.println("MARGINAL from " + alg2);
              System.out.println(joint2);

              System.out.println("Marginals from " + alg1 + ":");
              for (int i = 0; i < maxV; i++) {
                System.out.println(joints[mdl][alg1][i]);
              }

              System.out.println("Marginals from " + alg2 + ":");
              for (int i = 0; i < maxV; i++) {
                System.out.println(joints[mdl][alg2][i]);
              }

              models[mdl].dump ();

              throw e;
            }
          }
        }
      }
    }

    // Compare all approximate algorithms against brute force.
    logger.fine("Checking the approximate algorithms...");

    int alg2 = 0; // Brute force
    for (int appxIdx = 0; appxIdx < appxInferencers.length; appxIdx++) {
      Inferencer alg = appxInferencers [appxIdx];

      for (int mdl = 0; mdl < models.length; mdl++) {
        logger.finer("Running inference alg " + alg + " with model " + mdl);

        try {
          alg.computeMarginals(models[mdl]);
        } catch (UnsupportedOperationException e) {
          // LoopyBP does not support vertex potentials.
          //  We'll let that slide.
          if (alg instanceof AbstractBeliefPropagation) {
            logger.warning("Skipping model " + mdl + " for alg " + alg
                           + "\nInference unsupported.");
            continue;
          } else {
            throw e;
          }
        }

        /* lookup all marginals */
        int vrt = 0;
        int alg1 = numExactAlgs + appxIdx;
        int maxV = models[mdl].numVariables ();
        joints[mdl][alg1] = new Factor[maxV];
        for (Iterator it = models[mdl].variablesSet ().iterator();
             it.hasNext();
             vrt++) {
          Variable var = (Variable) it.next();
          logger.finer("Lookup marginal for model " + mdl + " vrt " + var + " alg " + alg);
          Factor ptl = alg.lookupMarginal(var);
          joints[mdl][alg1][vrt] = ptl.duplicate();
        }

        for (vrt = 0; vrt < maxV; vrt++) {
          Factor joint1 = joints[mdl][alg1][vrt];
          Factor joint2 = joints[mdl][alg2][vrt];
          try {
            assertTrue(joint1.almostEquals(joint2, APPX_EPSILON));
          } catch (AssertionFailedError e) {
            System.out.println("\n************************************\nAppx Marginal Test FAILED\n\n");
            System.out.println("Inferencer: " + alg);
            System.out.println("Model " + mdl + " Vertex " + vrt);
            System.out.println(joint1.dumpToString ());
            System.out.println(joint2.dumpToString ());
            models[mdl].dump ();

            System.out.println("All marginals:");
            for (int i = 0; i < maxV; i++) {
              System.out.println(joints[mdl][alg1][i].dumpToString ());
            }

            System.out.println("Correct marginals:");
            for (int i = 0; i < maxV; i++) {
              System.out.println(joints[mdl][alg2][i].dumpToString ());
            }

            throw e;
          }

        }
      }
    }

    System.out.println("Tested " + models.length + " undirected models.");
  }

  private Inferencer[] constructAllAppxInferencers () throws IllegalAccessException, InstantiationException
  {
    List algs = new ArrayList (appxAlgs.length * 2);
    for (int i = 0; i < appxAlgs.length; i++) {
      algs.add (appxAlgs[i].newInstance ());
    }

    // Add a few that don't fit
    algs.add (new TRP ().setMessager (new AbstractBeliefPropagation.SumProductMessageStrategy (0.8)));
    algs.add (new LoopyBP ().setMessager (new AbstractBeliefPropagation.SumProductMessageStrategy (0.8)));
    algs.add (new SamplingInferencer (new GibbsSampler (10000), 10000));
    algs.add (new SamplingInferencer (new ExactSampler (), 1000));

    return (Inferencer[]) algs.toArray (new Inferencer [algs.size ()]);
  }

  private Inferencer[] constructMaxProductInferencers () throws IllegalAccessException, InstantiationException
  {
    List algs = new ArrayList ();
    algs.add (JunctionTreeInferencer.createForMaxProduct ());
    algs.add (TRP.createForMaxProduct ());
    algs.add (LoopyBP.createForMaxProduct ());

    return (Inferencer[]) algs.toArray (new Inferencer [algs.size ()]);
  }

  private Factor[] collectAllMarginals (FactorGraph mdl, Inferencer alg)
  {
    int vrt = 0;
    int numVertices = mdl.numVariables ();
    Factor[] collector = new Factor[numVertices];
    for (Iterator it = mdl.variablesSet ().iterator();
         it.hasNext();
         vrt++) {
      Variable var = (Variable) it.next();
      try {
        collector[vrt] = alg.lookupMarginal(var);
        assert collector [vrt] != null
                : "Query returned null for model " + mdl + " vertex " + var + " alg " + alg;
      } catch (UnsupportedOperationException e) {
        // Allow unsupported inference to slide with warning
        logger.warning("Warning: Skipping model " + mdl + " for alg " + alg
                       + "\n  Inference unsupported.");
      }
    }
    return collector;
  }


  public void testQuery () throws Exception
  {
    java.util.Random rand = new java.util.Random (15667);
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      FactorGraph mdl = models [mdlIdx];
      int size = rand.nextInt (3) + 2;
      size = Math.min (size, mdl.varSet ().size ());
      Collection vars = CollectionUtils.subset (mdl.variablesSet (), size, rand);
      Variable[] varArr = (Variable[]) vars.toArray (new Variable [0]);
      Assignment assn = new Assignment (varArr, new int [size]);

      BruteForceInferencer brute = new BruteForceInferencer();
      Factor joint = brute.joint(mdl);
      double marginal = joint.marginalize(vars).value (assn);

      for (int algIdx = 0; algIdx < appxAlgs.length; algIdx++) {
        Inferencer alg = (Inferencer) appxAlgs[algIdx].newInstance();
        if (alg instanceof TRP) continue; // trp can't handle disconnected models, which arise during query()
        double returned = alg.query (mdl, assn);
        assertEquals ("Failure on model "+mdlIdx+" alg "+alg, marginal, returned, APPX_EPSILON);
      }
    }

    logger.info ("Test testQuery passed.");
  }

  // be careful that caching of inference algorithms does not affect results here.
  public void testSerializable () throws Exception
  {
    for (int i = 0; i < algorithms.length; i++) {
      Inferencer alg = (Inferencer) algorithms[i].newInstance();
      testSerializationForAlg (alg);
    }
    for (int i = 0; i < appxAlgs.length; i++) {
       Inferencer alg = (Inferencer) appxAlgs[i].newInstance();
       testSerializationForAlg (alg);
     }

    Inferencer[] maxAlgs = constructMaxProductInferencers ();
    for (int i = 0; i < maxAlgs.length; i++) {
       testSerializationForAlg (maxAlgs [i]);
     }
  }

  private void testSerializationForAlg (Inferencer alg)
          throws IOException, ClassNotFoundException
  {
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      FactorGraph mdl = models [mdlIdx];
      // Copy the inferencer before calling b/c of random seed issues.
      Inferencer alg2 = (Inferencer) TestSerializable.cloneViaSerialization (alg);

      alg.computeMarginals(mdl);
      Factor[] pre = collectAllMarginals (mdl, alg);

      alg2.computeMarginals (mdl);
      Factor[] post2 = collectAllMarginals (mdl, alg2);
      compareMarginals ("Error comparing marginals after serialzation on model "+mdl,
                        pre, post2);
    }
  }

  private void compareMarginals (String msg, Factor[] pre, Factor[] post)
  {
    for (int i = 0; i < pre.length; i++) {
      Factor ptl1 = pre[i];
      Factor ptl2 = post[i];
      assertTrue (msg + "\n" + ptl1.dumpToString () + "\n" + ptl2.dumpToString (),
                  ptl1.almostEquals (ptl2, 1e-3));
    }
  }

  // This is really impossible after the change to the factor graph representation
  // Tests the measurement of numbers of messages sent
  public void ignoreTestNumMessages ()
  {
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      UndirectedModel mdl = models [mdlIdx];

      TRP trp = new TRP ();
      trp.computeMarginals (mdl);
      int expectedMessages = (mdl.numVariables () - 1) * 2
                             * trp.iterationsUsed();
      assertEquals (expectedMessages, trp.getTotalMessagesSent ());

      LoopyBP loopy = new LoopyBP ();
      loopy.computeMarginals (mdl);
      expectedMessages = mdl.getEdgeSet().size() * 2
                         * loopy.iterationsUsed();
      assertEquals (expectedMessages, loopy.getTotalMessagesSent ());
    }
  }


  private UndirectedModel createJtChain()
  {
    int numNodes = 4;
    Variable[] nodes = new Variable[numNodes];
    for (int i = 0; i < numNodes; i++) {
      nodes[i] = new Variable(2);
    }

    Factor[] pots = new TableFactor[]{
      new TableFactor (new Variable[]{nodes[0], nodes[1]},
                       new double[]{1, 2, 5, 4}),
      new TableFactor (new Variable[]{nodes[1], nodes[2]},
                       new double[]{4, 2, 4, 1}),
      new TableFactor (new Variable[]{nodes[2], nodes[3]},
                       new double[]{7, 3, 6, 9}),
    };
    for (int i = 0; i < pots.length; i++) {
      pots[i].normalize();
    }

    UndirectedModel uGraph = new UndirectedModel();
    for (int i = 0; i < numNodes - 1; i++) {
      uGraph.addFactor (pots[i]);
    }

    return uGraph;
  }


  private static final int JT_CHAIN_TEST_TREE = 2;

  private void createTestTrees()
  {
    Random r = new Random(185);
    trees = new FactorGraph[] {
      RandomGraphs.createUniformChain (2),
      RandomGraphs.createUniformChain (4),
      createJtChain(),
      createRandomGrid(5, 1, 3, r),
      createRandomGrid(6, 1, 2, r),
      createRandomTree(10, 2, r),
      createRandomTree(10, 2, r),
      createRandomTree(8, 3, r),
      createRandomTree(8, 3, r),
    };
    modelsList.addAll(Arrays.asList(trees));
  }


  private void computeTestTreeMargs()
  {
    treeMargs = new Factor[trees.length][];
    BruteForceInferencer brute = new BruteForceInferencer();
    for (int i = 0; i < trees.length; i++) {
      FactorGraph mdl = trees[i];
      Factor joint = brute.joint(mdl);
      treeMargs[i] = new Factor[mdl.numVariables ()];
      for (Iterator it = mdl.variablesIterator (); it.hasNext();) {
        Variable var = (Variable) it.next();
        treeMargs[i][mdl.getIndex(var)] = joint.marginalize(var);
      }
    }
  }


public void testJtConsistency() {
  for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
    UndirectedModel mdl = models[mdlIdx];
    JunctionTreeInferencer jti = new JunctionTreeInferencer();
    JunctionTree jt = jti.buildJunctionTree(mdl);

    for (Iterator it = jt.getVerticesIterator(); it.hasNext();) {
      VarSet parent = (VarSet) it.next();
      for (Iterator it2 = jt.getChildren(parent).iterator(); it2.hasNext();) {
        VarSet child = (VarSet) it2.next();
        Factor ptl = jt.getSepsetPot(parent, child);
        Set intersection = parent.intersection (child);
        assertTrue (intersection.equals (ptl.varSet()));
      }
    }
  }
}

  private void compareTrpJoint(Factor joint, TRP trp)
  {
    Assignment assn = null;
    double prob1 = 0.0, prob2 = 0.0;
    try {
      VarSet all = new HashVarSet (joint.varSet());
      for (Iterator it = all.assignmentIterator(); it.hasNext();) {
        assn = (Assignment) it.next();
        prob1 = trp.lookupJoint(assn);
        prob2 = joint.value (assn);
//				assertTrue (Maths.almostEquals (prob1, prob2));
        assertTrue(Math.abs(prob1 - prob2) < 0.01);
      }
    } catch (AssertionFailedError e) {
      System.out.println("*****************************************\nTEST FAILURE in compareTrpJoint");
      System.out.println("*****************************************\nat");
      System.out.println(assn);
      System.out.println("Expected: " + prob2);
      System.out.println("TRP: " + prob1);

      System.out.println("*****************************************\nExpected joint");
      System.out.println(joint);
      System.out.println("*****************************************\nTRP dump");
      trp.dump();
      throw e;
    }
  }


  public void testTrp()
  {
    final UndirectedModel model = createTriangle();
    TRP trp = new TRP().setTerminator (new TRP.IterationTerminator(200));

    BruteForceInferencer brute = new BruteForceInferencer();
    Factor joint = brute.joint(model);

    trp.computeMarginals(model);

    // Check joint
//		DiscretePotential joint = brute.joint (model);
    compareTrpJoint(joint, trp);

    // Check all marginals
    try {
      for (Iterator it = model.variablesIterator (); it.hasNext();) {
        Variable var = (Variable) it.next();
        Factor marg1 = trp.lookupMarginal(var);
        Factor marg2 = joint.marginalize (var);
        assertTrue(marg1.almostEquals(marg2, APPX_EPSILON));
      }
      for (Iterator it = model.factorsIterator(); it.hasNext();) {
        Factor factor = (Factor) it.next ();
        Factor marg1 = trp.lookupMarginal (factor.varSet ());
        Factor marg2 = joint.marginalize (factor.varSet ());
        assertTrue(marg1.almostEquals(marg2, APPX_EPSILON));
      }

    } catch (AssertionFailedError e) {
      System.out.println("\n*************************************\nTEST FAILURE in compareTrpMargs");
//    System.out.println(marg1);
//    System.out.println(marg2);
      System.out.println("*************************************\nComplete model:\n\n");
      model.dump ();
      System.out.println("*************************************\nTRP margs:\n\n");
      trp.dump();
      System.out.println("**************************************\nAll correct margs:\n");
      for (Iterator it2 = model.variablesIterator (); it2.hasNext();) {
        Variable v2 = (Variable) it2.next();
        brute.computeMarginals (model);
        System.out.println(brute.lookupMarginal(v2));
      }
      throw e;
    }
  }


  public void testTrpJoint()
  {
    FactorGraph model = createTriangle();
    TRP trp = new TRP().setTerminator (new TRP.IterationTerminator(25));
    trp.computeMarginals(model);

    // For each assignment to the model, check that
    // TRP.lookupLogJoint and TRP.lookupJoint are consistent
    VarSet all = new HashVarSet (model.variablesSet ());
    for (Iterator it = all.assignmentIterator(); it.hasNext();) {
      Assignment assn = (Assignment) it.next();
      double log = trp.lookupLogJoint(assn);
      double prob = trp.lookupJoint(assn);
      assertTrue(Maths.almostEquals(Math.exp(log), prob));
    }

    logger.info("Test trpJoint passed.");
  }


  /** Tests that running TRP doesn't inadvertantly change potentials
   in the original graph. */
  public void testTrpNonDestructivity()
  {
    FactorGraph model = createTriangle();
    TRP trp = new TRP(new TRP.IterationTerminator(25));
    BruteForceInferencer brute = new BruteForceInferencer();
    Factor joint1 = brute.joint(model);
    trp.computeMarginals(model);
    Factor joint2 = brute.joint(model);
    assertTrue(joint1.almostEquals(joint2));

    logger.info("Test trpNonDestructivity passed.");
  }

  public void testTrpReuse()
  {
    TRP trp1 = new TRP(new TRP.IterationTerminator(25));
    for (int i = 0; i < models.length; i++) {
      trp1.computeMarginals(models[i]);
    }

    // Hard to do automatically right now...
    logger.info("Please ensure that all instantiations above run for 25 iterations.");

    // Ensure that all edges touched works...
    UndirectedModel mdl = models[0];
    final Tree tree = trp1.new AlmostRandomTreeFactory().nextTree(mdl);
    TRP trp2 = new TRP(new TRP.TreeFactory() {
      public Tree nextTree(FactorGraph mdl)
      {
        return tree;
      }
    });
    trp2.computeMarginals(mdl);
    logger.info("Ensure that the above instantiation ran for 1000 iterations with a warning.");
  }

  private static String[] treeStrs = new String[] {
                  "<TREE>" +
                  "  <VAR NAME='V0'>" +
                  "    <FACTOR VARS='V0 V1'>" +
                  "      <VAR NAME='V1'/>" +
                  "    </FACTOR>" +
                  "    <FACTOR VARS='V0 V2'>" +
                  "      <VAR NAME='V2'/>" +
                  "    </FACTOR>" +
                          "  </VAR>"+
                  "</TREE>",
          "<TREE>" +
          "  <VAR NAME='V1'>" +
          "    <FACTOR VARS='V0 V1'>" +
          "      <VAR NAME='V0'/>" +
          "    </FACTOR>" +
          "    <FACTOR VARS='V1 V2'>" +
          "      <VAR NAME='V2'/>" +
          "    </FACTOR>" +
          "  </VAR>"+
          "</TREE>",
          "<TREE>" +
          "  <VAR NAME='V0'>" +
          "    <FACTOR VARS='V0 V1'>" +
          "      <VAR NAME='V1'>" +
                 "  <FACTOR VARS='V1 V2'>" +
                 "    <VAR NAME='V2'/>" +
                 "  </FACTOR>" +
                 "</VAR>"+
          "    </FACTOR>" +
          "  </VAR>" +
          "</TREE>",
          "<TREE>" +
          "  <VAR NAME='V2'>" +
          "    <FACTOR VARS='V2 V1'>" +
          "      <VAR NAME='V1'/>" +
          "    </FACTOR>" +
          "    <FACTOR VARS='V0 V2'>" +
          "      <VAR NAME='V0'/>" +
          "    </FACTOR>" +
                  "  </VAR>"+
          "</TREE>",
  };

  public void testTrpTreeList ()
  {
    FactorGraph model = createTriangle();
    model.getVariable (0).setLabel ("V0");
    model.getVariable (1).setLabel ("V1");
    model.getVariable (2).setLabel ("V2");

    List readers = new ArrayList ();
    for (int i = 0; i < treeStrs.length; i++) {
      readers.add (new StringReader (treeStrs[i]));
    }

    TRP trp = new TRP().setTerminator (new TRP.DefaultConvergenceTerminator())
                       .setFactory (TRP.TreeListFactory.makeFromReaders (model, readers));
    trp.computeMarginals(model);

    Inferencer jt = new BruteForceInferencer ();
    jt.computeMarginals (model);

    compareMarginals ("", model, trp, jt);
  }

  // Verify that variable indices are consistent in undirectected
  // models.
  public void testUndirectedIndices()
  {
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      FactorGraph mdl = models[mdlIdx];
      for (Iterator it = mdl.variablesIterator (); it.hasNext();) {
        Variable var1 = (Variable) it.next();
        Variable var2 = mdl.get(mdl.getIndex(var1));
        assertTrue("Mismatch in Variable index for " + var1 + " vs "
                   + var2 + " in model " + mdlIdx + "\n" + mdl,
                   var1 == var2);
      }
    }
    logger.info("Test undirectedIndices passed.");
  }


  // Tests that TRP and max-product propagation return the same
  // results when TRP runs for exactly one iteration.
  public void testTrpViterbiEquiv()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      TreeBP maxprod = TreeBP.createForMaxProduct ();
      TRP trp = TRP.createForMaxProduct ()
                              .setTerminator (new TRP.IterationTerminator (1));

      maxprod.computeMarginals (mdl);
      trp.computeMarginals (mdl);

      // TRP should return same results as viterbi
      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor maxPotBp = maxprod.lookupMarginal (var);
        Factor maxPotTrp = trp.lookupMarginal (var);

        maxPotBp.normalize ();
        maxPotTrp.normalize ();
        assertTrue ("TRP 1 iter maxprod propagation not the same as plain maxProd!\n" +
                "Trp " + maxPotTrp.dumpToString () + "\n Plain maxprod " + maxPotBp.dumpToString (),
                    maxPotBp.almostEquals (maxPotTrp));
      }
    }
  }

  public void testTrpOnTrees ()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      Inferencer bp = new TreeBP ();
      Inferencer trp = new TRP ().setTerminator (new TRP.IterationTerminator (1));

      bp.computeMarginals (mdl);
      trp.computeMarginals (mdl);

      int[] outcomes = new int [mdl.numVariables ()];
      Assignment assn = new Assignment (mdl, outcomes);
      assertEquals (bp.lookupLogJoint (assn), trp.lookupLogJoint (assn), 1e-5);

      Arrays.fill (outcomes, 1);
      assn = new Assignment (mdl, outcomes);
      assertEquals (bp.lookupLogJoint (assn), trp.lookupLogJoint (assn), 1e-5);

      // TRP should return same results as viterbi
      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor maxPotBp = bp.lookupMarginal (var);
        Factor maxPotTrp = trp.lookupMarginal (var);

        maxPotBp.normalize ();
        maxPotTrp.normalize ();
        assertTrue ("TRP 1 iter bp propagation not the same as plain maxProd!\n" +
                "Trp " + maxPotTrp.dumpToString () + "\n Plain bp " + maxPotBp.dumpToString (),
                    maxPotBp.almostEquals (maxPotTrp));
      }
    }
  }


  // Tests that TRP and max-product propagation return the same
  // results when TRP is allowed to run to convergence.
  public void testTrpViterbiEquiv2()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      Inferencer maxprod = TreeBP.createForMaxProduct ();
      TRP trp = TRP.createForMaxProduct ();

      maxprod.computeMarginals (mdl);
      trp.computeMarginals (mdl);

      // TRP should return same results as viterbi
      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor maxPotBp = maxprod.lookupMarginal (var);
        Factor maxPotTrp = trp.lookupMarginal (var);
        assertTrue ("TRP maxprod propagation not the same as plain maxProd!\n" +
                "Trp " + maxPotTrp + "\n Plain maxprod " + maxPotBp,
                    maxPotBp.almostEquals (maxPotTrp));
      }
    }
  }


  public void testTreeViterbi()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      BruteForceInferencer brute = new BruteForceInferencer ();
      Inferencer maxprod = TreeBP.createForMaxProduct ();

      Factor joint = brute.joint (mdl);
      maxprod.computeMarginals (mdl);

      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor maxPot = maxprod.lookupMarginal (var);
        Factor trueMaxPot = joint.extractMax (var);
        maxPot.normalize ();
        trueMaxPot.normalize ();
        assertTrue ("Maximization failed! Normalized returns:\n" + maxPot
                + "\nTrue: " + trueMaxPot,
                    maxPot.almostEquals (trueMaxPot));
      }
    }

    logger.info("Test treeViterbi passed: " + trees.length + " models.");
  }


  public void testJtViterbi()
  {
    JunctionTreeInferencer jti = new JunctionTreeInferencer();
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      UndirectedModel mdl = models[mdlIdx];
      BruteForceInferencer brute = new BruteForceInferencer ();

      JunctionTreeInferencer maxprod = JunctionTreeInferencer.createForMaxProduct ();
      JunctionTree jt = maxprod.buildJunctionTree (mdl);
      Factor joint = brute.joint (mdl);
      maxprod.computeMarginals (jt);

      for (Iterator it = mdl.variablesIterator (); it.hasNext ();) {
        Variable var = (Variable) it.next ();
        Factor maxPotRaw = maxprod.lookupMarginal (var);
        Factor trueMaxPotRaw = joint.extractMax (var);
        Factor maxPot = maxPotRaw.duplicate().normalize ();
        Factor trueMaxPot = trueMaxPotRaw.duplicate().normalize ();
        assertTrue ("Maximization failed on model " + mdlIdx
                + " ! Normalized returns:\n" + maxPot.dumpToString ()
                + "\nTrue: " + trueMaxPot.dumpToString (),
                    maxPot.almostEquals (trueMaxPot, 0.01));
      }
    }

    logger.info("Test jtViterbi passed.");
  }

        /*
  public void testMM() throws Exception
  {
      testQuery();
      testTreeViterbi();
      testTrpViterbiEquiv();
      testTrpViterbiEquiv2();
      testMaxMarginals();
  }
    */

  // xxx fails because of TRP termination
  //  i.e., always succeeds if termination is IterationTermination (10)
  //   but usually fails if termination is DefaultConvergenceTerminator (1e-12, 1000)
  //     something about selection of random spanning trees???
  public void testMaxMarginals() throws Exception
  {
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
//    { int mdlIdx = 4;
      FactorGraph mdl = models[mdlIdx];
//      if (mdlIdx != 3) {
//        Visualizer.showModel(mdl);
//          mdl.dump(); System.out.println ("***END MDL "+mdlIdx+"***");
//      }
      BruteForceInferencer brute = new BruteForceInferencer();
      Factor joint = brute.joint(mdl);

//      long foo = System.currentTimeMillis ();
//      System.out.println(foo);
      Inferencer[] algs = constructMaxProductInferencers ();
      for (int infIdx = 0; infIdx < algs.length; infIdx++) {
        Inferencer inf = algs[infIdx];

        if (inf instanceof TRP)
          ((TRP)inf).setRandomSeed(42);
        inf.computeMarginals(mdl);

        for (Iterator it = mdl.variablesIterator (); it.hasNext();) {
          Variable var = (Variable) it.next();
          Factor maxPot = inf.lookupMarginal(var);
          Factor trueMaxPot = joint.extractMax(var);
          if (maxPot.argmax() != trueMaxPot.argmax()) {
            logger.warning("Argmax not equal on model " + mdlIdx + " inferencer "
                           + inf + " !\n  Factors:\nReturned: " + maxPot +
                           "\nTrue: " + trueMaxPot);
            System.err.println("Dump of model " + mdlIdx + " ***");
            mdl.dump ();
            assertTrue (maxPot.argmax() == trueMaxPot.argmax());
          }
        }
      }
    }

    logger.info("Test maxMarginals passed.");
  }


  public void testBeliefPropagation()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      Inferencer prop = new TreeBP ();
//      System.out.println(mdl);

      prop.computeMarginals(mdl);

      for (Iterator it = mdl.variablesIterator (); it.hasNext();) {
        Variable var = (Variable) it.next();
        Factor marg1 = treeMargs[mdlIdx][mdl.getIndex(var)];
        Factor marg2 = prop.lookupMarginal(var);
        try {
          assertTrue("Test failed on graph " + mdlIdx + " vertex " + var + "\n" +
                     "Model: " + mdl + "\nExpected: " + marg1.dumpToString () + "\nActual: " + marg2.dumpToString (),
                     marg1.almostEquals(marg2, 0.011));
        } catch (AssertionFailedError e) {
          System.out.println (e.getMessage ());
          System.out.println("*******************************************\nMODEL:\n");
          mdl.dump ();
          System.out.println("*******************************************\nMESSAGES:\n");
          ((AbstractBeliefPropagation)prop).dump();
          throw e;
        }
      }
    }

    logger.info("Test beliefPropagation passed.");
  }

  public void testBpJoint ()
  {
    for (int mdlIdx = 0; mdlIdx < trees.length; mdlIdx++) {
      FactorGraph mdl = trees[mdlIdx];
      Inferencer bp = new TreeBP ();
      BruteForceInferencer brute = new BruteForceInferencer ();

      brute.computeMarginals (mdl);
      bp.computeMarginals (mdl);

      for (AssignmentIterator it = mdl.assignmentIterator (); it.hasNext();) {
        Assignment assn = (Assignment) it.next ();
        assertEquals (brute.lookupJoint (assn), bp.lookupJoint (assn), 1e-15);
      }
    }
  }


  // Eventially this should be folded into testMarginals, testJoint, etc.
  public void testDirectedJt ()
  {
    DirectedModel bn = createDirectedModel ();
    BruteForceInferencer brute = new BruteForceInferencer ();
    brute.computeMarginals (bn);

    JunctionTreeInferencer jt = new JunctionTreeInferencer ();
    jt.computeMarginals (bn);

    compareMarginals ("Error comparing junction tree to brute on directed model!",
                      bn, brute, jt);
  }

  private DirectedModel createDirectedModel ()
  {
    int NUM_OUTCOMES = 2;
    cc.mallet.util.Randoms random = new cc.mallet.util.Randoms (13413);

    Dirichlet dirichlet = new Dirichlet (NUM_OUTCOMES, 1.0);
    double[] pA = dirichlet.randomVector (random);
    double[] pB = dirichlet.randomVector (random);

    TDoubleArrayList pC = new TDoubleArrayList (NUM_OUTCOMES * NUM_OUTCOMES * NUM_OUTCOMES);
    for (int i = 0; i < (NUM_OUTCOMES * NUM_OUTCOMES); i++) {
      pC.add (dirichlet.randomVector (random));
    }

    Variable[] vars = new Variable[] { new Variable (NUM_OUTCOMES), new Variable (NUM_OUTCOMES),
            new Variable (NUM_OUTCOMES) };
    DirectedModel mdl = new DirectedModel ();
    mdl.addFactor (new CPT (new TableFactor (vars[0], pA), vars[0]));
    mdl.addFactor (new CPT (new TableFactor (vars[1], pB), vars[1]));
    mdl.addFactor (new CPT (new TableFactor (vars, pC.toArray ()), vars[2]));

    return mdl;
  }

  private void compareMarginals (String msg, FactorGraph fg,  Inferencer inf1, Inferencer inf2)
  {
    for (int i = 0; i < fg.numVariables (); i++) {
      Variable var = fg.get (i);
      Factor ptl1 = inf1.lookupMarginal (var);
      Factor ptl2 = inf2.lookupMarginal (var);
      assertTrue (msg + "\n" + ptl1.dumpToString () + "\n" + ptl2.dumpToString (),
                  ptl1.almostEquals (ptl2, 1e-5));
    }
  }


  protected void setUp()
  {
    modelsList = createTestModels();
    createTestTrees();
    models = (UndirectedModel[]) modelsList.toArray
            (new UndirectedModel[]{});
    computeTestTreeMargs();
  }


  public void testMultiply()
  {
    TableFactor p1 = new TableFactor (new Variable[]{});
    System.out.println(p1);

    Variable[] vars = new Variable[]{
      new Variable(2),
      new Variable(2),
    };
    double[] probs = new double[]{1, 3, 5, 6};
    TableFactor p2 = new TableFactor
            (vars, probs);

    Factor p3 = p1.multiply(p2);
    assertTrue("Should be equal: " + p2 + "\n" + p3,
               p2.almostEquals(p3));
  }

  /* TODO: Not sure how to test this anymore.
	// Test multiplication of potentials where variables are in
	//  a different order
	public void testMultiplication2 ()
	{
		Variable[] vars = new Variable[] {
			new Variable (2),
			new Variable (2),
		};
		double[] probs1 = new double[] { 2, 4, 1, 6 };
		double[] probs2a = new double[] { 3, 7, 6, 5 };
		double[] probs2b = new double[] { 3, 6, 7, 5 };

		MultinomialPotential ptl1a = new MultinomialPotential (vars, probs1);
		MultinomialPotential ptl1b = new MultinomialPotential (vars, probs1);
		MultinomialPotential ptl2a = new MultinomialPotential (vars, probs2a);

		Variable[] vars2 = new Variable[] { vars[1], vars[0], };
		MultinomialPotential ptl2b = new MultinomialPotential (vars2, probs2b);

		ptl1a.multiplyBy (ptl2a);
		ptl1b.multiplyBy (ptl2b);
		
		assertTrue (ptl1a.almostEquals (ptl1b));
	}
  */

  public void testLogMarginalize ()
  {
    FactorGraph mdl = models [0];
    Iterator it = mdl.variablesIterator ();
    Variable v1 = (Variable) it.next();
    Variable v2 = (Variable) it.next();
    Random rand = new Random (3214123);

    for (int i = 0; i < 10; i++) {
      Factor ptl = randomEdgePotential (rand, v1, v2);

      Factor logmarg1 = new LogTableFactor ((AbstractTableFactor) ptl).marginalize(v1);
      Factor marglog1 = new LogTableFactor((AbstractTableFactor) ptl.marginalize(v1));
      assertTrue ("LogMarg failed! Correct: "+marglog1+" Log-marg: "+logmarg1,
                  logmarg1.almostEquals (marglog1));

      Factor logmarg2 = new LogTableFactor ((AbstractTableFactor) ptl).marginalize(v2);
      Factor marglog2 = new LogTableFactor((AbstractTableFactor) ptl.marginalize(v2));
      assertTrue (logmarg2.almostEquals (marglog2));
    }
  }

  public void testLogNormalize ()
  {
    FactorGraph mdl = models [0];
    Iterator it = mdl.variablesIterator ();
    Variable v1 = (Variable) it.next();
    Variable v2 = (Variable) it.next();
    Random rand = new Random (3214123);

    for (int i = 0; i < 10; i++) {
      Factor ptl = randomEdgePotential (rand, v1, v2);
      Factor norm1 = new LogTableFactor((AbstractTableFactor) ptl);
      Factor norm2 = ptl.duplicate();
      norm1.normalize();
      norm2.normalize();
      assertTrue ("LogNormalize failed! Correct: "+norm2+" Log-normed: "+norm1,
                  norm1.almostEquals (norm2));
    }
  }

  public void testSumLogProb ()
  {
    java.util.Random rand = new java.util.Random (3214123);

    for (int i = 0; i < 10; i++) {
      double v1 = rand.nextDouble();
      double v2 = rand.nextDouble();

      double sum1 = Math.log (v1 + v2);
      double sum2 = Maths.sumLogProb (Math.log(v1), Math.log (v2));
//			System.out.println("Summing "+v1+" + "+v2);
      assertEquals (sum1, sum2, 0.00001);
    }

  }

  public void testInfiniteCost()
  {
    Variable[] vars = new Variable[3];
    for (int i = 0; i < vars.length; i++) {
      vars[i] = new Variable (2);
    }

    FactorGraph mdl = new FactorGraph (vars);
    mdl.addFactor (vars[0], vars[1], new double[] { 2, 6, 4, 8 });
    mdl.addFactor (vars[1], vars[2], new double[] { 1, 0, 0, 1 });
    mdl.dump ();

    Inferencer bp = new TreeBP ();
    bp.computeMarginals (mdl);
    //below should be true, except potentials have different ranges.
    //assertTrue (bp.lookupMarginal(vars[1]).almostEquals (bp.lookupMarginal(vars[2])));
  }

  public void testJtCaching()
  {
    // clear all caches
    for (int i = 0; i < models.length; i++) {
      FactorGraph model = models[i];
      model.setInferenceCache (JunctionTreeInferencer.class, null);
    }

    Factor[][] margs = new Factor[models.length][];
    long stime1 = new Date().getTime();
    for (int i = 0; i < models.length; i++) {
      FactorGraph model = models[i];
      JunctionTreeInferencer inf = new JunctionTreeInferencer();
      inf.computeMarginals(model);

      margs[i] = new Factor[model.numVariables ()];
      Iterator it = model.variablesIterator ();
      int j = -1;
      while (it.hasNext()) {
        Variable var = (Variable) it.next();
        j++;
        margs[i][j] = inf.lookupMarginal(var);
      }
    }
    long etime1 = new Date().getTime();
    long diff1 = etime1 - stime1;
    logger.info ("Pre-cache took "+diff1+" ms.");

    long stime2 = new Date().getTime();
    for (int i = 0; i < models.length; i++) {
      FactorGraph model = models[i];
      JunctionTreeInferencer inf = new JunctionTreeInferencer();
      inf.computeMarginals(model);

      Iterator it = model.variablesIterator ();
      int j = -1;
      while (it.hasNext()) {
        Variable var = (Variable) it.next();
        j++;
        assertTrue (margs[i][j].almostEquals (inf.lookupMarginal (var)));
      }

    }
    long etime2 = new Date().getTime();
    long diff2 = etime2 - stime2;
    logger.info ("Post-cache took "+diff2+" ms.");

//    assertTrue (diff2 < diff1);
  }

  public void testFindVariable ()
  {
    FactorGraph mdl = models [0];
    Variable[] vars = new Variable [mdl.numVariables ()];
    Iterator it = mdl.variablesIterator ();
    while (it.hasNext()) {
      Variable var = (Variable) it.next();
      String name = new String (var.getLabel());
      assertTrue (var == mdl.findVariable (name));
    }
    assertTrue (mdl.findVariable ("xsdfasdf") == null);
  }

  public void testDefaultLookupMarginal ()
  {
    Inferencer inf = new TreeBP ();
    FactorGraph mdl = trees[JT_CHAIN_TEST_TREE];
    Variable var = mdl.get (0);

    inf.computeMarginals (mdl);
    // Previously: UnsupportedOperationException
    //  Exptected: default to lookupMarginal (Variable) for clique of size 1
    VarSet varSet = new HashVarSet (new Variable[] { var });
    Factor ptl1 = inf.lookupMarginal (varSet);
    Factor ptl2 = inf.lookupMarginal (var);
    assertTrue (ptl1.almostEquals (ptl2));

    Variable var2 = mdl.get (1);
    Variable var3 = mdl.get (2);
    VarSet c2 = new HashVarSet (new Variable[] { var, var2, var3 });
    try {
      inf.lookupMarginal (c2);
      fail ("Expected an UnsupportedOperationException with clique "+c2);
    } catch (UnsupportedOperationException e) {}
  }

  // Eventually this should be moved to models[], but TRP currently chokes on disconnected
  //  model
  public void testDisconnectedModel ()
  {
    Variable[] vars = new Variable [4];
    for (int i = 0; i < vars.length; i++) {
      vars [i] = new Variable (2);
    }
    FactorGraph mdl = new UndirectedModel (vars);

    Random r = new Random (67);
    Factor[] ptls = new Factor [4];
    Factor[] normed = new Factor [4];
    for (int i = 0; i < vars.length; i++) {
      ptls[i] = randomNodePotential (r, vars[i]);
      normed[i] = ptls[i].duplicate();
      normed[i].normalize();
      mdl.addFactor (ptls[i]);
    }
    mdl.dump ();

    Inferencer inf = new LoopyBP ();
    inf.computeMarginals (mdl);

    for (int i = 0; i < vars.length; i++) {
      Factor marg = inf.lookupMarginal (vars[i]);
      assertTrue ("Marginals not equal!\n   True: "+normed[i]+"\n   Returned "+marg,
                  marg.almostEquals (normed[i]));
    }

    for (AssignmentIterator it = mdl.assignmentIterator (); it.hasNext();) {
      Assignment assn = (Assignment) it.next ();
      double trueProb = 1.0;
      for (int i = 0; i < vars.length; i++) trueProb *= normed[i].value (assn);
      assertEquals (trueProb, inf.lookupJoint (assn), 1e-5);
    }
  }

  public void timeMarginalization ()
  {
    java.util.Random r = new java.util.Random (7732847);
    Variable[] vars = new Variable[] { new Variable (2),
                                       new Variable (2),
    };
    TableFactor ptl = randomEdgePotential (r, vars[0], vars[1]);

    long stime = System.currentTimeMillis ();
    for (int i = 0; i < 1000; i++) {
      Factor marg = ptl.marginalize (vars[0]);
      Factor marg2 = ptl.marginalize (vars[1]);
    }
    long etime = System.currentTimeMillis ();
    logger.info ("Marginalization (2-outcome) took "+(etime-stime)+" ms.");

    Variable[] vars45 = new Variable[] { new Variable (45),
                                       new Variable (45),
    };
    TableFactor ptl45 = randomEdgePotential (r, vars45[0], vars45[1]);
    stime = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      Factor marg = ptl45.marginalize (vars45[0]);
      Factor marg2 = ptl45.marginalize (vars45[1]);
    }
    etime = System.currentTimeMillis();
    logger.info ("Marginalization (45-outcome) took "+(etime-stime)+" ms.");
  }

  // using this for profiling
  public void runJunctionTree ()
  {
    for (int mdlIdx = 0; mdlIdx < models.length; mdlIdx++) {
      FactorGraph model = models[mdlIdx];
      JunctionTreeInferencer inf = new JunctionTreeInferencer();
      inf.computeMarginals(model);

      Iterator it = model.variablesIterator ();
      while (it.hasNext()) {
        Variable var = (Variable) it.next();
        inf.lookupMarginal (var);
      }
    }
  }

  public void testDestructiveAssignment ()
  {
    Variable vars[] = { new Variable(2), new Variable (2), };
    Assignment assn = new Assignment (vars, new int[] { 0, 1 });
    assertEquals (0, assn.get (vars[0]));
    assertEquals (1, assn.get (vars[1]));

    assn.setValue (vars[0], 1);
    assertEquals (1, assn.get (vars[0]));
    assertEquals (1, assn.get (vars[1]));
  }

  public void testLoopyConvergence ()
  {
    Random r = new Random (67);
    FactorGraph mdl = createRandomGrid (5, 5, 2, r);
    LoopyBP loopy = new LoopyBP ();
    loopy.computeMarginals (mdl);
    assertTrue (loopy.iterationsUsed() > 8);
  }

  public void testSingletonGraph ()
  {
    Variable v = new Variable (2);
    FactorGraph mdl = new FactorGraph (new Variable[] { v });
    mdl.addFactor (new TableFactor (v, new double[] { 1, 2 }));

    TRP trp = new TRP ();
    trp.computeMarginals (mdl);

    Factor ptl = trp.lookupMarginal (v);

    double[] dbl = ((AbstractTableFactor) ptl).toValueArray ();
    assertEquals (2, dbl.length);
    assertEquals (0.33333, dbl[0], 1e-4);
    assertEquals (0.66666, dbl[1], 1e-4);
  }

  public void testLoopyCaching ()
  {
    FactorGraph mdl1 = models[4];
    FactorGraph mdl2 = models[5];
    Variable var = mdl1.get (0);

    LoopyBP inferencer = new LoopyBP ();
    inferencer.setUseCaching (true);
    inferencer.computeMarginals (mdl1);
    Factor origPtl = inferencer.lookupMarginal (var);
    assertTrue (2 < inferencer.iterationsUsed ());

    // confuse the inferencer
    inferencer.computeMarginals (mdl2);

    // make sure we have cached, correct results
    inferencer.computeMarginals (mdl1);
    Factor sndPtl = inferencer.lookupMarginal (var);
    // note that we can't use an epsilon here, that's less than our convergence criteria.
    assertTrue ("Huh? Original potential:"+origPtl+"After: "+sndPtl,
                origPtl.almostEquals (sndPtl, 1e-4));
    assertEquals (1, inferencer.iterationsUsed ());

  }

  public void testJunctionTreeConnectedFromRoot ()
  {
    JunctionTreeInferencer jti = new JunctionTreeInferencer ();
    jti.computeMarginals (models[0]);
    jti.computeMarginals (models[1]);
    JunctionTree jt = jti.lookupJunctionTree ();

    List reached = new ArrayList ();
    LinkedList queue = new LinkedList ();
    queue.add (jt.getRoot ());
    while (!queue.isEmpty ()) {
      VarSet current = (VarSet) queue.removeFirst ();
      queue.addAll (jt.getChildren (current));
      reached.add (current);
    }

    assertEquals (jt.clusterPotentials ().size (), reached.size());
  }

  public void testBpLargeModels ()
  {
    Timing timing = new Timing ();
//    UndirectedModel mdl = RandomGraphs.createUniformChain (800);
    FactorGraph mdl = RandomGraphs.createUniformChain (8196);
    timing.tick ("Model creation");
    AbstractBeliefPropagation inf = new LoopyBP ();

    try {
      inf.computeMarginals (mdl);
    } catch (OutOfMemoryError e) {
      System.out.println ("OUT OF MEMORY: Messages sent "+inf.getTotalMessagesSent ());
      throw e;
    }
    
    timing.tick ("Inference time (Random sched BP)");
  }

  public void testTrpLargeModels ()
  {
    Timing timing = new Timing ();
//    UndirectedModel mdl = RandomGraphs.createUniformChain (800);
    FactorGraph mdl = RandomGraphs.createUniformChain (8192);
    timing.tick ("Model creation");
    Inferencer inf = new TRP ();
    inf.computeMarginals (mdl);
    timing.tick ("Inference time (TRP)");
  }

/*
  public void testBpDualEdgeFactor ()
  {
    Variable[] vars = new Variable[] {
            new Variable (2),
            new Variable (2),
            new Variable (2),
            new Variable (2),
    };

    Random r = new Random ();
    Factor tbl1 = createEdgePtl (vars[0], vars[1], r);
    Factor tbl2a = createEdgePtl (vars[1], vars[2], r);
    Factor tbl2b = createEdgePtl (vars[1], vars[2], r);
    Factor tbl3 = createEdgePtl (vars[2], vars[3], r);

    FactorGraph fg = new FactorGraph (vars);
    fg.addFactor (tbl1);
    fg.addFactor (tbl2a);
    fg.addFactor (tbl2b);
    fg.addFactor (tbl3);

    Inferencer inf = new TRP ();
    inf.computeMarginals (fg);

    VarSet vs = tbl2a.varSet ();
    Factor marg1 = inf.lookupMarginal (vs);

    Factor prod = TableFactor.multiplyAll (fg.factors ());
    Factor marg2 = prod.marginalize (vs);
    marg2.normalize ();

    assertTrue ("Factors not equal!  BP: "+marg1.dumpToString ()+"\n EXACT: "+marg2.dumpToString (), marg1.almostEquals (marg2));
  }
  */

  private Factor createEdgePtl (Variable var1, Variable var2, Random r)
  {
    double[] dbls = new double [4];
    for (int i = 0; i < dbls.length; i++) {
      dbls[i] = r.nextDouble ();
    }
    return new TableFactor (new Variable[] { var1, var2 }, dbls);
  }

  private String gridStr =
          "VAR alpha u : CONTINUOUS\n" +
          "alpha ~ Uniform -1.0 1.0\n" +
          "u ~ Uniform -2.0 2.0\n" +
          "x00 ~ Unary u\n" +
          "x10 ~ Unary u\n" +
          "x01 ~ Unary u\n" +
          "x11 ~ Unary u\n" +
          "x00 x01 ~ Potts alpha\n" +
          "x00 x10 ~ Potts alpha\n" +
          "x01 x11 ~ Potts alpha\n" +
          "x10 x11 ~ Potts alpha\n";

  public void testJtConstant () throws IOException
  {
    FactorGraph masterFg = new ModelReader ().readModel (new BufferedReader (new StringReader (gridStr)));
    JunctionTreeInferencer jt = new JunctionTreeInferencer ();
    Assignment assn = masterFg.sampleContinuousVars (new cc.mallet.util.Randoms (3214));
    FactorGraph fg = (FactorGraph) masterFg.slice (assn);
    jt.computeMarginals (fg);
  }

  public static Test suite()
  {
    return new TestSuite(TestInference.class);
  }


  public static void main(String[] args) throws Exception
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest(new TestInference(args[i]));
      }
    } else {
      theSuite = (TestSuite) suite();
    }

    junit.textui.TestRunner.run(theSuite);
  }

}
