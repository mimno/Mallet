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
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.LabelVector;
import cc.mallet.types.MatrixOps;


/** 
 * Classification methods of BalancedWinnow algorithm.
 *
 * @see BalancedWinnowTrainer
 * @author Gary Huang <a href="mailto:ghuang@cs.umass.edu">ghuang@cs.umass.edu</a>
 */
public class BalancedWinnow extends Classifier implements Serializable
{
    double [][] m_weights;
	
    /**
     * Passes along data pipe and weights from 
     * {@link #BalancedWinnowTrainer BalancedWinnowTrainer}
     * @param dataPipe needed for dictionary, labels, feature vectors, etc
     * @param weights weights calculated during training phase
     */
    public BalancedWinnow (Pipe dataPipe, double [][] weights)
    {
        super (dataPipe);
        m_weights = new double[weights.length][weights[0].length];
        for (int i = 0; i < weights.length; i++)
			for (int j = 0; j < weights[0].length; j++)
				m_weights[i][j] = weights[i][j];
    }
	
    /**
     * @return a copy of the weight vectors
     */
    public double[][] getWeights()
    {
        int numCols = m_weights[0].length;
        double[][] ret = new double[m_weights.length][numCols];
        for (int i = 0; i < ret.length; i++)
			System.arraycopy(m_weights[i], 0, ret[i], 0, numCols);
        return ret;
    }
	
    /**
     * Classifies an instance using BalancedWinnow's weights
     *
     * <p>Returns a Classification containing the normalized
     * dot products between class weight vectors and the instance 
     * feature vector.
     *
     * <p>One can obtain the confidence of the classification by 
     * calculating weight(j')/weight(j), where j' is the 
     * highest weight prediction and j is the 2nd-highest.
     * Another possibility is to calculate 
     * <br><tt><center>e^{dot(w_j', x} / sum_j[e^{dot(w_j, x)}]</center></tt>
     */
    public Classification classify (Instance instance)
    {
        int numClasses = getLabelAlphabet().size();
        int numFeats = getAlphabet().size();
        double[] scores = new double[numClasses];
        FeatureVector fv = (FeatureVector) instance.getData ();

        // Make sure the feature vector's feature dictionary matches
        // what we are expecting from our data pipe (and thus our notion
        // of feature probabilities.
        assert (instancePipe == null || fv.getAlphabet () == this.instancePipe.getDataAlphabet ());
        int fvisize = fv.numLocations();

        // Take dot products
        double sum = 0;
        for (int ci = 0; ci < numClasses; ci++) {
			for (int fvi = 0; fvi < fvisize; fvi++) {
				int fi = fv.indexAtLocation (fvi);
				double vi = fv.valueAtLocation(fvi);

				if ( m_weights[ci].length > fi ) {
				scores[ci] += vi * m_weights[ci][fi];
				sum += vi * m_weights[ci][fi];
				}
			}
			scores[ci] += m_weights[ci][numFeats];
			sum += m_weights[ci][numFeats];
        }
        MatrixOps.timesEquals(scores, 1.0 / sum);

        // Create and return a Classification object
        return new Classification (instance, this, new LabelVector (getLabelAlphabet(), scores));
    }

    // Serialization
    // serialVersionUID is overriden to prevent innocuous changes in this
    // class from making the serialization mechanism think the external
    // format has changed.

    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 1;

    private void writeObject(ObjectOutputStream out) throws IOException
    {
        out.writeInt(CURRENT_SERIAL_VERSION);
        out.writeObject(getInstancePipe());
        
        // write weight vector for each class
        out.writeObject(m_weights);
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        if (version != CURRENT_SERIAL_VERSION)
			throw new ClassNotFoundException("Mismatched BalancedWinnow versions: wanted " +
											 CURRENT_SERIAL_VERSION + ", got " +
											 version);
        instancePipe = (Pipe) in.readObject();
        m_weights = (double[][]) in.readObject();
        
    }
    
}

