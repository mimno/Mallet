/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.rmi.dgc.VMID;
import java.util.logging.*;
import java.io.*;
import java.lang.Runtime;

import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.RandomTokenSequenceIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.Labeling;
import cc.mallet.util.DoubleList;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.PropertyList;
import cc.mallet.util.Randoms;

/**
	 TODO .split() methods still unreliable

	 An InstanceList which avoids OutOfMemoryErrors by saving Instances
	 to disk when there is not enough memory to create a new
	 Instance. It implements a fixed-size paging scheme, where each page
	 on disk stores <code>instancesPerPage</code> Instances. So, while
	 the number of Instances per pages is constant, the size in bytes of
	 each page may vary. Using this class instead of InstanceList means
	 the number of Instances you can store is essentially limited only
	 by disk size (and patience).

	 The paging scheme is optimized for the most frequent case of
	 looping through the InstanceList from index 0 to n. If there are n
	 instances, then instances 0->(n/size()) are stored together on page
	 1, instances (n/size)+1 -> 2*(n/size) are on page 2, ... etc. This
	 way, pages adjacent in the <code>instances</code> list will usually
	 be in the same page.

	 The paging scheme also tries to only keep one page in memory at a
	 time. The justification for this is that the page size is near the
	 limit of the maximum number of instances that can be kept in
	 memory. Since we assume the frequent case is looping from instance
	 0 to n, keeping other Instances in memory will be a waste of
	 resources.
	 
	 About <code>instancesPerPage</code> -- If
	 <code>instancesPerPage</code> = -1, then its value will be set
	 automatically by the following: When the first OutOfMemoryError is
	 thrown, count how many instances are currently in memory, then
	 divide by two. This is a conservative estimate of how many Instance
	 objects can fit in memory simultaneously. If you know this value
	 beforehand, simply pass it to the constructor.

	 NOTE: The event which causes an OutOfMemoryError is the
	 instantiation of a new Instance, _not_ the addition of this
	 Instance to an InstanceList. Therefore, if you want to avoid
	 OutOfMemoryErrors, let PagedInstanceList instantiate the new
	 Instance for you. IOW, do this:

	 Pipe p = ...;
	 PagedInstanceList ilist = new PagedInstanceList (p);
	 ilist.add (data, target, name, source);

	 Or This

	 Instance.Iterator iter = ...;
	 Pipe p = ...;
	 PagedInstanceList ilist = new PagedInstanceList (p);
	 ilist.add (iter);
	 
	 But Not This:

	 Pipe p = ...;
	 PagedInstanceList ilist = new PagedInstanceList (p);
	 ilist.add (new Instance (data, target, name, source));

	 If memory is low, the last example will throw an OutOfMemoryError
	 before control has been passed to PagedInstanceList to catch the
	 error.

	 NOTE ALSO: To save write time, we do not write the same Instance to
	 disk more than once, i.e., there are no dirty bits or
	 write-throughs. Thus, this assumes that after an Instance has been
	 passed through its Pipe, it is no longer modified. One way around
	 this is to call PagedInstanceList.setInstance (Instance inst),
	 which _will_ overwrite an Instance that has been paged to disk.
	 
	 @see InstanceList

   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

public class PagedInstanceList extends InstanceList
{
	private static Logger logger = MalletLogger.getLogger(PagedInstanceList.class.getName());

	/** number of instances to put in one page. if -1, determine at
	 * first call to <code>swapOutExcept</code> */
	int instancesPerPage;

	/** directory to store swap files */
	File swapDir;
	
	/** inMemory.get(i) == true iff instances.get(i) is in memory, else 0 */
	BitSet inMemory;

	/**  pageNotInMemory.get(i) == true iff page i is not in memory,
	 *  else 0 */
	BitSet pageNotInMemory;

	/** recommend garbage collection after every swap out? */
	boolean collectGarbage = true;
	
	/** uniquely identifies this InstanceList. Used in creating
	 * serialized page name for swap files. */
	VMID id = new VMID();


	// CONSTRUCTORS

	
	/** Creates a PagedInstanceList where "instancesPerPage" instances
	 * are swapped to disk in directory "swapDir" if the amount of free
	 * system memory drops below "minFreeMemory" bytes
	 * @param pipe instance pipe
	 * @param instancesPerPage number of Instances to store in each
	 * page. If -1, determine at first call to
	 * <code>swapOutExcept</code>
	 * @param swapDir where the pages on disk live.
	 */
	public PagedInstanceList (Pipe pipe, int size, int instancesPerPage, File swapDir)
	{
		super (pipe, size);
		inMemory = new BitSet();
		pageNotInMemory = new BitSet();
		this.instancesPerPage = instancesPerPage;
		this.swapDir = swapDir;
		try {
			if (!swapDir.exists()) {
				swapDir.mkdir();
			}
		} catch (SecurityException e) {
			System.err.println ("No permission to make directory " + swapDir);
			System.exit(-1);
		}
	}

	public PagedInstanceList (Pipe pipe, int size) {
		this (pipe, size, -1, new File ("."));
	}

	public PagedInstanceList (Pipe pipe) {		
 		this (pipe, 10);
	}

	public PagedInstanceList () {		
		this (notYetSetPipe);
	}


	// SPLITTING AND SAMPLING METHODS

	
  /**
   * Shuffles the elements of this list among several smaller
   * lists. Overrides InstanceList.split to add instances in original
   * order, to prevent thrashing.
   * @param proportions A list of numbers (not necessarily summing to 1) which,
   * when normalized, correspond to the proportion of elements in each returned
   * sublist.
   * @param r The source of randomness to use in shuffling.
   * @return one <code>InstanceList</code> for each element of <code>proportions</code>
   */
	public InstanceList[] split (java.util.Random r, double[] proportions)
	{
    ArrayList<Integer> shuffled = new ArrayList<Integer> (size());
		for (int i=0; i < size(); i++)
			shuffled.add (new Integer (i));
		Collections.shuffle (shuffled, r);
    return splitInOrder(shuffled, proportions, this);
	}

	public InstanceList[] split (double[] proportions)
	{
		return split (new java.util.Random(System.currentTimeMillis()), proportions);
	}

  private static InstanceList[] splitInOrder (List<Integer> instanceIndices, double[] proportions,
                                              PagedInstanceList cloneMe) {
    double[] maxind = new double[proportions.length];
 		System.arraycopy (proportions, 0, maxind, 0, proportions.length);
		PagedInstanceList[] ret = new PagedInstanceList[proportions.length];
		ArrayList<Integer>[] splitIndices = new ArrayList[proportions.length];
		DenseVector.normalize(maxind);
		// Fill maxind[] with the highest instance index that should go in
		// each corresponding returned InstanceList.
		for (int i = 0; i < maxind.length; i++) {
			// xxx Is it dangerous to share the featureSelection that comes with cloning?
			ret[i] = (PagedInstanceList)cloneMe.cloneEmpty();
			splitIndices[i] = new ArrayList<Integer>();
			if (i > 0)
				maxind[i] += maxind[i-1];
		}
		for (int i = 0; i < maxind.length; i++)
			maxind[i] = Math.rint (maxind[i] * instanceIndices.size());
		int j = 0;
		// This gives a slight bias toward putting an extra instance in the last InstanceList.
		for (int i = 0; i < instanceIndices.size(); i++) {
			while (i >= maxind[j])
				j++;
			splitIndices[j].add(new Integer(i));
		}

		// now sort each splitIndices so paging is reduced
		for (int i=0; i < splitIndices.length; i++) {
			Collections.sort(splitIndices[i]);
		}
		for (int i=0; i < cloneMe.size(); i++) {
			Instance tmpi = null;
			try { // try to read in instance i
				tmpi = cloneMe.get(i);
			}
			catch (OutOfMemoryError e) {
				tmpi = null;
				System.gc();
				logger.warning ("Caught " + e + " while splitting InstanceList. Paging out instances in all lists and retrying...");
				cloneMe.swapOutAll();
				for (int x=0; x < ret.length; x++) {
					if (ret[x].size() > 0) {
						System.out.println ("Swapping out ilist " + x);
						ret[x].swapOutAll();
					}
				}
				System.gc();
				try {
					tmpi = cloneMe.get(i);
				}
				catch (OutOfMemoryError ee) {
					logger.warning ("Still can't free enough memory to read in instance " +
													i + " while splitting. Try using smaller value for \"instancesPerPage\".");
					System.exit(-1);
				}				
			}
			tmpi = tmpi.shallowCopy();
			boolean found = false;
			for (int ii=0; ii < splitIndices.length; ii++) {
				if (splitIndices[ii].size()==0)
					continue;
				int index = ((Integer)splitIndices[ii].get(0)).intValue();
				if (index==i) {
					logger.info ("adding instance " + i + " to split ilist " + ii);
					found = true;
 					ret[ii].add (tmpi);
				 	splitIndices[ii].remove(0);
				}
				if (!found)
					throw new IllegalStateException ("Error splitting instances.");
			}
 		}		
	 	return ret;
	}
	
	  /** Returns a pair of new lists such that the first list in the
   * pair contains every <code>m</code>th element of this list,
   * starting with the first.  The second list contains all remaining
   * elements. Overrides InstanceList.splitByModulo to use
   * PagedInstanceLists.
   */
	public InstanceList[] splitByModulo (int m)
	{
		PagedInstanceList[] ret = new PagedInstanceList[2];
		ret[0] = (PagedInstanceList)this.cloneEmpty();
		ret[1] = (PagedInstanceList)this.cloneEmpty();
		for (int i = 0; i < this.size(); i++) {
			if (i % m == 0)
				ret[0].add (this.get(i));
			else
				ret[1].add (this.get(i));
		}
		return ret;
	}

	/** Overridden to add samples in original order to reduce
	 * thrashing. */
	public InstanceList sampleWithReplacement (java.util.Random r, int numSamples)
	{
		PagedInstanceList ret = (PagedInstanceList)this.cloneEmpty();
		ArrayList indices = new ArrayList (numSamples);
		for (int i=0; i < numSamples; i++)
			indices.add (new Integer (r.nextInt(size())));
		Collections.sort (indices);
		for (int i = 0; i < indices.size(); i++)
			ret.add (this.get(((Integer)indices.get(i)).intValue()));
		return ret;
	}

  /**
   * Returns an <code>InstanceList</code> of the same size, where the instances come from the
   * random sampling (with replacement) of this list using the given weights.
   * The length of the weight array must be the same as the length of this list
   * The new instances all have their weights set to one.
   */
  // added by Gary - ghuang@cs.umass.edu
  public InstanceList sampleWithWeights(java.util.Random r, double[] weights) 
  {
    if (weights.length != size())
		  throw new IllegalArgumentException("length of weight vector must equal number of instances");
		if (size() == 0)
		  return cloneEmpty();
		
		double sumOfWeights = 0;
		for (int i = 0; i < size(); i++) {
		  if (weights[i] < 0)
				throw new IllegalArgumentException("weight vector must be non-negative");
		  sumOfWeights += weights[i];
		}
		if (sumOfWeights <= 0)
		  throw new IllegalArgumentException("weights must sum to positive value");
		
		PagedInstanceList newList = new PagedInstanceList();
		double[] probabilities = new double[size()];
		double sumProbs = 0;
		for (int i = 0; i < size(); i++) {
		  sumProbs += r.nextDouble();
		  probabilities[i] = sumProbs;
		}
		MatrixOps.timesEquals(probabilities, sumOfWeights / sumProbs);
		
		// make sure rounding didn't mess things up
		probabilities[size() - 1] = sumOfWeights;
		// do sampling
		int a = 0; int b = 0; sumProbs = 0;
		while (a < size() && b < size()) {
		  sumProbs += weights[b];		  
		  while (a < size() && probabilities[a] <= sumProbs) {
				newList.add(get(b));
				newList.setInstanceWeight(a, 1);
				a++;
		  }
		  b++;
		}
		
		return newList;
	}
	

	// PAGING METHODS

	
	/** Swap in the page for Instance at <code>index</code>. Swap out
	 * all other pages.
	 * @param index index in the <code>instances</code> list of the
	 * Instance we want. */
	private void swapIn (int index) {
    long start = System.currentTimeMillis ();
		if (instancesPerPage == -1) {
			throw new IllegalStateException ("instancesPerPage not set => swapOut not yet called => swapIn cannot be called yet.");
		}
		int bin = index / instancesPerPage;
		if (pageNotInMemory.get(bin)) {
			logger.info ("Swapping in instance " + index + " from page " + bin);
			swapOutExcept (index);
			try {
				ObjectInputStream in = new ObjectInputStream
															 (new FileInputStream (new File (swapDir, id + "." + String.valueOf(bin))));
				for (int ii=0; ii < instancesPerPage; ii++) {
					// xxx What if now we don't have enough memory to swap in
					// entire page?!?!
					Instance inst = (Instance) in.readObject();
					int newIndex = (instancesPerPage*bin) + ii;
					inst.unLock();
					//inst.setPipe (pipe);
					inst.lock();
					if (inMemory.get(newIndex))
						throw new IllegalStateException (newIndex + " already in memory! ");
					this.set (newIndex, inst);
					inMemory.set (newIndex);
					if (newIndex == size()-1) // for last bin
						break;
				}
				pageNotInMemory.set (bin, false);
			}
			catch (Exception e) {
				System.err.println (e);
				System.exit(-1);
			}
		}

    long end = System.currentTimeMillis ();
    logger.info ("PagedInstaceList swap-in time (ms) = "+(end-start));
	}

	/** Save all instances to disk and set to null to free memory.*/
	public void swapOutAll () {
		swapOutExcept (size());
	}
	
	/** Swap out all pages except the page for index. 
 	 * @param index index in the <code>instances</code> list of the
 	 * Instance we want. */
	private void swapOutExcept (int index) {
    long start = System.currentTimeMillis ();
		if (index < 0 || inMemory.cardinality() < 1) {
			logger.warning ("nothing to swap out to read instance " + index);
			return;
		}
		if (instancesPerPage == -1) { // set to half the # of instances we can store in mem
			instancesPerPage = Math.max(size()/2,1);
		}		
		int binToKeep = index / instancesPerPage;
		int maxBin =  (size()-1) / instancesPerPage;
		for (int i=0; i <= maxBin; i++) {
			if (i==binToKeep || pageNotInMemory.get(i))
				continue;
			logger.info ("\tSwapping out page " + i);
			try {
				int beginIndex = i*instancesPerPage;
				int endIndex = Math.min((i+1)*(instancesPerPage)-1, size()-1);
				File f = new File (swapDir, id + "." + String.valueOf(i));
				if (!f.exists()) { // save time by not re-writing files.
					try {
						ObjectOutputStream out = new ObjectOutputStream (new FileOutputStream (f));
						for (int bi=beginIndex; bi <= endIndex; bi++) {
							Instance inst = this.get(bi);
							
							if (inst.getDataAlphabet() != null) 
								inst.getDataAlphabet().setInstanceId (new VMID());
							if (inst.getTargetAlphabet() != null) 
								inst.getTargetAlphabet().setInstanceId (new VMID());
							
							assert (inst != null) : "null instance while swapping out page from bin " + i;
							inst.unLock();
							//inst.setPipe (null);
							inst.lock();
							out.writeObject (inst);
						}
						out.close();
					}
					catch (Exception e) {
						System.err.println (e);
						System.exit(-1);
					}
				}
			
			for (int bi=beginIndex; bi <= endIndex; bi++) {
				this.set(bi, null);
				inMemory.set (bi, false);
			}
			logger.fine ("Swapping out page " + i);
			pageNotInMemory.set(i, true);
			}
			catch (OutOfMemoryError ee) { // xxx FIX THIS SOMEHOW!
				System.out.println ("Ran out of memory while swapping out.");
				System.exit(-1);				
			}
		}
		
		if (collectGarbage)
			System.gc();

    long end = System.currentTimeMillis ();
    logger.info ("PagedInstanceList swapout time (ms) = "+(end - start));
	}
		

	// ACCESSORS

	
	/** Returns the <code>Instance</code> at the specified index. If
	 * this Instance is not in memory, swap a block of instances back
	 * into memory. */
	public Instance get (int index)
	{
		if (!inMemory.get(index))
			swapIn (index);
		return (Instance) this.get (index);
	}
    
  /** Replaces the <code>Instance</code> at position
   * <code>index</code> with a new one. Note that this is the only
   * sanctioned way of changing an Instance. */
  public Instance set (int index, Instance instance)
  {
		if (!inMemory.get(index))
			swapIn (index);
    return this.set(index, instance);
  }

  /** Appends the instance to this list. Note that since memory for
   * the Instance has already been allocated, no check is made to
   * catch OutOfMemoryError.
   * @return <code>true</code> if successful
   */
	public boolean add (Instance instance)
	{
		boolean ret = super.add (instance);
		inMemory.set(size()-1);
		logger.finer ("Added instance " + (size()-1) + ". Free memory remaining (bytes): " +
								 Runtime.getRuntime().freeMemory());
 		return ret;
	}

	/** Constructs and appends an instance to this list, passing it through this
   * list's pipe and assigning it the specified weight. Checks are made to
   * ensure an OutOfMemoryError is not thrown when instantiating a new
   * Instance.
   * @return <code>true</code>
   */
	/* This no longer works because this method is deprecated.  
	 * We could try to re-implement this behavior in add(Iterator<Instance>), but I'm not
	 * sure how re-entrant all those nested pipes would be after throwing an exception!?
	 *  
	public boolean add (Object data, Object target, Object name, Object source, double instanceWeight)fff
	{
		Instance inst = null;
		logger.fine ("Trying to add instance...");
		try {
			inst = pipe.instanceFrom(new Instance (data, target, name, source));
		}
		catch (OutOfMemoryError e) {
			logger.info ("Caught " + e + "\n Instances in memory: " + inMemory.cardinality()
									 + ". Swapping out to free memory.");
 			inst = null;
			if (collectGarbage) System.gc();
			swapOutExcept (size());
			logger.info ("After paging, InstanceList.size:" + size() + " Instances in memory: " +
									 inMemory.cardinality() + " Free Memory (bytes): " + Runtime.getRuntime().freeMemory());
			try {
 				inst = pipe.instanceFrom(new Instance (data, target, name, source));
			}
			catch (OutOfMemoryError ee) {
				inst = null;											
				logger.warning ("Still insufficient memory after swapping to disk. Instance too large to fit in memory?");
				System.exit(-1);
			}
		}

		 boolean retVal = add (inst, instanceWeight);

    if ((instancesPerPage > 0) && (inMemory.cardinality () > instancesPerPage)) {
      logger.info ("Page size "+instancesPerPage+" exceeded.  Forcing swap.  Instances in memory: " +
									 inMemory.cardinality() + " Free Memory (bytes): " + Runtime.getRuntime().freeMemory());
      if (collectGarbage) System.gc ();
      swapOutExcept (size());
      logger.info ("After paging, InstanceList.size:" + size() + " Instances in memory: " +
                   inMemory.cardinality() + " Free Memory (bytes): " + Runtime.getRuntime().freeMemory());
    }

    return retVal;
	}
	*/

	public void setCollectGarbage (boolean b) { this.collectGarbage = b; }
	public boolean collectGarbage () { return this.collectGarbage; }

	public InstanceList shallowClone ()
	{
		PagedInstanceList ret = (PagedInstanceList) this.cloneEmpty();
		for (int i = 0; i < this.size(); i++)
			ret.add (get(i));
		return ret;
	}

	public InstanceList cloneEmpty ()
	{
		PagedInstanceList ret = (PagedInstanceList) super.cloneEmptyInto(new PagedInstanceList (pipe, size(), instancesPerPage, swapDir)); 
		ret.collectGarbage = this.collectGarbage;
		return ret;
	}

	/** Constructs a new <code>InstanceList</code>, deserialized from
			<code>file</code>.  If the string value of <code>file</code> is
			"-", then deserialize from {@link System.in}. */
	public static InstanceList load (File file)
	{
		try {
			ObjectInputStream ois;
			if (file.toString().equals("-"))
				ois = new ObjectInputStream (System.in);
			else
				ois = new ObjectInputStream (new FileInputStream (file));
			PagedInstanceList ilist = (PagedInstanceList) ois.readObject();
			ois.close();
			return ilist;
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException ("Couldn't read PagedInstanceList from file "+file);
		}
	}

	// Serialization of PagedInstanceList

	private static final long serialVersionUID = 1;
	private static final int CURRENT_SERIAL_VERSION = 1;
		
	private void writeObject (ObjectOutputStream out) throws IOException {
		int i, size;
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeObject (id);
		out.writeObject(pipe);
		// memory attributes
		out.writeInt (instancesPerPage);
		out.writeObject (swapDir);
		out.writeObject (inMemory);
		out.writeObject (pageNotInMemory);
		out.writeBoolean (collectGarbage);
	}
		
	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int i, size;
		int version = in.readInt ();
		id = (VMID) in.readObject ();
		pipe = (Pipe) in.readObject();
		// memory attributes
		instancesPerPage = in.readInt ();
		swapDir = (File) in.readObject ();
		inMemory = (BitSet) in.readObject ();
		pageNotInMemory = (BitSet) in.readObject ();
		collectGarbage = in.readBoolean ();
	}
}
