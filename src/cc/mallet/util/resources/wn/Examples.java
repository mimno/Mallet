/**
 * Java WordNet Library (JWNL)
 * See the documentation for copyright information.
 *
 * @version 1.1
 */
package cc.mallet.util.resources.wn;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.PointerUtils;
import net.didion.jwnl.data.list.PointerTargetNodeList;
import net.didion.jwnl.data.list.PointerTargetTree;
import net.didion.jwnl.data.relationship.AsymmetricRelationship;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;
import net.didion.jwnl.dictionary.Dictionary;

import java.io.FileInputStream;
import java.util.Iterator;

/** A class to demonstrate the functionality of the JWNL package. */
public class Examples {
	private static final String USAGE = "java Examples <properties file>";

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println(USAGE);
			System.exit(-1);
		}

		String propsFile = args[0];
		try {
			// initialize JWNL (this must be done before JWNL can be used)
			JWNL.initialize(new FileInputStream(propsFile));
			new Examples().go();
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}

	private IndexWord ACCOMPLISH;
	private IndexWord DOG;
	private IndexWord CAT;
	private IndexWord FUNNY;
	private IndexWord DROLL;
	private String MORPH_PHRASE = "running-away";

	public Examples() throws JWNLException {
		ACCOMPLISH = Dictionary.getInstance().getIndexWord(POS.VERB, "accomplish");
		DOG = Dictionary.getInstance().getIndexWord(POS.NOUN, "dog");
		CAT = Dictionary.getInstance().lookupIndexWord(POS.NOUN, "cat");
		FUNNY = Dictionary.getInstance().lookupIndexWord(POS.ADJECTIVE, "funny");
		DROLL = Dictionary.getInstance().lookupIndexWord(POS.ADJECTIVE, "droll");
	}

	public void go() throws JWNLException {
		demonstrateMorphologicalAnalysis(MORPH_PHRASE);
		demonstrateListOperation(ACCOMPLISH);
		demonstrateTreeOperation(DOG);
		demonstrateAsymmetricRelationshipOperation(DOG, CAT);
		demonstrateSymmetricRelationshipOperation(FUNNY, DROLL);
	}

	private void demonstrateMorphologicalAnalysis(String phrase) throws JWNLException {
		// "running-away" is kind of a hard case because it involves
		// two words that are joined by a hyphen, and one of the words
		// is not stemmed. So we have to both remove the hyphen and stem
		// "running" before we get to an entry that is in WordNet
		System.out.println("Base form for \"" + phrase + "\": " +
		                   Dictionary.getInstance().lookupIndexWord(POS.VERB, phrase));
	}

	private void demonstrateListOperation(IndexWord word) throws JWNLException {
		// Get all of the hypernyms (parents) of the first sense of <var>word</var>
		PointerTargetNodeList hypernyms = PointerUtils.getInstance().getDirectHypernyms(word.getSense(1));
		System.out.println("Direct hypernyms of \"" + word.getLemma() + "\":");
		hypernyms.print();
	}

	private void demonstrateTreeOperation(IndexWord word) throws JWNLException {
		// Get all the hyponyms (children) of the first sense of <var>word</var>
		PointerTargetTree hyponyms = PointerUtils.getInstance().getHyponymTree(word.getSense(1));
		System.out.println("Hyponyms of \"" + word.getLemma() + "\":");
		hyponyms.print();
	}

	private void demonstrateAsymmetricRelationshipOperation(IndexWord start, IndexWord end) throws JWNLException {
		// Try to find a relationship between the first sense of <var>start</var> and the first sense of <var>end</var>
		RelationshipList list = RelationshipFinder.getInstance().findRelationships(start.getSense(1), end.getSense(1), PointerType.HYPERNYM);
		System.out.println("Hypernym relationship between \"" + start.getLemma() + "\" and \"" + end.getLemma() + "\":");
		for (Iterator itr = list.iterator(); itr.hasNext();) {
			((Relationship) itr.next()).getNodeList().print();
		}
		System.out.println("Common Parent Index: " + ((AsymmetricRelationship) list.get(0)).getCommonParentIndex());
		System.out.println("Depth: " + ((Relationship) list.get(0)).getDepth());
	}

	private void demonstrateSymmetricRelationshipOperation(IndexWord start, IndexWord end) throws JWNLException {
		// find all synonyms that <var>start</var> and <var>end</var> have in common
		RelationshipList list = RelationshipFinder.getInstance().findRelationships(start.getSense(1), end.getSense(1), PointerType.SIMILAR_TO);
		System.out.println("Synonym relationship between \"" + start.getLemma() + "\" and \"" + end.getLemma() + "\":");
		for (Iterator itr = list.iterator(); itr.hasNext();) {
			((Relationship) itr.next()).getNodeList().print();
		}
		System.out.println("Depth: " + ((Relationship) list.get(0)).getDepth());
	}
}