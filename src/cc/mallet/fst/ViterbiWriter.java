package cc.mallet.fst;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cc.mallet.types.TokenSequence;

/**
 * Prints the input instances along with the features and the true and 
 * predicted labels to a file.
 * <p>
 * To control the number of times output has to be printed, override the
 * {@link cc.mallet.fst.TransducerTrainer.precondition} method.
 * <p>
 * The name of the output file is <tt>filename_prefix + description + iteration_number + '.viterbi'</tt>.
 */
public class ViterbiWriter extends TransducerEvaluator {
	
	String filenamePrefix;
	String outputEncoding = "UTF-8";
	
	public ViterbiWriter (String filenamePrefix, InstanceList[] instanceLists, String[] descriptions) {
		super (instanceLists, descriptions);
		this.filenamePrefix = filenamePrefix;
	}
	
	public ViterbiWriter (String filenamePrefix, InstanceList instanceList1, String description1) {
		this (filenamePrefix, new InstanceList[] {instanceList1}, new String[] {description1});
	}

	public ViterbiWriter (String filenamePrefix, 
			InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2) {
		this (filenamePrefix, new InstanceList[] {instanceList1, instanceList2}, new String[] {description1, description2});
	}

	public ViterbiWriter (String filenamePrefix, 
			InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2,
			InstanceList instanceList3, String description3) {
		this (filenamePrefix, new InstanceList[] {instanceList1, instanceList2, instanceList3}, 
				new String[] {description1, description2, description3});
	}

	protected void preamble (TransducerTrainer tt) {
		// We don't want to print iteration number and cost, so here we override this behavior in the superclass.
	}

	@SuppressWarnings("unchecked")
  @Override
	public void evaluateInstanceList(TransducerTrainer transducerTrainer,	InstanceList instances, String description) {
		int iteration = transducerTrainer.getIteration();
    String viterbiFilename = filenamePrefix + description + iteration + ".viterbi";
    PrintStream viterbiOutputStream;
    try {
      FileOutputStream fos = new FileOutputStream (viterbiFilename);
      if (outputEncoding == null)
        viterbiOutputStream = new PrintStream (fos);
      else
        viterbiOutputStream = new PrintStream (fos, true, outputEncoding);
      //((CRF)model).write (new File(viterbiOutputFilePrefix + "."+description + iteration+".model"));
    } catch (IOException e) {
      System.err.println ("Couldn't open Viterbi output file '"+viterbiFilename+"'; continuing without Viterbi output trace.");
			return;
    }
    
    for (int i = 0; i < instances.size(); i++) {
      if (viterbiOutputStream != null)
        viterbiOutputStream.println ("Viterbi path for "+description+" instance #"+i);
      Instance instance = instances.get(i);
      Sequence input = (Sequence) instance.getData();
      TokenSequence sourceTokenSequence = null;
      if (instance.getSource() instanceof TokenSequence)
      	sourceTokenSequence = (TokenSequence) instance.getSource();

      Sequence trueOutput = (Sequence) instance.getTarget();
      assert (input.size() == trueOutput.size());
      Sequence predOutput = transducerTrainer.getTransducer().transduce (input);
      assert (predOutput.size() == trueOutput.size());
      
      for (int j = 0; j < trueOutput.size(); j++) {
      	FeatureVector fv = (FeatureVector) input.get(j);
      	//viterbiOutputStream.println (tokens.charAt(j)+" "+trueOutput.get(j).toString()+
      	//'/'+predOutput.get(j).toString()+"  "+ fv.toString(true));
      	if (sourceTokenSequence != null)
      		viterbiOutputStream.print (sourceTokenSequence.get(j).getText()+": ");
      	viterbiOutputStream.println (trueOutput.get(j).toString()+
      			'/'+predOutput.get(j).toString()+"  "+ fv.toString(true));
      }
    }
	}

}
