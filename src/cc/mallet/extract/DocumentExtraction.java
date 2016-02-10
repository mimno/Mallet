/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package cc.mallet.extract;

import org.jdom.Element;
import org.jdom.Document;
import org.jdom.Namespace;
import org.jdom.Text;
import org.jdom.output.XMLOutputter;

import cc.mallet.types.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import gnu.trove.THashMap;

/**
 * Created: Oct 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: DocumentExtraction.java,v 1.1 2007/10/22 21:37:44 mccallum Exp $
 */
//TODO: Add place where user can have general Transducers to change CRF tokenization into LabeledSpans
//TODO: Add field for CRF's labeled tokenization
public class DocumentExtraction implements Serializable {

  private Tokenization input;
  private Sequence predictedLabels;
  private LabelSequence target;

  private LabeledSpans extractedSpans;
  private LabeledSpans targetSpans;

  private Object document;
  private Label backgroundTag;
  private String name;


  public DocumentExtraction (String name, LabelAlphabet dict, Tokenization input, Sequence predicted, String background)
  {
    this (name, dict, input, predicted, null, background, new BIOTokenizationFilter ());
  }

  public DocumentExtraction (String name, LabelAlphabet dict, Tokenization input, Sequence predicted,
                             Sequence target, String background)
  {
    this (name, dict, input, predicted, target, background, new BIOTokenizationFilter ());
  }

  public DocumentExtraction (String name, LabelAlphabet dict, Tokenization input,
                             Sequence predicted, Sequence target, String background,
                             TokenizationFilter filter)
  {

    this.document = input.getDocument ();
    this.name = name;
    assert (input.size() == predicted.size());

    this.backgroundTag = dict.lookupLabel (background);
    this.input = input;

    this.predictedLabels = predicted;
    this.extractedSpans = filter.constructLabeledSpans (dict, document, backgroundTag, input, predicted);

    if (target != null) {
      if (target instanceof LabelSequence) this.target = (LabelSequence) target;
      this.targetSpans = filter.constructLabeledSpans (dict, document, backgroundTag, input, target);
    }

  }

  public DocumentExtraction (String name, LabelAlphabet dict, Tokenization input,
                             LabeledSpans predictedSpans, LabeledSpans trueSpans, String background)
  {
    this.document = input.getDocument ();
    this.name = name;

    this.backgroundTag = dict.lookupLabel (background);
    this.input = input;

    this.extractedSpans = predictedSpans;
    this.targetSpans = trueSpans;
  }



  public Object getDocument ()
  {
    return document;
  }

  public Tokenization getInput ()
  {
    return input;
  }


  public Sequence getPredictedLabels ()
  {
    return predictedLabels;
  }


  public LabeledSpans getExtractedSpans ()
  {
    return extractedSpans;
  }

  public LabeledSpans getTargetSpans ()
  {
    return targetSpans;
  }

  public LabelSequence getTarget ()
  {
    return target;
  }


  public String getName ()
  {
    return name;
  }

  public Label getBackgroundTag ()
  {
    return backgroundTag;
  }

  //xxx nyi
  public Span subspan (int start, int end)
  {
    throw new UnsupportedOperationException ("not yet implemented.");
  }


  public Document toXmlDocument ()
  {
    return toXmlDocument ("doc", Namespace.NO_NAMESPACE);
  }

 /*
  public Document toXmlDocument (String rootEltName, Namespace ns)
  {
    Element element = new Element (rootEltName, ns);
    for (int i = 0; i < extractedSpans.size(); i++) {
       LabeledSpan span = (LabeledSpan) extractedSpans.get(i);
       Label tag = span.getLabel();
       if (tag == backgroundTag) {
         org.jdom.Parent p = element.addContent (span.getText ());
       } else {
         Element field = new Element (tag.toString(), ns);
         field.setText (span.getText ());
         element.addContent (field);
       }
     }
    return new Document (element);
  }
   */

  // does not do non-overlap sanity checking
  public Document toXmlDocument (String rootEltName, Namespace ns)
   {
     ArrayList orderedByStart = new ArrayList (extractedSpans);
     Collections.sort (orderedByStart, new Comparator () {
       public int compare (Object o, Object o1)
       {
         int start1 = ((Span)o).getStartIdx ();
         int start2 = ((Span)o1).getStartIdx ();
         return Double.compare (start1, start2);
       }
     } );

     ArrayList roots = new ArrayList (orderedByStart);
     THashMap children = new THashMap ();
     for (int i = 0; i < orderedByStart.size(); i++) {
       LabeledSpan child = (LabeledSpan) orderedByStart.get (i);
       for (int j = i-1; j >= 0; j--) {
         LabeledSpan parent = (LabeledSpan) orderedByStart.get (j);
         if (parent.isSubspan (child)) {
           List childList = (List) children.get (parent);
           if (childList == null) {
             childList = new ArrayList ();
             children.put (parent, childList);
           }
           roots.remove (child);
           childList.add (child);
           break;
         }
       }
     }

     CharSequence doc = (CharSequence) document;
     Span wholeDoc = new StringSpan (doc, 0, doc.length ());
     return new Document (generateElement (rootEltName, wholeDoc, roots, children));
   }


  private Element generateElement (String parentName, Span span, List childSpans, THashMap tree)
  {
    Element parentElt = new Element (parentName);
    if (childSpans == null || childSpans.isEmpty ()) {
      parentElt.setContent (new Text (span.getText ()));
    } else {
      List childElts = new ArrayList (childSpans.size());
      int start = span.getStartIdx ();
      int current = 0;
      for (int i = 0; i < childSpans.size(); i++) {
        LabeledSpan childSpan = (LabeledSpan) childSpans.get (i);
        Label childLabel = childSpan.getLabel();

        int childStart = childSpan.getStartIdx () - start;
        if (childStart > current) {
          childElts.add (new Text (span.getText().substring (current, childStart)));
        }

        if (childLabel == backgroundTag) {
          childElts.add (new Text (childSpan.getText()));
        } else {
          String name = childLabel.getEntry ().toString();
          List grandchildren = (List) tree.get (childSpan);
          childElts.add (generateElement (name, childSpan, grandchildren, tree));
        }

        current = childSpan.getEndIdx () - start;
      }

      if (current < span.getEndIdx ())
        childElts.add (new Text (span.getText().substring (current)));

      parentElt.addContent (childElts);
    }

    return parentElt;
  }


  public String toXmlString ()
  {
    Document jdom = toXmlDocument ();
    XMLOutputter outputter = new XMLOutputter ();
    return outputter.outputString (jdom);
  }

  public int size ()
  {
    return extractedSpans.size();
  }
  
	// Serialization garbage

	private static final long serialVersionUID = 1L;

	private static final int CURRENT_SERIAL_VERSION = 1;

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		out.writeInt(CURRENT_SERIAL_VERSION);
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		in.readInt(); // read version
	}

}
