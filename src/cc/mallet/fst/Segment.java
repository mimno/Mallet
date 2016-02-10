/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
		@author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.fst;


import java.util.ArrayList;

import cc.mallet.types.ArraySequence;
import cc.mallet.types.Sequence;

/**
 * Represents a labelled chunk of a {@link Sequence} segmented by a
 * {@link Transducer}, usually corresponding to some object extracted
 * from an input {@link Sequence}.
 */
public class Segment implements Comparable
{
	Sequence input, pred, truth; // input, predicted, and true sequences
	int start, end;              // offsets for this segment in the sequence
	Object startTag, inTag; // label for the beginning and inside of this Segment
 	double confidence;           // confidence score for this extracted segment
	boolean correct;
	// this is a tough case b/c technically everything inside the
	// segment is tagged correctly
	boolean endsPrematurely; // e.g. truth: B I I O O
	                         //      pred:  B I O O O
	
	/**
	 * Initializes the segment.
	 * 
	 * @param input entire input sequence
	 * @param pred predicted sequence
	 * @param start starting position of extracted segment
	 * @param end ending position of extracted segment
	 */
	public Segment (Sequence input, Sequence pred, Sequence truth, int start, int end,
									Object startTag, Object inTag )
	{
		this.input = input;
		this.pred = pred;
		this.truth = truth;
 		this.start = start;
		this.startTag = startTag;
		this.inTag = inTag;
		this.end = end;
		this.confidence = -1;
		this.correct = true;
		this.endsPrematurely = false;
		for (int i=start; i <= end; i++) {
			if (!pred.get(i).equals (truth.get(i))) {
				this.correct = false;
				break;
			}			
		}
		// segment can also be incorrect if it ends prematurely
		if (truth != null) {
		  if (correct && end+1 < truth.size() && truth.get (end+1).equals (inTag)) {
		    this.correct = false;
		    this.endsPrematurely = true;
		  }				
		}			
	}
  
	public void setCorrect (boolean b) { this.correct = b; }
	public int size() { return this.end - this.start + 1; }
	public Object getTruth (int i) { return this.truth.get( i ); }
	public Sequence getTruth () { return this.truth; }
	public Object getPredicted (int i) { return this.pred.get( i ); }
	public Sequence getPredicted () { return this.pred; }
	public void setPredicted (Sequence predicted) { this.pred = predicted; }
	public Sequence getInput () { return this.input; }
	public int getStart () { return this.start; }
	public int getEnd () { return this.end; }
	public Object getStartTag () { return this.startTag; }
	public Object getInTag () { return this.inTag; }
	public double getConfidence () {return this.confidence; }
	public void setConfidence (double c) {this.confidence = c; } 
	public boolean correct () { return this.correct; }
	public boolean endsPrematurely () { return this.endsPrematurely; }
	public boolean indexInSegment (int index) {
		return (index >= this.start && index <= this.end);
	}

	public Sequence getSegmentInputSequence () {
		ArrayList ret = new ArrayList ();
		for (int i=start; i <= end; i++)
			ret.add( input.get( i ) );
		return new ArraySequence( ret );
	}
	
	public int compareTo (Object o) {
		Segment s = (Segment) o;
		if (s.confidence == -1 || this.confidence == -1) {
			throw new IllegalArgumentException ("attempting to compare confidences that have not been set yet..");
		}
		if (this.confidence > s.confidence)
			return 1;
		else if (this.confidence < s.confidence)
			return -1;
		else return 0;
	}

	public String sequenceToString () {
		String ret = "";
		for (int i=0; i < input.size(); i++) {
			if (i <= end && i >= start) // part of segment
				ret += pred.get(i).toString() + "[" + truth.get (i) + "][" + confidence + "]\t";
			else
				ret += "-[" + truth.get (i) + "]\t";
 		}
		return ret;
	}

	public String toString () {
		String ret = "";
		ret += "start: " + start + " end: " + end + " confidence: " + confidence + "\n";
		for (int i=start; i <= end; i++) {
				ret += pred.get (i).toString() + "[" + truth.get (i) + "]\t";
 		}
		return ret;
	}

	public boolean equals (Object o) {
		Segment s = (Segment) o;
		if (start == s.getStart() &&
				end == s.getEnd() &&
				correct == s.correct() &&
				input.size() == s.getInput().size()) {
			for (int i=start; i <= end; i++) {
				if (!pred.get( i ).equals( s.getPredicted( i ) ) ||
						!truth.get( i ).equals( s.getTruth( i ) )	)
					return false;
			}
			return true;
		}
    return false;
	}
}
