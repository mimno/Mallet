/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Random;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

/**
 * An implementation of InstanceList that logically combines multiple instance
 * lists so that they appear as one list without copying the original lists.
 * This is useful when running cross-validation experiments with large data sets.
 * 
 * Any operation that would modify the size of the list is not supported.
 * 
 * @see InstanceList
 * 
 * @author Michael Bond <a href="mailto:mbond@gmail.com">mbond@gmail.com</a>
 */
public class MultiInstanceList extends InstanceList {

    private static final long serialVersionUID = -7177121200386974657L;
    private static final InstanceList[] EMPTY_ARRAY = new InstanceList[0];

    private final InstanceList[] lists;
    private final int[] offsets;
    
    private class MultiIterator implements Iterator<Instance>, Serializable {

        private static final long serialVersionUID = -2446488635289279133L;
        int index = 0;
        Iterator<Instance> i;
        
        public MultiIterator () {
            this.i = lists.length == 0 ? null : lists[0].iterator ();
        }
        
        @Override public boolean hasNext () {
            if (this.index < lists.length) {
                if (this.i.hasNext ()) {
                    return true;
                }

                for (int tmpIndex = this.index + 1; tmpIndex < lists.length; tmpIndex++) {
                    final InstanceList list = lists[tmpIndex];
                    if (list != null && lists[tmpIndex].size () > 0) {
                        return true;
                    }
                }
            }
            
            return false;
        }

        @Override public Instance next () {
            if (this.index < lists.length) {
                if (this.i.hasNext ()) {
                    return this.i.next ();
                }
                
                for (this.index++; this.index < lists.length; this.index++) {
                    final InstanceList list = lists[this.index];
                    if (list != null && lists[this.index].size () > 0) {
                        this.i = lists[this.index].iterator ();
                        return this.i.next ();
                    }
                }
            }
            throw new NoSuchElementException ();
        }

        @Override public void remove () {
            throw new UnsupportedOperationException ();
        }
        
    }
    
    /**
     * Constructs a {@link MultiInstanceList} with an array of {@link InstanceList}
     * 
     * @param lists Array of {@link InstanceList} to logically combine
     */
    public MultiInstanceList (InstanceList[] lists) {
        super (lists[0].getPipe ());
        this.lists = lists;
        this.offsets = new int[lists.length];
        
        // build index offsets array and populate instance weights
        int offset = 0;
        for (int i = 0; i < lists.length; i++) {
            this.offsets[i] = offset;
            offset += lists[i].size ();
            if (lists[i].instWeights != null) {
                if (this.instWeights == null) {
                    this.instWeights = new HashMap<Instance,Double> ();
                }
                this.instWeights.putAll (instWeights);
            }
        }
    }
    
    /**
     * Constructs a {@link MultiInstanceList} with a {@link List} of {@link InstanceList}
     * 
     * @param lists List of {@link InstanceList} to logically combine
     */
    public MultiInstanceList (List<InstanceList> lists) {
        this (lists.toArray (EMPTY_ARRAY));
    }
    
    @Override public boolean add (Instance instance, double instanceWeight) {
        throw new UnsupportedOperationException ();
    }

    @Override public boolean add (Instance instance) {
        throw new UnsupportedOperationException ();
    }

    @Override public void add (int index, Instance element) {
        throw new UnsupportedOperationException ();
    }

    @Override public void clear () {
        throw new UnsupportedOperationException ();
    }

    @Override public Object clone () {
        InstanceList[] newLists = new InstanceList[this.lists.length];
        for (int i = 0; i < this.lists.length; i++) {
            newLists[i] = (InstanceList) this.lists[i].clone ();
        }
        
        return new MultiInstanceList (newLists);
    }

    @Override public InstanceList cloneEmpty () {
        InstanceList[] newLists = new InstanceList[this.lists.length];
        for (int i = 0; i < this.lists.length; i++) {
            newLists[i] = this.lists[i].cloneEmpty ();
        }
        
        return new MultiInstanceList (newLists);
    }

    @Override protected InstanceList cloneEmptyInto (InstanceList ret) {
        throw new UnsupportedOperationException ();
    }

    @Override public boolean contains (Object elem) {
        for (InstanceList list : this.lists) {
            if (list != null && list.contains (elem)) {
                return true;
            }
        }
        return false;
    }

    @Override public CrossValidationIterator crossValidationIterator (int nfolds, int seed) {
        throw new UnsupportedOperationException ();
    }

    @Override public CrossValidationIterator crossValidationIterator (int nfolds) {
        throw new UnsupportedOperationException ();
    }

    @Override public void ensureCapacity (int minCapacity) {
        throw new UnsupportedOperationException ();
    }

