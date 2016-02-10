/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.grmm.types;

/**
 * A grid-shaped undirected graphical model.  All this adds to the
 *  base UndirectedModel class is the ability to retrieve variables
 *  by their (x,y) position.
 *
 * Created: Mar 28, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: UndirectedGrid.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class UndirectedGrid extends UndirectedModel {

  private Variable[][] vars;
  private int width;
  private int height;

  /**
   * Creates an undirected grid and its associated Variable objects.
   * @param width The max x coordinate of the grid.
   * @param height The max y coordinate of thee grid.
   * @param numOutcomes The number of outcomes of each created variable.
   */
  public UndirectedGrid (int width, int height, int numOutcomes)
  {
    super (width * height);
    this.width = width;
    this.height = height;
    addVariables (numOutcomes);
//    addEdges ();
  }

  public int getWidth ()
  {
    return width;
  }

  public int getHeight ()
  {
    return height;
  }

  /* xxx Is this necessary any more?
  private void addEdges ()
  {
    // add up-down edges
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height - 1; y++) {
        Variable v1 = vars[x][y];
        Variable v2 = vars[x][y+1];
        addEdge (v1, v2);
      }
    }
    // add left-right edges
    for (int x = 0; x < width - 1; x++) {
      for (int y = 0; y < height; y++) {
        Variable v1 = vars[x][y];
        Variable v2 = vars[x+1][y];
        addEdge (v1, v2);
      }
    }
  }
  */

  private void addVariables (int numOutcomes)
  {
    vars = new Variable [width][height];
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        vars[x][y] = new Variable (numOutcomes);
      }
    }
  }

  public Variable get (int x, int y)
  {
    return vars[x][y];
  }

}
