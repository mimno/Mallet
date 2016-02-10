/**
 * 
 */
package cc.mallet.pipe;

import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.TokenSequence;

/**
 * @author lmyao
 * Convert Feature sequence 
 */
public class FeatureSequenceConvolution extends Pipe {

	/**
	 * 
	 */
	public FeatureSequenceConvolution() {
		// TODO Auto-generated constructor stub
		super(new Alphabet(), null);
	}

	/**
	 * construct word co-occurrence features from the original sequence
	 * do combinatoric,  n choose 2, can be extended to n choose 3
	 
	public void convolution() {
		int fi = -1;
		int pre = -1;
		int i,j;
		int curLen = length;
		for(i = 0; i < curLen-1; i++) {
			for(j = i + 1; j < curLen; j++) {
				pre = features[i];
				fi = features[j];
				Object preO = dictionary.lookupObject(pre);
				Object curO = dictionary.lookupObject(fi);
				Object coO = preO.toString() + "_" + curO.toString();
				add(coO);
			}
		}
	}*/
	
	public Instance pipe (Instance carrier)
	{
		FeatureSequence fseq = (FeatureSequence) carrier.getData();
		FeatureSequence ret =
			new FeatureSequence ((Alphabet)getDataAlphabet());
		int i,j, curLen;
		curLen=fseq.getLength();
		//first add fseq to ret
		for(i = 0; i < curLen; i++) {
			ret.add(fseq.getObjectAtPosition(i));
		}
		//second word co-occurrence
		int pre, cur;
		Object coO;
		for(i = 0; i < curLen-1; i++) {
			for(j = i + 1; j < curLen; j++) {
				pre = fseq.getIndexAtPosition(i);
				cur = fseq.getIndexAtPosition(j);
				coO = pre + "_" + cur;
				ret.add(coO);
			}
		}
		if(carrier.isLocked()) {
			carrier.unLock();
		}
		carrier.setData(ret);
		return carrier;
	}

}
