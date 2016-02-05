package cc.mallet.types.tests;

import org.junit.Before; 
import org.junit.Ignore; 
import org.junit.Test; 
import static org.junit.Assert.*;

import cc.mallet.types.*;
import cc.mallet.pipe.*;

public class TestInstanceListWeights {
	
	@Test
		public void setWeights() {
		
		InstanceList instances = new InstanceList(new Noop());
		Instance instance1 = new Instance("test", null, null, null);
		Instance instance2 = new Instance("test", null, null, null);
		Instance instance3 = new Instance("test", null, null, null);
		
		instances.add(instance1, 10.0);
		instances.add(instance2);

		assertEquals("#1", instances.getInstanceWeight(0), 10.0, 0.0);
		assertEquals("#2", instances.getInstanceWeight(instance1), 10.0, 0.0);
		assertEquals("#3", instances.getInstanceWeight(1), 1.0, 0.0);
		assertEquals("#4", instances.getInstanceWeight(instance2), 1.0, 0.0);

		// Reset an existing weight
		instances.setInstanceWeight(0, 1.0);
		assertEquals("#5", instances.getInstanceWeight(0), 1.0, 0.0);

		// Reset an existing default (and therefore not represented) weight
		instances.setInstanceWeight(1, 5.0);
		assertEquals("#6", instances.getInstanceWeight(1), 5.0, 0.0);
	}

}