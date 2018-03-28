/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




package cc.mallet.types;

import java.util.logging.*;
import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Labeling;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.PropertyList;

/**
     A machine learning "example" to be used in training, testing or
     performance of various machine learning algorithms.

     <p>An instance contains four generic fields of predefined name:
     "data", "target", "name", and "source".   "Data" holds the data represented
    `by the instance, "target" is often a label associated with the instance,
     "name" is a short identifying name for the instance (such as a filename),
     and "source" is human-readable sourceinformation, (such as the original text).

     <p> Each field has no predefined type, and may change type as the instance
     is processed. For example, the data field may start off being a string that
     represents a file name and then be processed by a {@link cc.mallet.pipe.Pipe} into a CharSequence
     representing the contents of the file, and eventually to a feature vector
     holding indices into an {@link cc.mallet.types.Alphabet} holding words found in the file.
     It is up to each pipe which fields in the Instance it modifies; the most common
     case is that the pipe modifies the data field.

     <p>Generally speaking, there are two modes of operation for
     Instances.  (1) An instance gets created and passed through a
     Pipe, and the resulting data/target/name/source fields are used.
     This is generally done for training instances.  (2) An instance
     gets created with raw values in its slots, then different users
     of the instance call newPipedCopy() with their respective
     different pipes.  This might be done for test instances at
     "performance" time.
     
     <p> Rather than store an {@link cc.mallet.types.Alphabet} in the Instance,
     we obtain it through the Pipe instance variable, because the Pipe also
     indicates where the data came from and how to interpret the Alphabet. 

     <p>Instances can be made immutable if locked.
     Although unlocked Instances are mutable, typically the only code that
     changes the values in the four slots is inside Pipes.

     <p> Note that constructing an instance with a pipe argument means
     "Construct the instance and then run it through the pipe".
     {@link cc.mallet.types.InstanceList} uses this method
     when adding instances through a pipeInputIterator.

   @see Pipe
   @see Alphabet
   @see InstanceList

   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class Instance implements Serializable, AlphabetCarrying, Cloneable {
    private static Logger logger = MalletLogger.getLogger(Instance.class.getName());

    protected Object data;                // The input data in digested form, e.g. a FeatureVector
    protected Object target;            // The output data in digested form, e.g. a Label
    protected Object name;                // A readable name of the source, e.g. for ML error analysis
    protected Object source;            /* The input in a reproducable form, e.g. enabling re-print of
                                                                     string w/ POS tags, usually without target information,
                                                                     e.g. an un-annotated RegionList. */
    PropertyList properties = null;
    boolean locked = false;


    /** In certain unusual circumstances, you might want to create an Instance 
     * without sending it through a pipe.
     */
    public Instance (Object data, Object target, Object name, Object source)
    {
        this.data = data;
        this.target = target;
        this.name = name;
        this.source = source;
    }


    public Object getData () { return data; }
    public Object getTarget () { return target; }
    public Object getName () { return name; }
    public Object getSource () { return source; }
    
    public Alphabet getDataAlphabet() {
        if (data instanceof AlphabetCarrying)
            return ((AlphabetCarrying)data).getAlphabet();
        else
            return null;
    }

    public Alphabet getTargetAlphabet() {
        if (target instanceof AlphabetCarrying)
            return ((AlphabetCarrying)target).getAlphabet();
        else
            return null;
    }
    
    @Override public Alphabet getAlphabet () {
        return getDataAlphabet();
    }
    
    @Override public Alphabet[] getAlphabets() {
        return new Alphabet[] {getDataAlphabet(), getTargetAlphabet()};
    }
    
    public boolean alphabetsMatch (AlphabetCarrying object) {
        Alphabet[] oas = object.getAlphabets();
        return  oas.length == 2 && oas[0].equals(getDataAlphabet()) && oas[1].equals(getDataAlphabet());
    }
    
    public boolean isLocked () { return locked; }
    public void lock() { locked = true; }
    public void unLock() { locked = false; }
    
    public Labeling getLabeling () {
        if (target == null || target instanceof Labeling) {
            return (Labeling)target;
        }
        throw new IllegalStateException ("Target is not a Labeling; it is a "+target.getClass().getName());
    }

    public void setData (Object d) {
        if (!locked) data = d;
        else throw new IllegalStateException ("Instance is locked.");
    }
    
    public void setTarget (Object t) {
        if (!locked) target = t;
        else throw new IllegalStateException ("Instance is locked.");
    }
    
    public void setLabeling (Labeling l) {
        // This test isn't strictly necessary, but might catch some typos.
        assert (target == null || target instanceof Labeling);
        if (!locked) target = l;
        else throw new IllegalStateException ("Instance is locked.");
    }
    
    public void setName (Object n) {
        if (!locked) name = n;
        else throw new IllegalStateException ("Instance is locked.");
    }
    
    public void setSource (Object s) {
        if (!locked) source = s;
        else throw new IllegalStateException ("Instance is locked.");
    }
    
    public void clearSource () {
        source = null;
    }

    public Instance shallowCopy () {
        Instance ret = new Instance (data, target, name, source);
        ret.locked = locked;
        ret.properties = properties;
        return ret;
    }
    
    @Override public Object clone () {
        return shallowCopy();
    }
    
    // Setting and getting properties
    
    public void setProperty (String key, Object value) {
        properties = PropertyList.add (key, value, properties);
    }

    public void setNumericProperty (String key, double value) {
        properties = PropertyList.add (key, value, properties);
    }

    @Deprecated
    public PropertyList getProperties () {
        return properties;
    }

    @Deprecated
    public void setPropertyList (PropertyList p) {
        if (!locked) properties = p;
        else throw new IllegalStateException ("Instance is locked.");
    }

    public Object getProperty (String key) {
        return properties == null ? null : properties.lookupObject (key);
    }

    public double getNumericProperty (String key) {
        return (properties == null ? 0.0 : properties.lookupNumber (key));
    }

    public boolean hasProperty (String key) {
        return (properties == null ? false : properties.hasProperty (key));
    }

    // Serialization of Instance

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
        out.writeObject(data);
        out.writeObject(target);
        out.writeObject(name);
        out.writeObject(source);
        out.writeObject(properties);
        out.writeBoolean(locked);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt ();
        data = in.readObject();
        target = in.readObject();
        name = in.readObject();
        source = in.readObject();
        properties = (PropertyList) in.readObject();
        locked = in.readBoolean();
    }

}
