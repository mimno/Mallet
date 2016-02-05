package cc.mallet.grmm.types;

import java.util.Set;


/**
 * Interface for classes that maintain a set of variables in a specified order.
 *
 * @author Charles Sutton
 * @version $Id: VarSet.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $ 
 */
public interface VarSet extends Set, Cloneable {

  /**
   * Returns the variable in this clique at index idx.
   * @param idx
   * @return the variable
   */
  Variable get (int idx);

  /**
   * Returns the variables in this clique as an array, that should
   *  not be modified.
   * @return An array of Variables.
   */
  Variable[] toVariableArray ();


  /**
   *  Returns the number of assignments of this clique.
   */
  int weight ();


  /**
   * Returns an iterator over the assignments to this clique.
   *  Each element in the Iterator is an {@link cc.mallet.grmm.types.Assignment} object.
   * @return An iterator over assignments
   */
  AssignmentIterator assignmentIterator ();


  /**
	 * Returns the intersection of two cliques.
	 */
  VarSet intersection (VarSet c);

}
