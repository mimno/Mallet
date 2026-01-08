package cc.mallet.topics.tui;

import java.io.File;
import cc.mallet.topics.tree.OntologyWriter;
import cc.mallet.util.CommandOption;

public class GenerateTree {
	
	static CommandOption.String vocabFile = new CommandOption.String
	(GenerateTree.class, "vocab", "FILENAME", true, null,
	 "The vocabulary file.", null);
	
	static CommandOption.String treeFiles = new CommandOption.String
	(GenerateTree.class, "tree", "FILENAME", true, null,
	 "The files for tree structure.", null);
	
	static CommandOption.String consFile = new CommandOption.String
	(GenerateTree.class, "constraint", "FILENAME", true, null,
	"The constraint file.", null);
	
	static CommandOption.Boolean mergeCons = new CommandOption.Boolean
	(GenerateTree.class, "merge-constraints", "true|false", false, true,
	"Merge constraints or not. For example, if you want to merge A and B, " +
	"and merge B and C and set merge-constraints as true, the new constraint" +
	"will be merge A, B and C.", null);
	
	public static void main (String[] args) throws java.io.IOException {
		// Process the command-line options
		CommandOption.setSummary (GenerateTree.class,
								  "Generate a prior tree structure for LDA, in proto buffer format.");
		CommandOption.process (GenerateTree.class, args);
			
		try {
			OntologyWriter.createOntology(consFile.value, vocabFile.value, 
	    		                          treeFiles.value, mergeCons.value);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
