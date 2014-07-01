package cc.mallet.fst.tests;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cc.mallet.fst.SimpleTagger;

public class SE3Tests {

	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	
	@Before
	public void setUp() throws Exception {
		System.setOut(new PrintStream(outContent));
	}

	@After
	public void tearDown() throws Exception {
		System.setOut(null);
	}
	
	// TEST 1: Testing with a simple file in the correct format (i.e. it is one word per line)
	
	@Test
	public void CheckCorrectFileType() {	
		
				try {
					SimpleTagger.main(new String[] { "--model-file", "crf", "test.txt" });

				} catch (Exception e) {
					e.printStackTrace();
				}
				
				Scanner scanner = new Scanner(outContent.toString());
				
				String phrase = "File is in correct format. Proceed.";
				
				while (scanner.hasNextLine()) {
		        String line = scanner.nextLine();
		        if(line.equals(phrase)){
		        	break;
		        }
				}
				scanner.close();
			    assertEquals("File is in correct format. Proceed.", phrase);

	}
	
	// TEST 2: Testing with a simple file in the incorrect format
	
	@Test
	public void CheckIncorrectFileType() {	
		
				try {
					SimpleTagger.main(new String[] { "--model-file", "crf", "badtest2.txt" });

				} catch (Exception e) {
					e.printStackTrace();
				}
				CharSequence phrase = "Test file is not in correct format. It should be one word per line e.g. \n\nI \nneed \na \nbear \n.";
				String s = outContent.toString();
				boolean retval = s.contains(phrase.toString());
				
			    assertTrue(retval);

	}
	
	// TEST 3: Testing with a larger file in the CORRECT format (one month of emails from the Enron email dataset)
	// from http://www.cs.cmu.edu/~./enron/
	
	@Test
	public void CheckCorrectFileType_STRESS() {	
		
		try {
			SimpleTagger.main(new String[] { "--model-file", "crf", "enrongood" });

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Scanner scanner = new Scanner(outContent.toString());
		
		String phrase = "File is in correct format. Proceed.";
		
		while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if(line.equals(phrase)){
        	break;
        }
		}
		scanner.close();
	    assertEquals("File is in correct format. Proceed.", phrase);
	}
	
	// TEST 3: Testing with a larger file in the INCORRECT format (one month of emails from the Enron email dataset)
	// from http://www.cs.cmu.edu/~./enron/
	
	@Test
	public void CheckIncorrectFileType_STRESS() {	
		
				try {
					SimpleTagger.main(new String[] { "--model-file", "crf", "enronbad" });

				} catch (Exception e) {
					e.printStackTrace();
				}
				CharSequence phrase = "Test file is not in correct format. It should be one word per line e.g. \n\nI \nneed \na \nbear \n.";
				String s = outContent.toString();
				boolean retval = s.contains(phrase.toString());
				
			    assertTrue(retval);
	}
	
	@Test
	public void out1() {
	    System.out.print("hello");
	    assertEquals("hello", outContent.toString());
	}
	@Test
	public void out2() {
	    System.out.print("hallo");
	    assertEquals("hello", outContent.toString());
	}
	
}
