/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.pipe.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.net.URI;
import java.io.*;

import cc.mallet.fst.*;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.*;

/**
	 Iterates over {@link Segment}s extracted by a {@link Transducer}
	 for some {@link InstanceList}. 
 */
public class SegmentIterator implements Iterator<Instance>
{
	Iterator subIterator;
	ArrayList segments;
	
	/**
		 NOTE!: Assumes that <code>segmentStartTags[i]</code> corresponds
		 to <code>segmentContinueTags[i]</code>.
		 @param model model to segment input sequences
		 @param ilist list of instances to be segmented
		 @param segmentStartTags array of tags indicating the start of a segment
		 @param segmentContinueTags array of tags indicating the continuation of a segment
	 */
	public SegmentIterator (Transducer model, InstanceList ilist, Object[] segmentStartTags,
													Object[] segmentContinueTags) {
		setSubIterator (model, ilist, segmentStartTags, segmentContinueTags);
	}

	/**
		 Iterates over {@link Segment}s for only one {@link Instance}.
	 */
	public SegmentIterator (Transducer model, Instance instance, Object[] segmentStartTags,
													Object[] segmentContinueTags) {
		InstanceList ilist = new InstanceList (new Noop (instance.getDataAlphabet(), instance.getTargetAlphabet()));
		ilist.add (instance);
		setSubIterator (model, ilist, segmentStartTags, segmentContinueTags);
	}

	/**
		 Useful when no {@link Transduce} is specified. A list of
		 sequences specifies the output.
		 @param ilist InstanceList containing sequence.
		 @param segmentStartTags array of tags indicating the start of a segment
		 @param segmentContinueTags array of tags indicating the continuation of a segment
		 @param predictions list of {@link Sequence}s that are the
		 predicted output of some {@link Transducer}
	 */
	public SegmentIterator (InstanceList ilist, Object[] startTags, Object[] inTags,
													ArrayList predictions) {
		setSubIterator (ilist, startTags, inTags, predictions);
	}

	/**
		 Iterate over segments in one instance.
		 @param ilist InstanceList containing sequence.
		 @param segmentStartTags array of tags indicating the start of a segment
		 @param segmentContinueTags array of tags indicating the continuation of a segment
		 @param predictions list of {@link Sequence}s that are the
		 predicted output of some {@link Transducer}
	 */
	public SegmentIterator (Instance instance, Object[] startTags, Object[] inTags,	Sequence prediction) {
		InstanceList ilist = new InstanceList (new Noop (instance.getDataAlphabet(), instance.getTargetAlphabet()));
		ilist.add (instance);
		ArrayList predictions = new ArrayList();
		predictions.add (prediction);
		setSubIterator (ilist, startTags, inTags, predictions);
	}

	/**
		 Iterate over segments in one labeled sequence
	 */
	public SegmentIterator (Sequence input, Sequence predicted, Sequence truth,
													Object[] startTags, Object[] inTags) {
		segments = new ArrayList ();
		if (input.size() != truth.size () || predicted.size () != truth.size ())
			throw new IllegalStateException ("sequence lengths not equal. input: " + input.size ()
																			 + " true: " + truth.size ()
																			 + " predicted: " + predicted.size ());
		// find predicted segments
		for (int n=0; n < predicted.size (); n++) {
			for (int s=0; s < startTags.length; s++) {
				if (startTags[s].equals (predicted.get (n))) { // found start tag
					int j=n+1;
					while (j < predicted.size() &&  inTags[s].equals (predicted.get (j))) // find end tag
						j++;
					segments.add (new Segment (input, predicted, truth, n, j-1,
																		 startTags[s], inTags[s]));
				}
			}
		}		
		this.subIterator = segments.iterator();
	}
	
	private void setSubIterator (InstanceList ilist, Object[] startTags, Object[] inTags,
															 ArrayList predictions) {
		segments = new ArrayList (); // stores predicted <code>Segment</code>s
		Iterator iter = ilist.iterator ();
		for (int i=0; i < ilist.size(); i++) {
			Instance instance = (Instance) ilist.get (i);
			Sequence input = (Sequence) instance.getData ();
			Sequence trueOutput = (Sequence) instance.getTarget ();
			Sequence predOutput = (Sequence) predictions.get (i);
			if (input.size() != trueOutput.size () || predOutput.size () != trueOutput.size ())
				throw new IllegalStateException ("sequence lengths not equal. input: " + input.size ()
																				 + " true: " + trueOutput.size ()
																				 + " predicted: " + predOutput.size ());
			// find predicted segments
			for (int n=0; n < predOutput.size (); n++) {
				for (int s=0; s < startTags.length; s++) {
					if (startTags[s].equals (predOutput.get (n))) { // found start tag
						int j=n+1;
						while (j < predOutput.size() &&
									 inTags[s].equals (predOutput.get (j))) // find end tag
							j++;
						segments.add (new Segment (input, predOutput, trueOutput, n, j-1,
																			 startTags[s], inTags[s]));
					}
				}
			}		
		}
		this.subIterator = segments.iterator ();
	}
	
	private void setSubIterator (Transducer model, InstanceList ilist, Object[] segmentStartTags, Object[] segmentContinueTags) {
		segments = new ArrayList (); // stores predicted <code>Segment</code>s
		Iterator iter = ilist.iterator ();
		while (iter.hasNext ()) {
			Instance instance = (Instance) iter.next ();
			Sequence input = (Sequence) instance.getData ();
			Sequence trueOutput = (Sequence) instance.getTarget ();
			Sequence predOutput = new MaxLatticeDefault (model, input).bestOutputSequence();
			if (input.size() != trueOutput.size () || predOutput.size () != trueOutput.size ())
				throw new IllegalStateException ("sequence lengths not equal. input: " + input.size ()
																				 + " true: " + trueOutput.size ()
																				 + " predicted: " + predOutput.size ());
			// find predicted segments
			for (int i=0; i < predOutput.size (); i++) {
				for (int s=0; s < segmentStartTags.length; s++) {
					if (segmentStartTags[s].equals (predOutput.get (i))) { // found start tag
						int j=i+1;
						while (j < predOutput.size() &&
									 segmentContinueTags[s].equals (predOutput.get (j))) // find end tag
							j++;
						segments.add (new Segment (input, predOutput, trueOutput, i, j-1,
																			 segmentStartTags[s], segmentContinueTags[s]));
					}
				}
			}			
		}
		this.subIterator = segments.iterator ();
	}
  // The PipeInputIterator interface
	public Instance next ()
	{
		Segment nextSegment = (Segment) subIterator.next();
		return new Instance (nextSegment, nextSegment.getTruth (), null, null);
	}

	public Segment nextSegment () {
		return (Segment) subIterator.next ();
	}
	public boolean hasNext ()	{	return subIterator.hasNext();	}

	public ArrayList toArrayList () { return this.segments; }
	
	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}

