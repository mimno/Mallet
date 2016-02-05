package cc.mallet.topics;

import java.io.Serializable;
import cc.mallet.types.*;

/** This class combines a sequence of observed features
 *   with a sequence of hidden "labels".
 */

public class TopicAssignment implements Serializable {
	public Instance instance;
	public LabelSequence topicSequence;
	public Labeling topicDistribution;
                
	public TopicAssignment (Instance instance, LabelSequence topicSequence) {
		this.instance = instance;
		this.topicSequence = topicSequence;
	}
}
