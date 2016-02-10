package cc.mallet.types;

public interface Labeler {
	/** Given the (presumably unlabeled) instanceToLabel, set its target field to the true label.
	 * @return true if labeling occurred successfully, false if for some reason the instance could not be labeled. */
	public boolean label (Instance instanceToLabel);
}
