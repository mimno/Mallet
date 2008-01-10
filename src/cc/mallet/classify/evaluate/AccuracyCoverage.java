/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.classify.evaluate;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.Trial;
import cc.mallet.classify.evaluate.GraphItem;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelVector;
import cc.mallet.util.MalletLogger;
import cc.mallet.util.PrintUtilities;

import java.util.*;
import java.util.logging.*;
import java.text.DecimalFormat;

/**
 * Methods for calculating and displaying the accuracy v.
 * coverage data for a Trial
 */
public class AccuracyCoverage implements ActionListener
{
	private static Logger logger = MalletLogger.getLogger(AccuracyCoverage.class.getName());
	static final int DEFAULT_NUM_BUCKETS = 20;
	static final int DEFAULT_MAX_X = 100;
	private ArrayList classifications;
	private double [] accuracyValues;
	private int numBuckets;
	private double step;
	private Graph2 graph;
	private JFrame frame;
	
	/**
	 * Constructs object, sorts classifications, and creates
	 * accuracyValues array
     * @param t trial to get data from
     * @param numBuckets number of x-axis measurements to find accuracy
     */
	public AccuracyCoverage(Trial t, int numBuckets, String title, String dataName)
	{
		this.classifications = t;
		this.numBuckets = numBuckets;
		this.step = (double)DEFAULT_MAX_X/numBuckets;
		this.accuracyValues = new double[numBuckets];
		this.frame = null;
		logger.info("Constructing AccCov with " + 
											 this.classifications.size()); 
		sortClassifications();
/*		for(int i=0; i<classifications.size(); i++)
		{
			Classification c = (Classification)this.classifications.get(i);
			LabelVector distr = c.getLabelVector();
			System.out.println(distr.getBestValue());
		}
*/
		createAccuracyArray();
		this.graph = new Graph2(
			title, 0, 100,
			"Coverage", "Accuracy");
		addDataToGraph(this.accuracyValues, numBuckets, dataName);
	}

	public AccuracyCoverage(Trial t, String title, String name)
	{
		this(t, DEFAULT_NUM_BUCKETS, title, name);
	}
	public AccuracyCoverage(Trial t, String title)
	{
		this(t, DEFAULT_NUM_BUCKETS, title, "unnamed");
	}
	
	public AccuracyCoverage(Classifier C, InstanceList ilist, String title)
	{
		this(new Trial(C, ilist), DEFAULT_NUM_BUCKETS, title, "unnamed");
	}
	
	public AccuracyCoverage(Classifier C, InstanceList ilist, int numBuckets, String title)
	{
		this(new Trial(C, ilist), numBuckets, title, "unnamed");
	}
	
	/**
	 * Finds the "area under the acc/cov curve"
	 * steps by one percentage point and calcs area
	 * of trapezoid
	 */
	public double cumulativeAccuracy()
	{
		double area = 0.0;
		for(int i=1; i<100; i++)
		{
			double leftAccuracy = accuracyAtCoverage((double)i/100);
			double rightAccuracy = accuracyAtCoverage((double)(i+1)/100);
			area += .5*(leftAccuracy + rightAccuracy);
		}
		return area;		
	}
	
	/**
	 * Creates array of accuracy values for coverage
	 * at each step as defined by numBuckets.
	 
	 */
	public void createAccuracyArray()
	{
//		System.out.println("Creating accuracyArray. Step= "+step);
		for(int i=0 ; i<numBuckets; i++)
		{
			accuracyValues[i] =
				accuracyAtCoverage(step
													 *(double)(i+1)/100.0);
		}
	}
	
	/**
	 * accuracy at a given coverage percentage
	 * @param cov coverage percentage
	 * @return accuracy value
	 */
	public double accuracyAtCoverage(double cov)
	{
		assert(cov <= 1 && cov > 0);
		int numTrials = (int)(Math.round((double)classifications.size()*cov));
		int numCorrect = 0;
//		System.out.println("NumTrials="+numTrials);
		for(int i= classifications.size()-1; 
				i >= classifications.size()-numTrials; i--)
		{
			Classification temp = (Classification)classifications.get(i); 
			if(temp.bestLabelIsCorrect())
		    numCorrect++;
		}
//		System.out.println("Accuracy at cov "+cov+" is "+
		//(double)numCorrect/numTrials);
		return((double)numCorrect/numTrials);
	}
	
	/**
	 * Sort classifications ArrayList 
	 * by winner's value
	 */
	public void sortClassifications()
	{
		Collections.sort(classifications, new  ClassificationComparator());
	}
	
	
	public void addDataToGraph(double [] accValues, int nBuckets, String name)
	{
		Vector values = new Vector(nBuckets);
		for(int i=0; i<nBuckets; i++)
		{
			GraphItem temp = new GraphItem("",
																		 (int)(accValues[i]*100),
																		 Color.black);
			values.add(temp);
		}
		logger.info("Sending "+values.size()+" elements to graph");
		this.graph.addItemVector(values, name);
	}
	
/**
 * Displays the accuracy v. coverage graph
 */
	public void displayGraph()
	{
		Vector values = new Vector(this.numBuckets);
		JButton printButton = new JButton("Print");
	  frame = new JFrame("Graph");
		DecimalFormat df = new DecimalFormat();

		printButton.addActionListener(this);
		
		frame.addWindowListener
			(new WindowAdapter() 
				{
					public void windowClosing(WindowEvent e) 
					{
						System.exit(0);
					}
				}
				);

		// Get content pane
		Container pane = frame.getContentPane();
		
		// Set layout manager
		pane.setLayout( new FlowLayout() );

		assert(graph!= null); // make sure we've got data in the graph
		// Add to pane
		pane.add( graph );
		pane.add( printButton );
		frame.pack();
		
		// Center the frame
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		
		// Get the current screen size
		Dimension scrnsize = toolkit.getScreenSize();
		
		// Get the frame size
		Dimension framesize= frame.getSize();
		
		// Set X,Y location
		frame.setLocation ( (int) (scrnsize.getWidth()
															 - frame.getWidth() ) / 2 ,
												(int) (scrnsize.getHeight()
															 - frame.getHeight()) / 2);
		
		frame.setVisible(true);
	}
	
	
	public void actionPerformed(ActionEvent event)
	{
		PrintUtilities.printComponent(graph);
	}

	public void addTrial(Trial t, String name)
	{
		addTrial(t, DEFAULT_NUM_BUCKETS, name);
	}
	
	public void addTrial(Trial t, int nBuckets, String name)
	{
		AccuracyCoverage newData = new AccuracyCoverage(t, nBuckets, "untitled", name);
		double [] accValues = newData.accuracyValues();
		addDataToGraph(accValues, nBuckets, name);
	}

	public double[] accuracyValues()
	{
		return this.accuracyValues;
	}
	public class ClassificationComparator implements Comparator
	{
		public final int compare (Object a, Object b)
		{
			LabelVector x = (LabelVector) (((Classification)a).getLabelVector());
			LabelVector y = (LabelVector) (((Classification)b).getLabelVector());
			double difference = x.getBestValue() - y.getBestValue();
			int toReturn = 0;
			if(difference > 0)
				toReturn = 1;
			else if (difference < 0)
				toReturn = -1;
			return(toReturn);		
		}
		
	}
	
}

