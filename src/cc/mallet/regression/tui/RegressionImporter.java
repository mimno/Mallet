package cc.mallet.regression.tui;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

/** Load data suitable for linear and Poisson regression */

public class RegressionImporter {

	static CommandOption.File inputFile =   new CommandOption.File
		(RegressionImporter.class, "input", "FILE", true, null,
		 "The file containing data to be classified, one instance per line", null);
	
    static CommandOption.File outputFile = new CommandOption.File
        (RegressionImporter.class, "output", "FILE", true, new File("text.vectors"),
         "Write the instance list to this file; Using - indicates stdout.", null);

    static CommandOption.String lineRegex = new CommandOption.String
        (RegressionImporter.class, "line-regex", "REGEX", true, "^\\s*(\\S*)[\\s,]*(.*)$",
         "Regular expression containing regex-groups for response, variables and name fields.\n" + 
		 "Default is response followed by explanatory variables, with no instance name.", null);

    static CommandOption.Integer labelOption = new CommandOption.Integer
        (RegressionImporter.class, "response", "INTEGER", true, 1,
         "The index of the group containing the response variables.\n" +
         "   Use 0 to indicate that the label field is not used.", null);

    static CommandOption.Integer nameOption = new CommandOption.Integer
        (RegressionImporter.class, "name", "INTEGER", true, 0,
         "The index of the group containing the instance name.\n" +
         "   Use 0 to indicate that the name field is not used.", null);

    static CommandOption.Integer dataOption = new CommandOption.Integer
        (RegressionImporter.class, "data", "INTEGER", true, 2,
         "The index of the group containing the explanatory variables.", null);

	static CommandOption.Boolean integerResponse = new CommandOption.Boolean
        (RegressionImporter.class, "integer-response", "[TRUE|FALSE]", false, false,
         "If true, interpret the response variable as an integer rather\n" +
         "   than a double precision real number. Use for Poisson regression.", null);

	static CommandOption.Boolean useFeatureValuePairs = new CommandOption.Boolean
		(RegressionImporter.class, "use-feature-value-pairs", "[TRUE|FALSE]", false, false,
         "If true, process the data field as a series of \"feature=value\" pairs rather\n" +
         "   than an ordered sequence of variables. Useful when most variables are 0.", null);

	static CommandOption.SpacedStrings fieldNames = new CommandOption.SpacedStrings
		(RegressionImporter.class, "field-names", "[A B C ...]", false, null,
		 "Use this option to specify names for the explanatory variables\n" +
		 "   when you are not using feature name/value pairs", null);

	public static void main (String[] args) throws IOException {
		
		// Process the command-line options                                                                                  
		CommandOption.setSummary (RegressionImporter.class,
                                  "A tool for importing data suitable for linear and Poisson regression");
        CommandOption.process (RegressionImporter.class, args);


		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

		if (useFeatureValuePairs.value) {
			// Break data string into tokens
			pipeList.add(new FeatureValueString2FeatureVector());
		}
		else if (fieldNames.value != null) {
			// Break the data into an array on whitespace and then convert 
			//  that to a feature vector, using user-specified names.
			pipeList.add(new ValueString2FeatureVector(fieldNames.value));
		}
		else {
			// Break the data into an array on whitespace and then convert 
			//  that to a feature vector.
			pipeList.add(new ValueString2FeatureVector());
		}

		// Convert the target to a numeric value, 
		//  which should be an integer for Poisson regression.
		if (integerResponse.value) {
			pipeList.add(new Target2Integer());
		} 
		else {
			pipeList.add(new Target2Double());
		}

		// Create an iterator that will create an instance from
		//  each line in a file by breaking the line into
		//   [NAME]  [LABEL]  [feature=value feature=value ...]
		CsvIterator reader =
			new CsvIterator(new FileReader(inputFile.value),
							lineRegex.value,
							dataOption.value, labelOption.value, nameOption.value);
		
		// Construct a new instance list, passing it the pipe
		//  we want to use to process instances.
		InstanceList instances = new InstanceList( new SerialPipes(pipeList) );

		// Now process each instance provided by the iterator.
		instances.addThruPipe(reader);

		System.out.println(instances.getDataAlphabet());

		instances.save(outputFile.value);

	}

}
