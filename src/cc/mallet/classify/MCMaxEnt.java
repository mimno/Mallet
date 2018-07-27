/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */





package cc.mallet.classify;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.DenseVector;
import cc.mallet.types.FeatureSelection;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;

/**
 * Maximum Entropy classifier.
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class MCMaxEnt extends Classifier implements Serializable
{
    double [] parameters;                                        // indexed by <labelIndex,featureIndex>
    int defaultFeatureIndex;
    FeatureSelection featureSelection;
    FeatureSelection[] perClassFeatureSelection;

    // The default feature is always the feature with highest index
    public MCMaxEnt (Pipe dataPipe,
                   double[] parameters,
                   FeatureSelection featureSelection,
                   FeatureSelection[] perClassFeatureSelection)
    {
        super (dataPipe);
        assert (featureSelection == null || perClassFeatureSelection == null);
        this.parameters = parameters;
        this.featureSelection = featureSelection;
        this.perClassFeatureSelection = perClassFeatureSelection;
        this.defaultFeatureIndex = dataPipe.getDataAlphabet().size();
//        assert (parameters.getNumCols() == defaultFeatureIndex+1);
    }

    public MCMaxEnt (Pipe dataPipe,
                   double[] parameters,
                   FeatureSelection featureSelection)
    {
        this (dataPipe, parameters, featureSelection, null);
    }

    public MCMaxEnt (Pipe dataPipe,
                   double[] parameters,
                   FeatureSelection[] perClassFeatureSelection)
    {
        this (dataPipe, parameters, null, perClassFeatureSelection);
    }

    public MCMaxEnt (Pipe dataPipe,
                   double[] parameters)
    {
        this (dataPipe, parameters, null, null);
    }

    public double[] getParameters ()
    {
        return parameters;
    }

    public void setParameter (int classIndex, int featureIndex, double value)
    {
        parameters[classIndex*(getAlphabet().size()+1) + featureIndex] = value;
    }

    public void getUnnormalizedClassificationScores (Instance instance, double[] scores)
    {
              //  arrayOutOfBounds if pipe has grown since training 
              //        int numFeatures = getAlphabet().size() + 1;
        int numFeatures = this.defaultFeatureIndex + 1;

        int numLabels = getLabelAlphabet().size();
        assert (scores.length == numLabels);
        FeatureVector fv = (FeatureVector) instance.getData ();
        // Make sure the feature vector's feature dictionary matches
        // what we are expecting from our data pipe (and thus our notion
        // of feature probabilities.
        assert (fv.getAlphabet ()
                == this.instancePipe.getDataAlphabet ());

        // Include the feature weights according to each label
        for (int li = 0; li < numLabels; li++) {
            scores[li] = parameters[li*numFeatures + defaultFeatureIndex]
                    + MatrixOps.rowDotProduct (parameters, numFeatures,
                            li, fv,
                            defaultFeatureIndex,
                            (perClassFeatureSelection == null
                    ? featureSelection
                    : perClassFeatureSelection[li]));
        }
    }

    public void getClassificationScores (Instance instance, double[] scores)
    {
        int numLabels = getLabelAlphabet().size();
        assert (scores.length == numLabels);
        FeatureVector fv = (FeatureVector) instance.getData ();
        // Make sure the feature vector's feature dictionary matches
        // what we are expecting from our data pipe (and thus our notion
        // of feature probabilities.
        assert (instancePipe == null || fv.getAlphabet () == this.instancePipe.getDataAlphabet ());
              //  arrayOutOfBounds if pipe has grown since training 
              //        int numFeatures = getAlphabet().size() + 1;
        int numFeatures = this.defaultFeatureIndex + 1;

        // Include the feature weights according to each label
        for (int li = 0; li < numLabels; li++) {
            scores[li] = parameters[li*numFeatures + defaultFeatureIndex]
                    + MatrixOps.rowDotProduct (parameters, numFeatures,
                            li, fv,
                            defaultFeatureIndex,
                            (perClassFeatureSelection == null
                    ? featureSelection
                    : perClassFeatureSelection[li]));
            // xxxNaN assert (!Double.isNaN(scores[li])) : "li="+li;
        }

        // Move scores to a range where exp() is accurate, and normalize
        double max = MatrixOps.max (scores);
        double sum = 0;
        for (int li = 0; li < numLabels; li++)
            sum += (scores[li] = Math.exp (scores[li] - max));
        for (int li = 0; li < numLabels; li++) {
            scores[li] /= sum;
            // xxxNaN assert (!Double.isNaN(scores[li]));
        }
    }

    @Override public Classification classify (Instance instance)
    {
        int numClasses = getLabelAlphabet().size();
        double[] scores = new double[numClasses];
        getClassificationScores (instance, scores);
        // Create and return a Classification object
        return new Classification (instance, this,
                new LabelVector (getLabelAlphabet(),
                        scores));
    }

    @Override public void print () 
    {        
        final Alphabet dict = getAlphabet();
        final LabelAlphabet labelDict = getLabelAlphabet();
                
        int numFeatures = dict.size() + 1;
        int numLabels = labelDict.size();
        
         // Include the feature weights according to each label
         for (int li = 0; li < numLabels; li++) {
             System.out.println ("FEATURES FOR CLASS "+labelDict.lookupObject (li));
             System.out.println (" <default> "+parameters [li*numFeatures + defaultFeatureIndex]);
             for (int i = 0; i < defaultFeatureIndex; i++) {
                 Object name = dict.lookupObject (i);
                double weight = parameters [li*numFeatures + i];
                 System.out.println (" "+name+" "+weight);
             }
         }
    }
    
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 1;
    static final int NULL_INTEGER = -1;

    private void writeObject(ObjectOutputStream out) throws IOException
    {
        out.writeInt(CURRENT_SERIAL_VERSION);
        out.writeObject(getInstancePipe());
        int np = parameters.length;
        out.writeInt(np);
        for (int p = 0; p < np; p++)
            out.writeDouble(parameters[p]);
        out.writeInt(defaultFeatureIndex);
        if (featureSelection == null)
            out.writeInt(NULL_INTEGER);
        else
        {
            out.writeInt(1);
            out.writeObject(featureSelection);
        }
        if (perClassFeatureSelection == null)
            out.writeInt(NULL_INTEGER);
        else
        {
            out.writeInt(perClassFeatureSelection.length);
            for (int i = 0; i < perClassFeatureSelection.length; i++)
                if (perClassFeatureSelection[i] == null)
                    out.writeInt(NULL_INTEGER);
                else
                {
                    out.writeInt(1);
                    out.writeObject(perClassFeatureSelection[i]);
                }
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        if (version != CURRENT_SERIAL_VERSION)
            throw new ClassNotFoundException("Mismatched MCMaxEnt versions: wanted " +
                    CURRENT_SERIAL_VERSION + ", got " +
                    version);
        instancePipe = (Pipe) in.readObject();
        int np = in.readInt();
        parameters = new double[np];
        for (int p = 0; p < np; p++)
            parameters[p] = in.readDouble();
        defaultFeatureIndex = in.readInt();
        int opt = in.readInt();
        if (opt == 1)
            featureSelection = (FeatureSelection)in.readObject();
        int nfs = in.readInt();
        if (nfs >= 0)
        {
            perClassFeatureSelection = new FeatureSelection[nfs];
            for (int i = 0; i < nfs; i++)
            {
                opt = in.readInt();
                if (opt == 1)
                    perClassFeatureSelection[i] = (FeatureSelection)in.readObject();
            }
        }
    }
}

