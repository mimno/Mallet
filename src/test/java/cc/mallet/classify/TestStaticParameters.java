package cc.mallet.classify;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestStaticParameters extends TestCase 
{
	int gamma = 1;
	public TestStaticParameters () { }
	
	public static class Factory {
		protected static int gamma = 2;
		public TestStaticParameters newTSP () {
			System.out.println ("Factory gamma="+this.gamma);
			TestStaticParameters t = new TestStaticParameters();
			t.gamma = this.gamma;
			return t;
		}
	}
	
	public void testParameterSetting () {
		Factory f = new Factory () {{gamma=3;}}; 
		TestStaticParameters g = f.newTSP();
		System.out.println ("g.gamma="+g.gamma);
		assertTrue("gamma="+g.gamma, g.gamma == 3);
	}

  public static Test suite ()
	{
		return new TestSuite (TestClassifiers.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}

}
