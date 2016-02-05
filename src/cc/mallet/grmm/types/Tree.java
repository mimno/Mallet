/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.grmm.types;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import cc.mallet.types.Alphabet;

import gnu.trove.TObjectIntHashMap;


/**
 *  Class for arbitrary trees, based on implementation in OpenJGraph.
 *  The OpenJGraph tree implementation is a bit minimal wrt
 *   convenience functions, so we add a few here.
 *
 * Created: Wed Oct  1 14:51:47 2003
 *
 * @author <a href="mailto:casutton@cs.umass.edu">Charles Sutton</a>
 * @version $Id: Tree.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
public class Tree {

  private TObjectIntHashMap vertex2int = new TObjectIntHashMap ();
  private ArrayList int2vertex = new ArrayList ();
  private ArrayList parents = new ArrayList ();
  private ArrayList children = new ArrayList ();

  private Object root = null;

  public Tree() {} // Tree constructor

  // Efficient indexing of parents, children

  public static Tree makeFromSubtree (Object parent, List subtrees)
  {
    Tree tree = new Tree();
    tree.add (parent);
    for (Iterator it = subtrees.iterator (); it.hasNext ();) {
      Tree subtree = (Tree) it.next ();
      tree.addSubtree (parent, subtree, subtree.getRoot ());
    }
    return tree;
  }

  private void addSubtree (Object parent, Tree subtree, Object child)
  {
    addNode (parent, child);
    for (Iterator it = subtree.getChildren (child).iterator (); it.hasNext ();) {
      Object gchild = it.next ();
      addSubtree (child, subtree, gchild);
    }
  }                                       

  protected int lookupIndex (Object v)
  {
    return vertex2int.get (v);
  }

  protected Object lookupVertex (int idx)
  {
    return int2vertex.get (idx);
  }

  int maybeAddVertex (Object v)
  {
    if (!vertex2int.containsKey (v)) {
      int foo = int2vertex.size ();
      int2vertex.add (v);
      vertex2int.put (v, foo);
      parents.add (null);
      children.add (new ArrayList ());
      return foo;
    } else {
	return vertex2int.get (v);
    }
  }

  public void add (Object rt)
  {
    if (root == null) {
      maybeAddVertex (rt);
      root = rt;
    } else {
      throw new UnsupportedOperationException
        ("This tree already has a root.");
    }
  }


  public void addNode (Object parent, Object child)
  {
    int id1;
    if (root == null) {
      root = parent;
      id1 = maybeAddVertex (parent);
    } else if ((id1 = lookupIndex (parent)) == -1)
      throw new UnsupportedOperationException
        ("This tree already has a root.");

    int id2 = maybeAddVertex (child);

    Object oldParent = parents.get (id2);
    if ((oldParent != null) && (oldParent != parent))
      throw new UnsupportedOperationException
              ("Trying to change parent of Object "+child+" from "
               +oldParent+" to "+parent);

    parents.set (id2, parent);
    ArrayList childList = (ArrayList) children.get (id1);
    childList.add (child);
  }


  public Object getParent (Object child)
  {
      if (vertex2int.containsKey (child)) {
	  return parents.get (vertex2int.get (child));
      } else {
	  return null;
      }
  }

  public List getChildren (Object parent)
  {
    int id = vertex2int.get (parent);
    return Collections.unmodifiableList ((List) children.get (id));
  }

  // Convenience functions

  public boolean isRoot (Object var)
  {
    int idx = lookupIndex (var);
    return (parents.get(idx) == null);
  }


  public boolean containsObject (Object v)
  {
    return (vertex2int.get (v) >= 0);
  }

  public boolean isLeaf (Object v)
  {
    int idx = lookupIndex (v);
    return ((List)children.get(idx)).size() == 0;
  }

  public Iterator getVerticesIterator ()
  {
    return int2vertex.iterator();
  }

  public Object getRoot () { return root; }

  public String dumpToString ()
  {
    StringBuffer buf = new StringBuffer ();
    dumpRec (root, 0, buf);
    return buf.toString ();
  }

  private void dumpRec (Object node, int lvl, StringBuffer buf)
  {
    for (int i = 0; i < 3 * lvl; i++) {
      buf.append ("-");
    }
    buf.append ("  ").append (node).append ("\n");
    for (Iterator it = getChildren (node).iterator (); it.hasNext();) {
      Object child = it.next ();
      dumpRec (child, lvl+1, buf);
    }
  }

} // Tree
