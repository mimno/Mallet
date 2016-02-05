/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.extract;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.PrintWriter;

import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.Sequence;

/**
 * The results of doing information extraction.  This is designed to handle
 *  field extraction from a single document, or relation extraction and
 *  coreference from multiple documents;
 */
public class Extraction
{
	private Extractor extractor;

  private List byDocs = new ArrayList (); // List of DocumentExtractions
  private List records = new ArrayList ();

  // If the DocumentExtractions contain true targets (i.e., they're labeled testing instances,
  //  then these are the true records obtained from those
  List trueRecords = new ArrayList ();
  private LabelAlphabet dict;


  /**
   * Creates an empty Extraction option.  DocumentExtractions can be added later by
   *  the addDocumentExtraction method.
   */
  public Extraction (Extractor extractor, LabelAlphabet dict)
  {
    this.extractor = extractor;
    this.dict = dict;
  }


  /**
   * Creates an extration given a sequence output by some kind of per-sequece labeler, like an
   *  HMM or a CRF.  The extraction will contain a single document.
   */
  public Extraction (Extractor extractor, LabelAlphabet dict, String name, Tokenization input, Sequence output, String background)
  {
    this.extractor = extractor;
    this.dict = dict;
    DocumentExtraction docseq = new DocumentExtraction (name, dict, input, output, background);
    addDocumentExtraction (docseq);
  }


  public void addDocumentExtraction (DocumentExtraction docseq)
  {
    byDocs.add (docseq);
    records.add (new Record (docseq.getName (), docseq.getExtractedSpans ()));
    if (docseq.getTargetSpans () != null) {
      trueRecords.add (new Record ("TRUE:"+docseq.getName (), docseq.getTargetSpans ()));
    }
  }

  public Record getRecord (int idx) { return (Record) records.get (idx); }
  public int getNumRecords () { return records.size(); }

  public DocumentExtraction getDocumentExtraction(int idx) { return (DocumentExtraction) byDocs.get (idx); }
  public int getNumDocuments () { return byDocs.size(); }

	public Extractor getExtractor ()
	{
		return extractor;
	}

  public Record getTargetRecord (int docnum)
  {
    return (Record) trueRecords.get (docnum);
  }

  public LabelAlphabet getLabelAlphabet () { return dict; }

  public void cleanFields (FieldCleaner cleaner)
  {
    Iterator it = records.iterator ();
    while (it.hasNext ()) {
      cleanRecord ((Record) it.next (), cleaner);
    }

    it = trueRecords.iterator ();
    while (it.hasNext ()) {
      cleanRecord ((Record) it.next (), cleaner);
    }
  }

  private void cleanRecord (Record record, FieldCleaner cleaner)
  {
    Iterator it = record.fieldsIterator ();
    while (it.hasNext ()) {
      Field field = (Field) it.next ();
      field.cleanField (cleaner);
    }
  }

  public void print (PrintWriter writer)
  {
    Iterator it = records.iterator ();
    writer.println ("***EXTRACTION***");
    while (it.hasNext ()) {
      Record record = (Record) it.next ();

      writer.println ("**RECORD "+record.getName ());
      Iterator fit = record.fieldsIterator ();
      while (fit.hasNext ()) {
        Field field = (Field) fit.next ();
        writer.println (field.getName ());
        for (int fidx = 0; fidx < field.numValues (); fidx++) {
          String val = field.value (fidx).replaceAll ("\n", " ");
          writer.print ("      ==> "+val+"\n");
        }
        writer.println ();
      }
    }
    writer.println ("***END EXTRACTION***");
  }
}
