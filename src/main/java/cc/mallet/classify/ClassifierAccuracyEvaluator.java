package cc.mallet.classify;

import cc.mallet.types.InstanceList;

public class ClassifierAccuracyEvaluator extends ClassifierEvaluator 
{
	
	public ClassifierAccuracyEvaluator (InstanceList[] instances, String[] descriptions) 
	{
		super(instances,descriptions);
	}
	
	public ClassifierAccuracyEvaluator (InstanceList instanceList1, String instanceListDescription1) {
		this(new InstanceList[] {instanceList1}, new String[] {instanceListDescription1});
	}
	
	public ClassifierAccuracyEvaluator (InstanceList instanceList1, String instanceListDescription1,
			InstanceList instanceList2, String instanceListDescription2) {
		this(new InstanceList[] {instanceList1, instanceList2}, new String[] {instanceListDescription1, instanceListDescription2});
	}

	public ClassifierAccuracyEvaluator (InstanceList instanceList1, String instanceListDescription1,
			InstanceList instanceList2, String instanceListDescription2,
			InstanceList instanceList3, String instanceListDescription3) {
		this(new InstanceList[] {instanceList1, instanceList2, instanceList3}, 
				new String[] {instanceListDescription1, instanceListDescription2, instanceListDescription3});
	}


	@Override public void evaluateInstanceList (ClassifierTrainer trainer,	InstanceList instances, String description) 
	{
		Classifier classifier = trainer.getClassifier();
		if (classifier.getFeatureSelection() != instances.getFeatureSelection())
			// TODO consider if we really want to do this... but note that the old MaxEnt did this to the testing the validation sets.
			//instances.setFeatureSelection(classifier.getFeatureSelection());
    System.out.print (description+" accuracy=" + classifier.getAccuracy (instances));
	}

}
