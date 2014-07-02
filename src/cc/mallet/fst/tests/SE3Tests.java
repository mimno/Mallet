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

	//	Set up taking input from the console
	
	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	
	@Before
	public void setUp() throws Exception {
		System.setOut(new PrintStream(outContent));
	}

	@After
	public void tearDown() throws Exception {
		System.setOut(null);
	}
	
	//	TEST 1: Testing with a simple file in the CORRECT format (i.e. it is one word per line)
	//	Should proceed to use trained CRF in tagging
	
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
	
	// TEST 2: Testing with a simple file in the INCORRECT format
	//	Should produce error message and exit
	
	@Test
	public void CheckIncorrectFileType() {	
		
				try {
					SimpleTagger.main(new String[] { "--model-file", "crf", "badtest.txt" });

				} catch (Exception e) {
					e.printStackTrace();
				}
				CharSequence phrase = "Test file is not in correct format. It should be one word per line e.g. \n\nI \nneed \na \nbear \n.";
				String s = outContent.toString();
				boolean retval = s.contains(phrase.toString());
				
			    assertTrue(retval);

	}
	
	//	TEST 3: Testing with a larger file in the CORRECT format (one month of emails from the Enron email dataset)
	//	from http://www.cs.cmu.edu/~./enron/
	//	Should proceed to use trained CRF in tagging
	
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
	
	//	TEST 4: Testing with a larger file in the INCORRECT format (one month of emails from the Enron email dataset)
	//	from http://www.cs.cmu.edu/~./enron/
	//	Should produce error message and exit
	
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
	
	//	TEST 5: Testing with a simple file in the CORRECT format to make sure correct output is occurring 
	//	Should proceed to use trained CRF in tagging
	
	@Test
	public void CheckCorrectOutputFromCorrectFileFormat() {	
		
				try {
					SimpleTagger.main(new String[] { "--model-file", "crf", "test.txt" });

				} catch (Exception e) {
					e.printStackTrace();
				}
				
				Scanner scanner = new Scanner(outContent.toString());
				
				//	Correct output of tagging should look like this:
				
				String phrase = "O \nO \nY \nO\n";
				
				while (scanner.hasNextLine()) {
		        String line = scanner.nextLine();
		        System.out.println(line);
		        if(line.equals(phrase)){
		        	break;
		        }
				}
				scanner.close();
			    assertEquals("O \nO \nY \nO\n", phrase);
	}
	
	//	Tests merely to check JUNIT is working properly with the ByteArrayOutputStream
	//	Should PASS
	
	@Test
	public void out1() {
	    System.out.print("hello");
	    assertEquals("hello", outContent.toString());
	}
	
	//	Should FAIL
	
	@Test
	public void out2() {
	    System.out.print("hallo");
	    assertEquals("hello", outContent.toString());
	}
	
}