    @Override public boolean equals (Object o) {
        if (o instanceof MultiInstanceList) {
            MultiInstanceList tmp = (MultiInstanceList) o;
            if (tmp.lists.length != this.lists.length) {
                return false;
            }
            
            for (int i = 0; i < this.lists.length; i++) {
                InstanceList thisList = this.lists[i];
                InstanceList tmpList = tmp.lists[i];

                if (thisList == null && tmpList != null) {
                    return false;
                } else if (!thisList.equals (tmpList)) {
                    return false;
                }
            }
            
            return true;
        }
        return false;
    }

    @Override public Instance get (int index) {
        int i = getOffsetIndex (index);
        return this.lists[i].get (index - this.offsets[i]);
    }

    /**
     * Gets the index into the offsets array for the given element index
     * 
     * @param index     Index of element
     * @return Index into offsets, will always give a valid index
     */
    private int getOffsetIndex (int index) {
        int i = Arrays.binarySearch (this.offsets, index);
        if (i < 0) {
            i = (-i) - 2;
        }
        return i;
    }
    
    @Override public int hashCode () {
        int hashCode = 1;
        for (InstanceList list : this.lists) {
            hashCode = 31*hashCode + (list==null ? 0 : list.hashCode ());
        }
        return hashCode;
    }

    @Override public int indexOf (Object elem) {
        for (int i = 0; i < this.lists.length; i++) {
            int index = this.lists[i].indexOf (elem);
            if (index != -1) {
                return index + this.offsets[i];
            }
        }
        return -1;
    }

    @Override public boolean isEmpty () {
        for (InstanceList list : this.lists) {
            if (list != null && !list.isEmpty ()) {
                return true;
            }
        }
        return false;
    }

    @Override public Iterator<Instance> iterator () {
        return new MultiIterator ();
    }

    @Override
    public int lastIndexOf (Object elem) {
        for (int i = this.lists.length - 1; i >= 0; i--) {
            int index = this.lists[i].lastIndexOf (elem);
            if (index != -1) {
                return index + this.offsets[i];
            }
        }
        return -1;
    }

    @Override
    public ListIterator<Instance> listIterator () {
        throw new UnsupportedOperationException ();
    }

    @Override
    public ListIterator<Instance> listIterator (int index) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public boolean remove (Instance instance) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Instance remove (int index) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public boolean remove (Object o) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Instance set (int index, Instance instance) {
        int i = getOffsetIndex (index);
        return this.lists[i].set (index - this.offsets[i], instance);
    }

    @Override
    public void setInstance (int index, Instance instance) {
        int i = getOffsetIndex (index);
        this.lists[i].setInstance (index - this.offsets[i], instance);
    }

    @Override
    public void setInstanceWeight (Instance instance, double weight) {
        super.setInstanceWeight (instance, weight);
        int index = indexOf (instance);
        int i = getOffsetIndex (index);
        this.lists[i].setInstanceWeight (index - this.offsets[i], weight);
    }

    @Override
    public InstanceList shallowClone () {
        InstanceList[] newLists = new InstanceList[this.lists.length];
        for (int i = 0; i < this.lists.length; i++) {
            newLists[i] = this.lists[i].shallowClone ();
        }
        
        return new MultiInstanceList (newLists);
    }

    @Override
    public void shuffle (Random r) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public int size () {
        int size = 0;
        for (InstanceList list : this.lists) {
            if (list != null) {
                size += list.size ();
            }
        }
        return size;
    }

    @Override
    public InstanceList[] split (double[] proportions) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList[] split (Random r, double[] proportions) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList[] splitInOrder (double[] proportions) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList[] splitInOrder (int[] counts) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList[] splitInTwoByModulo (int m) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList subList (double proportion) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InstanceList subList (int start, int end) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Object[] toArray () {
        Object[] result = new Object[size ()];
        int i = 0;
        for (InstanceList list : this.lists) {
            if (list != null) {
                for (Instance instance : list) {
                    result[i++] = instance;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray (T[] a) {
        int size = size ();
        if (a.length < size) {
            a = (T[])java.lang.reflect.Array
                .newInstance (a.getClass ().getComponentType (), size);
        }

        Object[] result = a;
        int i = 0;
        for (InstanceList list : this.lists) {
            if (list != null) {
                for (Instance instance : list) {
                    result[i++] = instance;
                }
            }
        }
        if (a.length > size)
            a[size] = null;
        return a;
    }

    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder ();
        buf.append ("[");

        for (int listIndex = 0; listIndex < this.lists.length; listIndex++) {
            if (this.lists[listIndex] != null) {
                Iterator<Instance> i = this.lists[listIndex].iterator ();
                boolean hasNext = i.hasNext ();
                while (hasNext) {
                    Instance o = i.next ();
                    buf.append (String.valueOf (o));
                    hasNext = i.hasNext ();
                    if (listIndex < this.lists.length || hasNext) {
                        buf.append (", ");
                    }
                }
            }
        }

        buf.append ("]");
        return buf.toString ();
    }

    @Override
    public void trimToSize () {
        for (InstanceList list : this.lists) {
            list.trimToSize ();
        }
    }

}
