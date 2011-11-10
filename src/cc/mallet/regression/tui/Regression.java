package cc.mallet.regression.tui;

import cc.mallet.regression.*;
import cc.mallet.types.*;
import cc.mallet.util.*;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class Regression {
	
	protected static Logger logger = MalletLogger.getLogger(Regression.class.getName());

	static cc.mallet.util.CommandOption.String inputFile = new cc.mallet.util.CommandOption.String
		(Regression.class, "input", "FILENAME", true, null,
		 "Filename to read from", null);

	static cc.mallet.util.CommandOption.String outputFile = new cc.mallet.util.CommandOption.String
		(Regression.class, "output", "FILENAME", true, null,
		 "Filename to write to", null);

	static cc.mallet.util.CommandOption.Double regularizationOption = new cc.mallet.util.CommandOption.Double
		(Regression.class, "ridge-penalty", "FILENAME", true, 0.0,
		 "Precision (inverse variance) of the Gaussian prior shrinking parameters towards zero", null);

	InstanceList data;
	double regularization;

	double[] coefficients;
	LinearRegression regression;

	public Regression (InstanceList data, double regularization) {
		this.data = data;
		this.regularization = regularization;
		LeastSquares model = new LeastSquares(data, regularization);
		coefficients = new double[ model.getNumParameters() ];
		model.getParameters(coefficients);
		regression = model.getRegression();
	}
	
	public void printParameters(String filename) throws IOException {
		Alphabet alphabet = data.getDataAlphabet();
		PrintWriter out = new PrintWriter(filename);
		
		for (int feature = 0; feature < alphabet.size(); feature++) {
			out.printf("%s\t%.8f\n", alphabet.lookupObject(feature), coefficients[feature]);
		}

		out.close();
	}

	public static void main(String[] args) throws Exception {
		CommandOption.setSummary (Regression.class, "Run a regression, print the learned parameters");
		CommandOption.process (Regression.class, args);

		InstanceList data = InstanceList.load(new File(inputFile.value));
		double regularization = regularizationOption.value;

		Regression regression = new Regression(data, regularization);
		regression.printParameters(outputFile.value);
		
	}

}