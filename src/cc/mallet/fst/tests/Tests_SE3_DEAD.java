package cc.mallet.fst.tests;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Scanner;

import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;	

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import cc.mallet.types.Alphabet;
import cc.mallet.types.ArrayListSequence;
import cc.mallet.types.Multinomial;
import cc.mallet.fst.FeatureTransducer;
import cc.mallet.fst.MaxLatticeDefault;
import cc.mallet.fst.SimpleTagger;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.fst.Transducer;

public class Tests_SE3_DEAD {
	
	//	Set up reading console output
	
	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	
	@Before
	public void setUpStreams() {
	    System.setOut(new PrintStream(outContent));
	}
	
	@After
	public void cleanUpStreams() {
	    System.setOut(null);
	}
	

public void testCorrectTestingFileFormat() {
		// TEST 1: Testing with a simple file in the correct format (i.e. it is one word per line)	
		
		try {
			SimpleTagger.main(new String[] { "--model-file", "crf", "test.txt" });
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		CharSequence cs1 = "File is in correct format.";
		
		Scanner scanner = new Scanner(outContent.toString());
		
		while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
		boolean retval = line.contains(cs1);
		assertTrue(retval);
		}
		scanner.close();
}

	public void testIncorrectTestingFileFormat() {
		System.setOut(new PrintStream(outContent));
		// TEST 2: Testing with a simple file not in the correct format (i.e. it is not one word per line)	
		
		try {
			SimpleTagger.main(new String[] { "--model-file", "crf", "badtest.txt" });
		} catch (Exception e) {
			e.printStackTrace();
		}

	    assertEquals("Test file is not in correct format. It should be one word per line e.g. \n\nI \nneed \na \nbear \n.", outContent.toString());
	}	
}
