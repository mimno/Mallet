package cc.mallet.classify;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestStaticParameters {
	int gamma = 1;

	public TestStaticParameters() {}

	public static class Factory {
		protected static int gamma = 2;

		public TestStaticParameters newTSP() {
			System.out.println("Factory gamma=" + this.gamma);
			TestStaticParameters t = new TestStaticParameters();
			t.gamma = this.gamma;
			return t;
		}
	}

	@Test
	public void testParameterSetting() {
		Factory f = new Factory() {{
			gamma = 3;
		}};
		TestStaticParameters g = f.newTSP();
		System.out.println("g.gamma=" + g.gamma);
		assertTrue("gamma=" + g.gamma, g.gamma == 3);
	}

}
