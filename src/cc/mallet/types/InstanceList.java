/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


package cc.mallet.types;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.google.errorprone.annotations.Var;

import cc.mallet.pipe.FeatureSequence2FeatureVector;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.RandomTokenSequenceIterator;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.Randoms;

/**
     A list of machine learning instances, typically used for training
     or testing of a machine learning algorithm.
   <p>
     All of the instances in the list will have been passed through the
     same {@link cc.mallet.pipe.Pipe}, and thus must also share the same data and target Alphabets.
   InstanceList keeps a reference to the pipe and the two alphabets.
   <p>
   The most common way of adding instances to an InstanceList is through
   the <code>add(PipeInputIterator)</code> method. PipeInputIterators are a way of mapping general
   data sources into instances suitable for processing through a pipe.
     As each {@link cc.mallet.types.Instance} is pulled from the PipeInputIterator, the InstanceList
     copies the instance and runs the copy through its pipe (with resultant
     destructive modifications) before saving the modified instance on its list.
     This is the  usual way in which instances are transformed by pipes.
     <p>
     InstanceList also contains methods for randomly generating lists of
     feature vectors; splitting lists into non-overlapping subsets (useful
     for test/train splits), and iterators for cross validation.

   @see Instance
   @see Pipe

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class InstanceList extends ArrayList<Instance> implements Serializable, Iterable<Instance>, AlphabetCarrying
{
    private static Logger logger = MalletLogger.getLogger(InstanceList.class.getName());

    HashMap<Instance, Double> instWeights = null;
  // This should never be set by a ClassifierTrainer, it should be used in conjunction with a Classifier's FeatureSelection
    // Or perhaps it should be removed from here, and there should be a ClassifierTrainer.train(InstanceList, FeatureSelection) method.
    FeatureSelection featureSelection = null;  
    FeatureSelection[] perLabelFeatureSelection = null;
    Pipe pipe;
    Alphabet dataAlphabet, targetAlphabet;
    Class dataClass = null;
    Class targetClass = null;

    /**
     * Construct an InstanceList having given capacity, with given default pipe.
     * Typically Instances added to this InstanceList will have gone through the 
     * pipe (for example using instanceList.addThruPipe); but this is not required.
     * This InstanaceList will obtain its dataAlphabet and targetAlphabet from the pipe.
     * It is required that all Instances in this InstanceList share these Alphabets. 
     * @param pipe The default pipe used to process instances added via the addThruPipe methods.
     * @param capacity The initial capacity of the list; will grow further as necessary.
     */
    // XXX not very useful, should perhaps be removed
    public InstanceList (Pipe pipe, int capacity) {
        super(capacity);
        this.pipe = pipe;
    }

    /**
     * Construct an InstanceList with initial capacity of 10, with given default pipe.
     * Typically Instances added to this InstanceList will have gone through the 
     * pipe (for example using instanceList.addThruPipe); but this is not required.
     * This InstanaceList will obtain its dataAlphabet and targetAlphabet from the pipe.
     * It is required that all Instances in this InstanceList share these Alphabets. 
     * @param pipe The default pipe used to process instances added via the addThruPipe methods.
     */
    public InstanceList (Pipe pipe) {
        this (pipe, 10);
    }

    /** 
     * Construct an InstanceList with initial capacity of 10, with a Noop default pipe.
     * Used in those infrequent circumstances when Instances typically would not have further
     * processing,  and objects containing vocabularies are entered
     * directly into the <code>InstanceList</code>; for example, the creation of a
     * random <code>InstanceList</code> using <code>Dirichlet</code>s and
     * <code>Multinomial</code>s.</p>
     *
     * @param dataAlphabet The vocabulary for added instances' data fields
     * @param targetAlphabet The vocabulary for added instances' targets
     */
    public InstanceList (Alphabet dataAlphabet, Alphabet targetAlphabet) {
        this (new Noop(dataAlphabet, targetAlphabet), 10);
        this.dataAlphabet = dataAlphabet;
        this.targetAlphabet = targetAlphabet;
    }

    private static class NotYetSetPipe extends Pipe    {
        @Override public Instance pipe (Instance carrier)    {
            throw new UnsupportedOperationException (
                    "The InstanceList has yet to have its pipe set; "+
            "this could happen by calling InstanceList.add(InstanceList)");
        }
        @Override public Object readResolve () throws ObjectStreamException    {
            return notYetSetPipe;
        }
        private static final long serialVersionUID = 1;
    }
    static final Pipe notYetSetPipe = new NotYetSetPipe();

    /** Creates a list that will have its pipe set later when its first Instance is added. */
    @Deprecated // Pipe is never set if you use this constructor 
    public InstanceList () {
        this (notYetSetPipe);
    }

    /**
     * Creates a list consisting of randomly-generated
     * <code>FeatureVector</code>s.
     */
    // xxx Perhaps split these out into a utility class
    public InstanceList (Randoms r,
                         // the generator of all random-ness used here
                         Dirichlet classCentroidDistribution,
                         // includes a Alphabet
                         double classCentroidAverageAlphaMean,
                         // Gaussian mean on the sum of alphas
                         double classCentroidAverageAlphaVariance,
                         // Gaussian variance on the sum of alphas
                         double featureVectorSizePoissonLambda,
                         double classInstanceCountPoissonLambda,
                         String[] classNames) {
        this (new SerialPipes (new Pipe[]    {
                new TokenSequence2FeatureSequence (),
                new FeatureSequence2FeatureVector (),
                new Target2Label()}));
        //classCentroidDistribution.print();
        Iterator<Instance> iter = new RandomTokenSequenceIterator (
                r, classCentroidDistribution,
                classCentroidAverageAlphaMean, classCentroidAverageAlphaVariance,
                featureVectorSizePoissonLambda, classInstanceCountPoissonLambda,
                classNames);
        this.addThruPipe (iter);
    }

    private static Alphabet dictOfSize (int size) {
        Alphabet ret = new Alphabet ();
        for (int i = 0; i < size; i++) {
            ret.lookupIndex ("feature"+i);
        }
        return ret;
    }

    private static String[] classNamesOfSize (int size) {
        String[] ret = new String[size];
        for (int i = 0; i < size; i++)
            ret[i] = "class"+i;
        return ret;
    }

    public InstanceList (Randoms r, Alphabet vocab, String[] classNames, int meanInstancesPerLabel) {
        this (r, new Dirichlet(vocab, 2.0),
                30, 0,
                10, meanInstancesPerLabel, classNames);
    }
    

    public InstanceList (Randoms r, int vocabSize, int numClasses) {
        this (r, new Dirichlet(dictOfSize(vocabSize), 2.0),
                30, 0,
                10, 20, classNamesOfSize(numClasses));
    }

    public InstanceList shallowClone () {
        InstanceList ret = new InstanceList (pipe, this.size());
        for (int i = 0; i < this.size(); i++)
            ret.add (get(i));
        if (instWeights == null)
            ret.instWeights = null;
        else
            ret.instWeights = (HashMap<Instance,Double>) instWeights.clone();
        // Should we really be so shallow as to not make new copies of these following instance variables? -akm 1/2008
        ret.featureSelection = featureSelection;
        ret.perLabelFeatureSelection = perLabelFeatureSelection;
        ret.pipe = pipe;;
        ret.dataAlphabet = dataAlphabet;
        ret.targetAlphabet = targetAlphabet;
        ret.dataClass = dataClass;
        ret.targetClass = targetClass;
        return ret;
    }
    
    @Override public Object clone () {
        return shallowClone();
    }
    
    
    @Override public InstanceList subList (int start, int end) {
        InstanceList other = this.cloneEmpty();
        for (int i = start; i < end; i++) {
            other.add (get (i));
        }
        return other;
    }

    public InstanceList subList (@Var double proportion) {
        if (proportion > 1.0)
            throw new IllegalArgumentException ("proportion must by <= 1.0");
        InstanceList other = (InstanceList) clone();
        other.shuffle(new java.util.Random());
        proportion *= other.size();
        for (int i = 0; i < proportion; i++)
            other.add (get(i));
        return other;
    }



    /** Adds to this list every instance generated by the iterator,
     * passing each one through this InstanceList's pipe. */
    // TODO This method should be renamed addPiped(Iterator<Instance> ii)
    public void addThruPipe (Iterator<Instance> ii) {
        //for debug
        Iterator<Instance> pipedInstanceIterator = pipe.newIteratorFrom(ii);
        while (pipedInstanceIterator.hasNext()) {    
            add (pipedInstanceIterator.next());
            //System.out.println("Add instance " + pipedInstanceIterator.next().getName());
        }
    }
    
    // gsc: method to add one instance at a time 
    /** Adds the input instance to this list, after passing it through the
     * InstanceList's pipe.
     * <p>
     * If several instances are to be added then accumulate them in a List\<Instance\>
     * and use <tt>addThruPipe(Iterator<Instance>)</tt> instead.
     */
    public void addThruPipe(Instance inst) {
      addThruPipe(new SingleInstanceIterator(inst));
    }

    /** Constructs and appends an instance to this list, passing it through this
     * list's pipe and assigning it the specified weight.
     * @return <code>true</code>
     * @deprecated Use trainingset.addThruPipe (new Instance(data,target,name,source)) instead.
     */
    @Deprecated 
    public boolean add (Object data, Object target, Object name, Object source, double instanceWeight) {
        Instance inst = new Instance (data, target, name, source);
        Iterator<Instance> ii = pipe.newIteratorFrom(new SingleInstanceIterator(inst));
        if (ii.hasNext()) {
            add (ii.next(), instanceWeight);
            return true;
        }
    return false;
    }

    /** Constructs and appends an instance to this list, passing it through this
     * list's pipe.  Default weight is 1.0.
     * @return <code>true</code>
     * @deprecated Use trainingset.add (new Instance(data,target,name,source)) instead.
     */
    @Deprecated
    public boolean add (Object data, Object target, Object name, Object source) {
        return add (data, target, name, source, 1.0);
    }

    /** Appends the instance to this list without passing the instance through
     * the InstanceList's pipe.  
     * The alphabets of this Instance must match the alphabets of this InstanceList.
     * @return <code>true</code>
     */
    @Override public boolean add (Instance instance) {
        if (dataAlphabet == null)
            dataAlphabet = instance.getDataAlphabet();
        
        if (targetAlphabet == null)
            targetAlphabet = instance.getTargetAlphabet();
        
        if (!Alphabet.alphabetsMatch(this, instance)) {
            // gsc
            @Var
            Alphabet data_alphabet = instance.getDataAlphabet();
            @Var
            Alphabet target_alphabet = instance.getTargetAlphabet();
            StringBuilder sb = new StringBuilder();
            sb.append("Alphabets don't match: ");
            sb.append("Instance: [" + (data_alphabet == null ? null : data_alphabet.size()) + ", " +
              (target_alphabet == null ? null : target_alphabet.size()) + "], ");
            data_alphabet = this.getDataAlphabet();
            target_alphabet = this.getTargetAlphabet();
            sb.append("InstanceList: [" + (data_alphabet == null ? null : data_alphabet.size()) + ", " +
              (target_alphabet == null ? null : target_alphabet.size()) + "]\n");
            throw new IllegalArgumentException(sb.toString());
            //            throw new IllegalArgumentException ("Alphabets don't match: Instance: "+
            //                    instance.getAlphabets()+" InstanceList: "+this.getAlphabets());
        }
        if (dataClass == null) {
            dataClass = instance.data.getClass();
            if (pipe != null && pipe.isTargetProcessing())
                if (instance.target != null)
                    targetClass = instance.target.getClass();
        }
        // Once it is added to an InstanceList, generally-speaking, the Instance shouldn't change.
        // There are exceptions, and for these you can instance.unlock(), then instance.lock() again.
        instance.lock(); 
        return super.add (instance);
    }

    /** Appends the instance to this list without passing it through this
     * InstanceList's pipe, assigning it the specified weight.
     * @return <code>true</code>
     */
    public boolean add (Instance instance, double instanceWeight) {
        // Call the add method above and make sure we
        // correctly handle adding the first instance to this list
        boolean ret = this.add(instance);
        if (!ret)
            // If for some reason a subclass of InstanceList refuses to add this Instance, be sure not to do the rest. 
            return ret; 
        if (instanceWeight != 1.0) { // Default weight is 1.0 for everything not in the HashMap.
            if (instWeights == null) 
                instWeights = new HashMap<Instance,Double>();
            else if (instWeights.get(instance) != null)
                throw new IllegalArgumentException ("You cannot add the same instance twice to an InstanceList when it has non-1.0 weight.  "+
                        "Trying adding instance.shallowCopy() instead.");
            instWeights.put(instance, instanceWeight);
        }
        return ret;
    }
    
    private void prepareToRemove (Instance instance) {
        if (instWeights != null)
            instWeights.remove(instance);
    }
  
    @Override public Instance set (int index, Instance instance) {
        prepareToRemove(get(index));
        return super.set (index, instance);
    }

    @Override public void add (int index, Instance element) {
        throw new IllegalStateException ("Not yet implemented.");
    }

    @Override public Instance remove (int index) {
        prepareToRemove (get(index));
        return super.remove(index);
    }

    public boolean remove (Instance instance) {
        prepareToRemove (instance);
        return super.remove(instance);
    }

    @Override public boolean addAll (Collection<? extends Instance> instances) {
        for (Instance instance : instances) {
            this.add (instance);
        }
        return true;
    }

    @Override public boolean addAll(int index, Collection <? extends Instance> c) {
        throw new IllegalStateException ("addAll(int,Collection) not supported by InstanceList.n");
    }

    @Override public void clear() {
        super.clear();
        instWeights.clear();
        // But retain all other instance variables.
    }
    
    public InstanceList cloneEmpty () {
        return cloneEmptyInto (new InstanceList (pipe));
    }

    // A precursor to cloning subclasses of InstanceList 
    protected InstanceList cloneEmptyInto (InstanceList ret) {
        ret.instWeights = null; // Don't copy these, because its empty! instWeights == null ? null : (HashMap<Instance,Double>) instWeights.clone();
        // xxx Should the featureSelection and perLabel... be cloned?
        // Note that RoostingTrainer currently depends on not cloning its splitting.
        ret.featureSelection = this.featureSelection;
        ret.perLabelFeatureSelection = this.perLabelFeatureSelection;
        ret.dataClass = this.dataClass;
        ret.targetClass = this.targetClass;
        ret.dataAlphabet = this.dataAlphabet;
        ret.targetAlphabet = this.targetAlphabet;
        return ret;
    }

    public void shuffle (java.util.Random r) {
        Collections.shuffle (this, r);
    }

    /**
     * Shuffles the elements of this list among several smaller lists.
     * @param proportions A list of numbers (not necessarily summing to 1) which,
     * when normalized, correspond to the proportion of elements in each returned
     * sublist.  This method (and all the split methods) do not transfer the Instance
     * weights to the resulting InstanceLists.
     * @param r The source of randomness to use in shuffling.
     * @return one <code>InstanceList</code> for each element of <code>proportions</code>
     */
    public InstanceList[] split (java.util.Random r, double[] proportions) {
        InstanceList shuffled = this.shallowClone();
        shuffled.shuffle (r);
        return shuffled.splitInOrder(proportions);
    }
    
    public InstanceList[] split (double[] proportions) {
        return split (new java.util.Random(System.currentTimeMillis()), proportions);
    }

    /** Chops this list into several sequential sublists.
     * @param proportions A list of numbers corresponding to the proportion of
     * elements in each returned sublist.  If not already normalized to sum to 1.0, it will be normalized here.
     * @return one <code>InstanceList</code> for each element of <code>proportions</code>
     */
    public InstanceList[] splitInOrder (double[] proportions) {
        InstanceList[] ret = new InstanceList[proportions.length];
        double maxind[] = proportions.clone();
        MatrixOps.normalize(maxind);
        for (int i = 0; i < maxind.length; i++) {
            ret[i] = this.cloneEmpty();  // Note that we are passing on featureSelection here.
            if (i > 0) 
                maxind[i] += maxind[i-1];
        }
        for (int i = 0; i < maxind.length; i++) { 
            // Fill maxind[] with the highest instance index to go in each corresponding returned InstanceList
            maxind[i] = Math.rint (maxind[i] * this.size());
        }
        for (int i = 0, j = 0; i < size(); i++) {
            // This gives a slight bias toward putting an extra instance in the last InstanceList.
            while (i >= maxind[j] && j < ret.length) 
                j++;
            ret[j].add(this.get(i));
        }
        return ret;
    }


    /**
     * Shuffles the elements of this list among several smaller lists, each sublist
     * having a number of elements proportional to the amount given in the array.  
     * If the target alphabet of this list is a {@link LabelAlphabet}, then each 
     * sublist has (approximately and to the extent possible) the same distribution
     * of the target classes as the original list.
     * Otherwise, the sublists are randomly generated without committing to the
     * underlying distribution.
     * <p/>
     * TODO Sublists must conform tothe underlying distribution, even when the target
     * alphabet is <b>not</b> of LabelAlplhabet type.
     * @param proportions A list of numbers (not necessarily summing to 1) which,
     *     when normalized, correspond to the proportion of elements in each returned
     *     sublist. This method (and all the split methods) do not transfer the Instance
     *     weights to the resulting InstanceLists.
     * @param r The source of randomness to use in shuffling.
     * @return one <code>InstanceList</code> for each element of <code>proportions</code>
     */
    public InstanceList[] stratifiedSplit(java.util.Random r, double[] proportions) {
        InstanceList shuffled = this.shallowClone();
        shuffled.shuffle(r);

        /* If the instance list does not have a target */
        if (this.targetAlphabet == null){
          return shuffled.splitInOrder(proportions);
        }else{
          return shuffled.stratifiedSplitInOrder(proportions);
        }
    }

    /** 
     * Chops this list into several sequential sublists, where each sublist
     * contains an (approximately) equal proportion of each target label.
     * @param proportions A list of numbers corresponding to the proportion of
     *     elements in each returned sublist.
     *     If not already normalized to sum to 1.0, it will be normalized here.
     * @return one <code>InstanceList</code> for each element of <code>proportions</code>
     */
    public InstanceList[] stratifiedSplitInOrder(double[] proportions) {

      InstanceList[] ret = new InstanceList[proportions.length];

      /* Create a normalized version of the given proportions */
      double normMaxInd[] = proportions.clone();
      MatrixOps.normalize(normMaxInd);
      for (int i = 0; i < normMaxInd.length; i++) {
        ret[i] = this.cloneEmpty();  // Note that we are passing on featureSelection here.
        if (i > 0) {
          normMaxInd[i] += normMaxInd[i-1];
        }
      }

      /* Keeps track of the fold that each stratum (target class) is currently at */
      int[] stratCurrentFold = new int[this.targetAlphabet.size()];

      /* Stores for each stratum the indexes of the original list that belong to it */
      List<Integer>[] stratIndexes = new ArrayList[this.targetAlphabet.size()];
      for (int i = 0; i < this.getTargetAlphabet().size(); i++) {
        stratIndexes[i] = new ArrayList<Integer>();
      }

      /* Do a first pass on this instance list and assign each original
       * instance position to the respective stratum */
      for ( int i = 0; i < this.size(); i++ ){
        Instance inst = this.get(i);
        int targetIndex = ((Label)inst.getTarget()).getIndex();
        assert (targetIndex >= 0);
        stratIndexes[targetIndex].add(i);
      }

      /* Whether the user has been warned about breaking the distribution */
      @Var
      boolean isUserWarned = false;

      /* Do a second pass on this instance list */
      for ( int i = 0; i < this.size(); i++ ){
        Instance inst = this.get(i);
        int targetIndex = ((Label)inst.getTarget()).getIndex();

        /* Get the current fold that the stratum is at,
         * and from that also get the expected data proportion */
        int stratumFold = stratCurrentFold[targetIndex];
        double stratumFoldRatio = normMaxInd[stratumFold];

        /* Check if a stratum has fewer instances than the number of folds.
         * In that case, the distribution is destined to fail
         * XXX Generalize the check for when the distribution will fail */
        if (stratIndexes[targetIndex].size() < proportions.length && !isUserWarned) {
          logger.warning("Target stratum has " + stratIndexes[targetIndex].size() + 
              " instances, less than the requested " + proportions.length + " folds. "
              + "The folds distribution will not match the original." );
          isUserWarned = true;
        }

        /* Find the position in the stratum array corresponding to the percentage.
         * If the position is out-of-bounds for the array, just use the original size,
         * to allow for the item to be added to the final fold. Otherwise,
         * get the original instance index for that position. */
        int stratumPos = (int)Math.rint (stratumFoldRatio * stratIndexes[targetIndex].size());
        @Var
        int stratumMaxInd = this.size();
        if (stratumPos < stratIndexes[targetIndex].size()){
          stratumMaxInd = stratIndexes[targetIndex].get(stratumPos);
        }

        /* If the current instance position exceeds the fold's minimum
         * proceed to the next fold */
        if (i >= stratumMaxInd && stratCurrentFold[targetIndex] < ret.length)
          stratCurrentFold[targetIndex]++;

        /* Add the instance to the fold */
        ret[stratCurrentFold[targetIndex]].add(this.get(i));
      }

      return ret;
  }
    

    public InstanceList[] splitInOrder (int[] counts) {
        InstanceList[] ret = new InstanceList[counts.length];
        // Will leave ununsed instances if sum of counts[] != this.size()!
        @Var
        int idx = 0;
        for (int num = 0; num < counts.length; num++){
            ret[num] = cloneEmpty();
            for (int i = 0; i < counts[num]; i++){
                ret[num].add (get(idx));  // Transfer weights?
                idx++;
            }
        }

        return ret;
    }


    /** Returns a pair of new lists such that the first list in the pair contains
     * every <code>m</code>th element of this list, starting with the first.
     * The second list contains all remaining elements.
     */
    public InstanceList[] splitInTwoByModulo (int m) {
        InstanceList[] ret = new InstanceList[2];
        ret[0] = this.cloneEmpty();
        ret[1] = this.cloneEmpty();
        for (int i = 0; i < this.size(); i++) {
            if (i % m == 0)
                ret[0].add (this.get(i));
            else
                ret[1].add (this.get(i));
        }
        return ret;
    }

    public InstanceList sampleWithReplacement (java.util.Random r, int numSamples) {
        InstanceList ret = this.cloneEmpty();
        for (int i = 0; i < numSamples; i++)
            ret.add (this.get(r.nextInt(this.size())));
        return ret;
    }

    /**
     * Returns an <code>InstanceList</code> of the same size, where the instances come from the
     * random sampling (with replacement) of this list using the instance weights.
     * The new instances all have their weights set to one.
     */
    // added by Gary - ghuang@cs.umass.edu
    @Deprecated
    // Move to InstanceListUtils
    public InstanceList sampleWithInstanceWeights(java.util.Random r) 
    {
        double[] weights = new double[size()];
        for (int i = 0; i < weights.length; i++)
            weights[i] = getInstanceWeight(i);

        return sampleWithWeights(r, weights);
    }

    /**
     * Returns an <code>InstanceList</code> of the same size, where the instances come from the
     * random sampling (with replacement) of this list using the given weights.
     * The length of the weight array must be the same as the length of this list
     * The new instances all have their weights set to one.
     */
    // added by Gary - ghuang@cs.umass.edu
    public InstanceList sampleWithWeights (java.util.Random r, double[] weights) 
    {
        if (weights.length != size())
            throw new IllegalArgumentException("length of weight vector must equal number of instances");
        if (size() == 0)
            return cloneEmpty();

        @Var
        double sumOfWeights = 0;
        for (int i = 0; i < size(); i++) {
            if (weights[i] < 0)
                throw new IllegalArgumentException("weight vector must be non-negative");
            sumOfWeights += weights[i];
        }
        if (sumOfWeights <= 0)
            throw new IllegalArgumentException("weights must sum to positive value");

        InstanceList newList = new InstanceList(getPipe(), size());
        double[] probabilities = new double[size()];
        @Var
        double sumProbs = 0;
        for (int i = 0; i < size(); i++) {
            sumProbs += r.nextDouble();
            probabilities[i] = sumProbs;
        }
        MatrixOps.timesEquals(probabilities, sumOfWeights / sumProbs);

        // make sure rounding didn't mess things up
        probabilities[size() - 1] = sumOfWeights;
        // do sampling
        @Var
        int a = 0;
        @Var
        int b = 0;
        sumProbs = 0;
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

    /** Returns the Java Class 'data' field of Instances in this list. */
    public Class getDataClass () {
        return dataClass;
    }

    /** Returns the Java Class 'target' field of Instances in this list. */
    public Class getTargetClass () {
        return targetClass;
    }

    //added by Fuchun
    /** Replaces the <code>Instance</code> at position <code>index</code>
     * with a new one. */
    public void setInstance (int index, Instance instance) {
        assert (this.getDataAlphabet().equals(instance.getDataAlphabet()));
        assert (this.getTargetAlphabet().equals(instance.getTargetAlphabet()));
        this.set(index, instance);
    }

    public double getInstanceWeight (Instance instance) {
        if (instWeights != null) {
            Double value = instWeights.get(instance);
            if (value != null) {
                return value;
            }
        }
        return 1.0;
    }

    public double getInstanceWeight (int index) {
        if (index > this.size()) {
            throw new IllegalArgumentException("Index out of bounds: index="+index+" size="+this.size());
        }

        if (instWeights != null) {
            Double value = instWeights.get(get(index));
            if (value != null) {
                return value;
            }
        }

        return 1.0;
    }

    public void setInstanceWeight (int index, double weight) {
        setInstanceWeight(get(index), weight);
    }

    public void setInstanceWeight (Instance instance, double weight) {

        // Weights of 1.0 are not explicitly stored in the hash.
        if (weight == 1.0) {
            // If the weights hash does not exist, we are done.
            if (instWeights == null) { return; }

            // Otherwise, see if there is a weight currently set.
            Double value = instWeights.get(instance);

            // If there is no value set or the value is 1.0, we're done.
            if (value == null || value.doubleValue() == weight) { return; }

            // Otherwise remove the value
            instWeights.remove(instance);
        }
        else {
            // Initialize the weights hash if it does not exist
            if (instWeights == null) {
                instWeights = new HashMap<Instance,Double> ();
            }
            // Add the new value, overriding any previous value
            instWeights.put(instance, weight);
        }
    }

    public void setFeatureSelection (FeatureSelection selectedFeatures) {
        if (selectedFeatures != null
                && selectedFeatures.getAlphabet() != null  // xxx We allow a null vocabulary here?  See CRF3.java
                && selectedFeatures.getAlphabet() != getDataAlphabet())
            throw new IllegalArgumentException ("Vocabularies do not match");
        featureSelection = selectedFeatures;
    }

    public FeatureSelection getFeatureSelection () {
        return featureSelection;
    }

    public void setPerLabelFeatureSelection (FeatureSelection[] selectedFeatures) {
        if (selectedFeatures != null) {
            for (int i = 0; i < selectedFeatures.length; i++)
                if (selectedFeatures[i].getAlphabet() != getDataAlphabet())
                    throw new IllegalArgumentException ("Vocabularies do not match");
        }
        perLabelFeatureSelection = selectedFeatures;
    }

    public FeatureSelection[] getPerLabelFeatureSelection () {
        return perLabelFeatureSelection;
    }

    /** Sets the "target" field to <code>null</code> in all instances.  This makes unlabeled data. */
    public void removeTargets() {
        for (Instance instance : this)
            instance.setTarget (null);
    }

    /** Sets the "source" field to <code>null</code> in all instances.  This will often save memory when
            the raw data had been placed in that field. */
    public void removeSources() {
        for (int i = 0; i < this.size(); i++)
            get(i).clearSource();
    }

    /** Constructs a new <code>InstanceList</code>, deserialized from <code>file</code>.  If the
            string value of <code>file</code> is "-", then deserialize from {@link System.in}. */
    public static InstanceList load (File file) {
        try {
            ObjectInputStream ois;
            if (file.toString().equals("-"))
                ois = new ObjectInputStream (System.in);
            else
                ois = new ObjectInputStream (new BufferedInputStream(new FileInputStream (file)));

            InstanceList ilist = (InstanceList) ois.readObject();
            ois.close();
            return ilist;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException ("*** It looks like you might be trying to load an older Mallet instance list. Mallet 2.1 breaks backwards compatibility, you may need to re-import files. ***");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException ("Couldn't read InstanceList from file "+file);
        }
    }

    /** Saves this <code>InstanceList</code> to <code>file</code>.
            If the string value of <code>file</code> is "-", then
            serialize to {@link System.out}. */
    public void save (File file) {
        try {
            ObjectOutputStream ois;
            if (file.toString().equals("-")) {
                ois = new ObjectOutputStream (System.out);
            }
            else {
                ois = new ObjectOutputStream (new FileOutputStream (file));
            }
            ois.writeObject(this);
            ois.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException ("Couldn't save InstanceList to file "+file);
        }
    }

    // Serialization of InstanceList

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 1;

    private void writeObject (ObjectOutputStream out) throws IOException {
        int i, size;
        out.writeInt (CURRENT_SERIAL_VERSION);
        out.writeObject(instWeights);
        out.writeObject(pipe);
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int i, size;
        int version = in.readInt ();
        instWeights = (HashMap<Instance,Double>) in.readObject();
        pipe = (Pipe) in.readObject();
        if (dataAlphabet == null) {
            if (size()>0) { 
                Instance instance = get(0); 
                dataAlphabet = instance.getDataAlphabet (); 
            }  else if (pipe.getDataAlphabet()!=null) {
                dataAlphabet = pipe.getDataAlphabet ();
            }
        }
        if (targetAlphabet == null) {
            if (size()>0) { 
                Instance instance = get(0); 
                targetAlphabet = instance.getTargetAlphabet (); 
            }  else if (pipe.getTargetAlphabet()!=null) {
                targetAlphabet = pipe.getTargetAlphabet ();
            }
        }
    }

    // added - culotta@cs.umass.edu
    /**
         <code>CrossValidationIterator</code> allows iterating over pairs of
         <code>InstanceList</code>, where each pair is split into training/testing
         based on nfolds.
     */    
    public class CrossValidationIterator implements java.util.Iterator<InstanceList[]>, Serializable {
        int nfolds;
        InstanceList[] folds;
        int index;

        /**
             @param _nfolds number of folds to split InstanceList into
             @param seed seed for random number used to split InstanceList
         */
        public CrossValidationIterator (int _nfolds, int seed) {            
            assert (_nfolds > 0) : "nfolds: " + nfolds;
            this.nfolds = _nfolds;
            this.index = 0;
            this.init(seed);
        }

        public CrossValidationIterator (int _nfolds) {
            this (_nfolds, 1);
        }

        /**
         * Initialize the folds of this Cross Validation instance.
         * @param seed seed for random number used to split InstanceList
         * */
        void init(long seed) {
          double fraction = 1.0 / this.nfolds;
          double[] proportions = new double[this.nfolds];
          for (int i=0; i < this.nfolds; i++) {
              proportions[i] = fraction;
          }
          folds = split (new java.util.Random (seed), proportions);
        }

        @Override public boolean hasNext () { return index < nfolds; }

        /**
         * Returns the next training/testing split.
         * @return A pair of lists, where <code>InstanceList[0]</code> is the larger split (training)
         *         and <code>InstanceList[1]</code> is the smaller split (testing)
         */
        public InstanceList[] nextSplit () {
            InstanceList[] ret = new InstanceList[2];
            ret[0] = new InstanceList (pipe);
            for (int i=0; i < folds.length; i++) {
                if (i==index) { continue; }
                Iterator<Instance> iter = folds[i].iterator();
                while (iter.hasNext()) {
                    ret[0].add (iter.next());
                }
            }
            ret[1] = folds[index].shallowClone();
            index++;
            return ret;
        }

        /** Returns the next split, given the number of folds you want in
         *   the training data.  */
        public InstanceList[] nextSplit (int numTrainFolds) {
            InstanceList[] ret = new InstanceList[2];
            ret[0] = new InstanceList (pipe);
            ret[1] = new InstanceList (pipe);

            // train on folds [index, index+numTrainFolds), test on rest
            for (int i = 0; i < folds.length; i++) {
                int foldno = (index + i) % folds.length;
                InstanceList addTo;
                if (i < numTrainFolds) {
                    addTo = ret[0];
                } else {
                    addTo = ret[1];
                }

                Iterator<Instance> iter = folds[foldno].iterator();
                while (iter.hasNext()) {
                    addTo.add (iter.next());
                }
            }
            index++;
            return ret;
        }

        @Override public InstanceList[] next () { return nextSplit(); }        
        @Override public void remove () { throw new UnsupportedOperationException(); }
    }


    /**
     * <code>StratifiedCrossValidationIterator</code> allows iterating over pairs of
     * <code>InstanceList</code>, where each pair is split into training/testing
     * based on nfolds, and each fold maintains the distribution properties of the
     * original InstanceList as much as possible.
     * <p>
     * If the target alphabet of this {@link InstanceList}, which we split for cross
     * validation, is null, then classic cross validation is used instead.
     * 
     * TODO: Implement stratified split, even when the target alphabet is null.
     * @author George Valkanas (lebiathan@gmail.com)
     */    
    public class StratifiedCrossValidationIterator extends CrossValidationIterator {

      public StratifiedCrossValidationIterator(int numFolds) {
        super(numFolds);
      }

      public StratifiedCrossValidationIterator(int numFolds, int seed) {
        super(numFolds, seed);
      }

      /**
       * Initialize the folds of this Cross Validation instance.
       * @param seed seed for random number used to split InstanceList
       * */
      @Override
      void init(long seed) {
        double fraction = 1.0 / this.nfolds;
        double[] proportions = new double[this.nfolds];
        for (int i = 0; i < this.nfolds; i++) {
          proportions[i] = fraction;
        }
        folds = stratifiedSplit(new java.util.Random(seed), proportions);
      }
    }


    /** Returns the pipe through which each added <code>Instance</code> is passed,
     * which may be <code>null</code>. */
    public Pipe getPipe () {
        return pipe;
    }

    /** Change the default Pipe associated with InstanceList.
     * This method is very dangerous and should only be used in extreme circumstances!! */
    public void setPipe(Pipe p) {
        assert (Alphabet.alphabetsMatch(this, p));
        pipe = p;
    }


    /** Returns the <code>Alphabet</code> mapping features of the data to
     * integers. */
    public Alphabet getDataAlphabet () {
        if (dataAlphabet == null && pipe != null) {
            dataAlphabet = pipe.getDataAlphabet ();
        }
        assert (pipe == null
                || pipe.getDataAlphabet () == null
                || pipe.getDataAlphabet () == dataAlphabet);
        return dataAlphabet;
    }
    
    /** Returns the <code>Alphabet</code> mapping target output labels to
     * integers. */
    public Alphabet getTargetAlphabet () {
        if (targetAlphabet == null && pipe != null) {
            targetAlphabet = pipe.getTargetAlphabet ();
        }
        assert (pipe == null
                || pipe.getTargetAlphabet () == null
                || pipe.getTargetAlphabet () == targetAlphabet);
        return targetAlphabet;
    }
    
    @Override public Alphabet getAlphabet () {
        return getDataAlphabet();
    }
    
    @Override public Alphabet[] getAlphabets () {
        return new Alphabet[] {getDataAlphabet(), getTargetAlphabet() };
    }
    
    public LabelVector targetLabelDistribution () {
        if (this.size() == 0) {return null;}
        if (!(get(0).getTarget() instanceof Labeling)) {
            throw new IllegalStateException ("Target is not a labeling.");
        }
        double[] counts = new double[getTargetAlphabet().size()];
        for (int i = 0; i < this.size(); i++) {
            Instance instance =  get(i);
            Labeling l = (Labeling) instance.getTarget();
            l.addTo (counts, getInstanceWeight(i));
        }
        return new LabelVector ((LabelAlphabet)getTargetAlphabet(), counts);
    }


    public CrossValidationIterator crossValidationIterator (int nfolds, int seed) {
        return new CrossValidationIterator(nfolds, seed);
    }

    public CrossValidationIterator crossValidationIterator (int nfolds) {
        return new CrossValidationIterator(nfolds);
    }

    public static final String TARGET_PROPERTY = "target";

    // I'm not sure these methods best belong here. On the other hand it is easy to find and centrally located here. -AKM Jan 2006
    public void hideSomeLabels (double proportionToHide, Randoms r) {
        for (int i = 0; i < this.size(); i++) {
            if (r.nextBoolean(proportionToHide)) {
                Instance instance = this.get(i);
                instance.unLock();
                if (instance.getProperty(TARGET_PROPERTY) != instance.getTarget()) {
                    instance.setProperty(TARGET_PROPERTY, instance.getTarget());
                }
                instance.setTarget (null);
                instance.lock();
            }
        }
    }

    public void hideSomeLabels (BitSet bs) {
        for (int i = 0; i < this.size(); i++) {
            if (bs.get(i)) {
                Instance instance = this.get(i);
                instance.unLock();
                if (instance.getProperty(TARGET_PROPERTY) != instance.getTarget()) {
                    instance.setProperty(TARGET_PROPERTY, instance.getTarget());
                }
                instance.setTarget (null);
                instance.lock();
            }
        }
    }

    public void unhideAllLabels () {
        for (int i = 0; i < this.size(); i++) {
            Instance instance = this.get(i);
            Object t;
            if (instance.getTarget() == null && (t=instance.getProperty(TARGET_PROPERTY)) != null) {
                instance.unLock();
                instance.setTarget(t);
                instance.lock();
            }
        }
    }



}
