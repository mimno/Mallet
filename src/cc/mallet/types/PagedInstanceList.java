/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.MatrixOps;

/**
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

      @see InstanceList

@author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

public class PagedInstanceList extends InstanceList
{
    
    private static final char TYPE_FEATURE_VECTOR       = 'F';
    private static final char TYPE_LABEL                = 'L';
    private static final char TYPE_OBJECT               = 'O';

    /** number of instances to put in one page */
    int instancesPerPage;

    /** directory to store swap files */
    File swapDir;

    /** array of page numbers that represent the in-memory pages */
    int[] inMemoryPageIds;
    
    /** array of instance lists that represent the in-memory pages */
    InstanceList[] inMemoryPages;

    /**  dirty.get(i) == true if in-memory bin i is dirty */
    BitSet dirty = new BitSet();
    
    /** Total number of instances in list, including those swapped out */
    int size = 0;

    /** recommend garbage collection after every swap out? */
    boolean collectGarbage = true;

    /** Total number of swap-ins */
    int swapIns = 0;

    /** Total time spent in swap-ins */
    long swapInTime = 0;
    
    /** Total number of swap-outs */
    int swapOuts = 0;

    /** Total time spent in swap-ins */
    long swapOutTime = 0;
    
    /** uniquely identifies this InstanceList. Used in creating
     * serialized page name for swap files. */
    UUID id = UUID.randomUUID();
    
    /** Avoids creating a new noop pipe for each page */
    Pipe noopPipe;

    // CONSTRUCTORS

    /** Creates a PagedInstanceList where "instancesPerPage" instances
     * are swapped to disk in directory "swapDir" if the amount of free
     * system memory drops below "minFreeMemory" bytes
     * @param pipe instance pipe
     * @param numPages number of pages to keep in memory
     * @param instancesPerPage number of Instances to store in each page
     * @param swapDir where the pages on disk live.
     */
    public PagedInstanceList (Pipe pipe, int numPages, int instancesPerPage, File swapDir) {
        super (pipe, numPages * instancesPerPage);
        this.instancesPerPage = instancesPerPage;
        this.swapDir = swapDir;
        this.inMemoryPageIds = new int[numPages];
        this.inMemoryPages = new InstanceList[numPages];
        this.noopPipe = new Noop(pipe.getDataAlphabet(), pipe.getTargetAlphabet());
        for (int i = 0; i < numPages; i++) {
            this.inMemoryPageIds[i] = -1;
        }

        try {
            if (!swapDir.exists()) {
                swapDir.mkdir();
            }
        } catch (SecurityException e) {
            System.err.println ("No permission to make directory " + swapDir);
            System.exit(-1);
        }
    }

    public PagedInstanceList (Pipe pipe, int numPages, int instancesPerPage) {
        this (pipe, numPages, instancesPerPage, new File ("."));
    }

    // SPLITTING AND SAMPLING METHODS

    /** Shuffles elements of an array, taken from Collections.shuffle
     * @param r The source of randomness to use in shuffling.
     * @param a Array to shuffle
     */
    private void shuffleArray (java.util.Random r, int[] a) {
        int size = a.length;

        // Shuffle array
        for (int i = size - 1; i > 0; i--) {
            int swap = r.nextInt(i + 1);
            int tmp = a[i];
            a[i] = a[swap];
            a[swap] = tmp;
        }
    }

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
    public InstanceList[] split (java.util.Random r, double[] proportions) {
        InstanceList[] ret = new InstanceList[proportions.length];
        double maxind[] = proportions.clone();
        int size = size();
        int[] shuffled = new int[size];
        int[] splits = new int[size];

        // build a list of shuffled instance indexes
        for (int i = 0; i < size; i++) {
            shuffled[i] = i;
        }
        shuffleArray(r, shuffled);

        MatrixOps.normalize(maxind);
        for (int i = 0; i < maxind.length; i++) {
            ret[i] = this.cloneEmpty();  // Note that we are passing on featureSelection here.
            if (i > 0) 
                maxind[i] += maxind[i-1];
        }
        for (int i = 0; i < maxind.length; i++) { 
            // Fill maxind[] with the highest instance index to go in each corresponding returned InstanceList
            maxind[i] = Math.rint (maxind[i] * size);
        }
        for (int i = 0, j = 0; i < size; i++) {
            // This gives a slight bias toward putting an extra instance in the last InstanceList.
            while (i >= maxind[j] && j < ret.length) 
                j++;
            splits[shuffled[i]] = j;
        }

        for (int i = 0; i < size; i++) {
            //logger.info ("adding instance " + i + " to split ilist " + splits[i]);
            ret[splits[i]].add(this.get(i));
        }

        return ret;
    }

    // PAGING METHODS

    /** Gets the swap file for the specified page
     * @param page Page to get swap file for
     * @return Swap file
     */
    private File getFileForPage (int page) {
        return new File (swapDir, id + "." + page);
    }
    
    /** Gets the page for the specified instance index, swapping in if necessary
     * @param index Instance index to get page for
     * @param dirty If true mark page as dirty
     * @return Page for the specified instance index
     */
    private InstanceList getPageForIndex (int index, boolean dirty) {
        if (index > this.size) {
            throw new IndexOutOfBoundsException (
                    "Index: " + index + ", Size: "+ this.size);
        }
        
        return swapIn (index / this.instancesPerPage, dirty);
    }
    
    /** Swaps in the specified page
     * @param pageId Page to swap in
     * @param dirty If true mark page as dirty
     * @return The page that was just swapped in */
    private InstanceList swapIn (int pageId, boolean dirty) {
        int bin = pageId % this.inMemoryPages.length;
        if (this.inMemoryPageIds[bin] != pageId) {
            swapOut (this.inMemoryPageIds[bin]);

            long startTime = System.currentTimeMillis ();

            File pageFile = getFileForPage (pageId);
            ObjectInputStream in = null;
            try {
                in = new ObjectInputStream (new FileInputStream (pageFile));
                InstanceList page = deserializePage(in);
                
                this.inMemoryPageIds[bin] = pageId;
                this.inMemoryPages[bin] = page;
            } catch (Exception e) {
                System.err.println (e);
                System.exit (-1);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        System.err.println (e);
                        System.exit (-1);
                    }
                }
            }
            
            this.swapIns++;
            this.swapInTime += System.currentTimeMillis () - startTime;
        }
        
        if (dirty) {
            this.dirty.set (bin);
        }

        return this.inMemoryPages[bin];
    }
    
    /** Swaps out the page in the specified bin if it is dirty
     * @param pageId Page to swap out
     */
    private void swapOut (int pageId) {
        int bin = pageId % this.inMemoryPages.length;
        if (pageId != -1 && this.dirty.get (bin)) {
            long startTime = System.currentTimeMillis ();
            File pageFile = getFileForPage (pageId);
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream (new FileOutputStream (pageFile));
                InstanceList page = this.inMemoryPages[bin];
                this.inMemoryPageIds[bin] = -1;
                this.inMemoryPages[bin] = null;

                serializePage(out, page);

                this.dirty.set(bin, false);
            } catch (Exception e) {
                System.err.println (e);
                System.exit (-1);
            } finally {
                if (out != null) {
                    try {
                        out.close ();
                    }
                    catch (Exception e) {
                        System.err.println (e);
                        System.exit (-1);
                    }
                }
            }
            
            if (this.collectGarbage) {
                System.gc();
            }

            this.swapOuts++;
            this.swapOutTime += System.currentTimeMillis () - startTime;
        }
    }

    // ACCESSORS
    
    /** Appends the instance to this list. Note that since memory for
     * the Instance has already been allocated, no check is made to
     * catch OutOfMemoryError.
     * @return <code>true</code> if successful
     */
    public boolean add (Instance instance) {
        InstanceList page;
        if (this.size % this.instancesPerPage == 0) {
            // this is the start of a new page, swap out the one in this pages
            // spot and create a new one
            int pageId = this.size / this.instancesPerPage;
            int bin = pageId % this.inMemoryPages.length;
            swapOut (this.inMemoryPageIds[bin]);
            page = new InstanceList (this.noopPipe);
            this.inMemoryPageIds[bin] = pageId;
            this.inMemoryPages[bin] = page;
        } else {
            page = getPageForIndex (this.size, true);
        }
        boolean ret = page.add (instance);
        if (ret) {
            this.size++;
        }
        return ret;
    }

    /** Returns the <code>Instance</code> at the specified index. If
     * this Instance is not in memory, swap a block of instances back
     * into memory. */
    public Instance get (int index) {
        InstanceList page = getPageForIndex (index, false);
        return page.get (index % this.instancesPerPage);
    }

    /** Replaces the <code>Instance</code> at position
     * <code>index</code> with a new one. Note that this is the only
     * sanctioned way of changing an Instance. */
    public Instance set (int index, Instance instance) {
        InstanceList page = getPageForIndex (index, true);
        return page.set (index % this.instancesPerPage, instance);
    }

    public boolean getCollectGarbage () {
        return this.collectGarbage;
    }
    
    public void setCollectGarbage (boolean b) {
        this.collectGarbage = b;
    
    }

    public InstanceList shallowClone () {
        InstanceList ret = this.cloneEmpty ();
        for (int i = 0; i < this.size (); i++) {
            ret.add (get (i));
        }
        return ret;
    }

    public InstanceList cloneEmpty () {
        return super.cloneEmptyInto (new PagedInstanceList (
                this.pipe,
                this.inMemoryPages.length,
                this.instancesPerPage,
                this.swapDir)); 
    }

    public void clear () {
        int numPages = this.size / this.instancesPerPage;
        for (int i = 0; i <= numPages; i++) {
            getFileForPage (i).delete ();
        }
        for (int i = 0; i < this.inMemoryPages.length; i++) {
            this.inMemoryPages[i] = null;
            this.inMemoryPageIds[i] = -1;
        }
        this.size = 0;
        this.swapIns = 0;
        this.swapInTime = 0;
        this.swapOuts = 0;
        this.swapOutTime = 0;
        this.dirty.clear ();
        super.clear ();
    }

    public int getSwapIns () {
        return this.swapIns;
    }

    public long getSwapInTime () {
        return this.swapInTime;
    }

    public int getSwapOuts () {
        return this.swapOuts;
    }

    public long getSwapOutTime () {
        return this.swapOutTime;
    }

    public int size () {
        return this.size;
    }
    
    /** Serializes a single object without metadata
     * @param out
     * @param object
     * @throws IOException 
     */
    private void serializeObject (ObjectOutputStream out, Object obj)
    throws IOException {
        if (obj instanceof FeatureVector) {
            FeatureVector features = (FeatureVector) obj;
            out.writeChar (TYPE_FEATURE_VECTOR);
            out.writeObject (features.getIndices ());
            out.writeObject (features.getValues ());
        }
        else if (obj instanceof Label) {
                out.writeChar (TYPE_LABEL);
                out.writeObject (((Label) obj).toString ());
        } else {
            out.writeChar (TYPE_OBJECT);
            out.writeObject (obj);
        }
    }

    /** Serialize a page without metadata. This attempts to serialize the
     * minimum amount needed to restore the page, leaving out redundant data
     * such as pipes and dictionaries.
     * @param out Object output stream
     * @param page
     * @throws IOException 
     */
    private void serializePage (ObjectOutputStream out, InstanceList page)
    throws IOException {
        out.writeInt (page.size ());
        for (Instance inst : page) {
            serializeObject (out, inst.getData ());
            serializeObject (out, inst.getTarget ());
            out.writeObject (inst.getName ());
            out.writeObject (inst.getSource ());
            if (this.instWeights != null) {
                Double weight = this.instWeights.get (inst);
                if (weight != null) {
                    out.writeDouble (this.instWeights.get (inst));
                } else {
                    out.writeDouble (1.0);
                }
            } else {
                out.writeDouble (1.0);
            }
        }
    }
    
    /** Deserialize an object serialized using
     * {@link #serializeObject(ObjectOutputStream, Object)}.
     * @throws IOException 
     * @throws ClassNotFoundException 
     */
    private Object deserializeObject (ObjectInputStream in)
    throws IOException, ClassNotFoundException {
        char type = in.readChar ();
        Object obj;
        
        switch (type) {
        case TYPE_LABEL:
            LabelAlphabet ldict = (LabelAlphabet) getTargetAlphabet ();
            String name = (String) in.readObject ();
            obj = ldict.lookupLabel (name);
            break;
        case TYPE_FEATURE_VECTOR:
            int[] indices = (int[]) in.readObject ();
            double[] values = (double[]) in.readObject ();
            obj = new FeatureVector(getDataAlphabet (), indices, values);
            break;
        case TYPE_OBJECT:
            obj = in.readObject ();
            break;
        default:
            throw new IOException ("Unknown object type " + type);
        }
        
        return obj;
    }
    
    /** Deserialize a page. This restores a page serialized using
     * {@link #serializePage(ObjectOutputStream, InstanceList)}.
     * @param in Object input stream
     * @return New page
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private InstanceList deserializePage(ObjectInputStream in)
    throws IOException, ClassNotFoundException {
        InstanceList page = new InstanceList(noopPipe);
        int size = in.readInt();
        
        for (int i = 0; i < size; i++) {
            Object data = deserializeObject (in);
            Object target = deserializeObject (in);
            Object name = in.readObject ();
            Object source = in.readObject ();
            double weight = in.readDouble ();
            page.add (new Instance (data, target, name, source), weight);
        }
        
        return page;
    }
    
    /** Constructs a new <code>InstanceList</code>, deserialized from
     * <code>file</code>.  If the string value of <code>file</code> is
     * "-", then deserialize from {@link System.in}. */
    public static InstanceList load (File file) {
        try {
            ObjectInputStream ois;
            if (file.toString ().equals ("-"))
                ois = new ObjectInputStream (System.in);
            else
                ois = new ObjectInputStream (new FileInputStream (file));
            PagedInstanceList ilist = (PagedInstanceList) ois.readObject();
            ois.close();
            return ilist;
        } catch (Exception e) {
            e.printStackTrace ();
            throw new IllegalArgumentException ("Couldn't read PagedInstanceList from file "+file);
        }
    }

    // Serialization of PagedInstanceList

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 1;

    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
        out.writeObject (this.id);
        out.writeObject (this.pipe);
        // memory attributes
        out.writeInt (this.instancesPerPage);
        out.writeObject (this.swapDir);
        out.writeObject(this.inMemoryPageIds);
        out.writeObject (this.dirty);
        
        for (int i = 0; i < this.inMemoryPages.length; i++) {
            serializePage(out, this.inMemoryPages[i]);
        }
    }

    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.id = (UUID) in.readObject ();
        this.pipe = (Pipe) in.readObject();
        // memory attributes
        this.instancesPerPage = in.readInt ();
        this.swapDir = (File) in.readObject ();
        this.inMemoryPageIds = (int[]) in.readObject();
        this.dirty = (BitSet) in.readObject ();
        
        this.inMemoryPages = new InstanceList[this.inMemoryPageIds.length];
        for (int i = 0; i < this.inMemoryPageIds.length; i++) {
            this.inMemoryPages[i] = deserializePage(in);
        }
    }
}
