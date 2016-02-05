package cc.mallet.util;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;

public class DBInstanceStore {

	// Supported types

	public static final int EMPTY = 0;
	public static final int STRING = 1;
	public static final int FEATURE_VECTOR = 2;
	public static final int FEATURE_SEQUENCE = 3;
	public static final int FEATURE_VECTOR_SEQUENCE = 4;
	public static final int LABEL = 5;

	boolean debug = false;

	Connection connection = null;

	public DBInstanceStore (String dbName) throws Exception {
		
		Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
		
		String connectionURL = "jdbc:derby:" + dbName + ";create=true";
		connection = DriverManager.getConnection(connectionURL);
		
		ResultSet tableCheck = connection.getMetaData().getTables(null, null, "INSTANCES", null);
		if (! tableCheck.next()) {
			Statement createTableStatement = connection.createStatement();
			createTableStatement.execute("CREATE TABLE instances (instance_id INT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 0, INCREMENT BY 1), instance_name VARCHAR(500), instance_name_type INT, instance_target VARCHAR(1000), instance_target_type INT, instance_data BLOB(1M), instance_data_type INT, instance_source VARCHAR(32000), instance_source_type int)");
			createTableStatement.execute("CREATE INDEX instances_instance_id ON instances(instance_id)");
			createTableStatement.execute("CREATE TABLE data_alphabet (entry_id INT NOT NULL, entry_value VARCHAR(1000))");
			createTableStatement.execute("CREATE TABLE target_alphabet (entry_id INT NOT NULL, entry_value VARCHAR(1000))");
		}
		tableCheck.close();

		connection.setAutoCommit(false);
	}

	/** Convert an array of integers to an array of bytes by copying the bits of each int to four bytes. Based on code at java2s.com. */
	public static byte[] intArrayToByteArray(int[] src) {
		int srcLength = src.length;
		byte[] dst = new byte[srcLength << 2];
    
		for (int i=0; i<srcLength; i++) {
			int x = src[i];
			int j = i << 2;
			dst[j++] = (byte) ((x >>> 0) & 0xff);           
			dst[j++] = (byte) ((x >>> 8) & 0xff);
			dst[j++] = (byte) ((x >>> 16) & 0xff);
			dst[j++] = (byte) ((x >>> 24) & 0xff);
		}
		return dst;
	}

	/** Convert an array of bytes to an array of integers by copying the bits directly. Based on code at java2s.com. */
	public static int[] byteArrayToIntArray(byte[] src) {
        int dstLength = src.length >>> 2;
        int[] dst = new int[dstLength];
        
        for (int i=0; i < dstLength; i++) {
            int j = i << 2;
            int x = 0;
            x += (src[j++] & 0xff) << 0;
            x += (src[j++] & 0xff) << 8;
            x += (src[j++] & 0xff) << 16;
            x += (src[j++] & 0xff) << 24;
            dst[i] = x;
        }
        return dst;
    }

	public void saveAlphabets(Alphabet dataAlphabet, Alphabet targetAlphabet) throws Exception {
		PreparedStatement insertStatement;

		if (dataAlphabet != null) {
			insertStatement = 
				connection.prepareStatement("INSERT INTO data_alphabet VALUES (?, ?)");
			for (int i = 0; i < dataAlphabet.size(); i++) {
				insertStatement.setInt(1, i);
				insertStatement.setString(2, dataAlphabet.lookupObject(i).toString());
				insertStatement.executeUpdate();
			}
			insertStatement.close();
		}

		if (targetAlphabet != null) { 
			insertStatement =
				connection.prepareStatement("INSERT INTO target_alphabet VALUES (?, ?)");
			for (int i = 0; i < targetAlphabet.size(); i++) {
				insertStatement.setInt(1, i);
				insertStatement.setString(2, targetAlphabet.lookupObject(i).toString());
				insertStatement.executeUpdate();
			}
			insertStatement.close();
		}

		connection.commit();
	}

	public void saveInstances(Iterator<Instance> iterator) throws Exception {

		PreparedStatement insertStatement = 
			connection.prepareStatement("INSERT INTO instances VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, NULL, 0)");
		
		long startTime = System.currentTimeMillis();
		int instancesSaved = 0;

		while (iterator.hasNext()) {

			Instance instance = iterator.next();
		
			Object name = instance.getName();
			if (name instanceof String) {
				insertStatement.setString(1, (String) name);
				insertStatement.setInt(2, STRING);
			}
			else {
				throw new Exception("Class " + name.getClass() + " is not supported");
			}
			
			Object target = instance.getTarget();
			if (target == null) {
				insertStatement.setString(3, null);
				insertStatement.setInt(4, EMPTY);
			}
			else if (target instanceof String) {
				insertStatement.setString(3, (String) target);
				insertStatement.setInt(4, STRING);
			}
			else {
				throw new Exception("Class " + name.getClass() + " is not supported");
			}

			Object data = instance.getData();
			if (data instanceof FeatureSequence) {
				int[] features = ((FeatureSequence) data).getFeatures();
				insertStatement.setBytes(5, intArrayToByteArray(features));
				insertStatement.setInt(6, FEATURE_SEQUENCE);
			}
			else {
				throw new Exception("Class " + name.getClass() + " is not supported");
			}

			insertStatement.executeUpdate();

			instancesSaved ++;
			if (instancesSaved % 100000 == 0) { 
				long diff = System.currentTimeMillis() - startTime;
				startTime = System.currentTimeMillis();
				System.out.println(instancesSaved + "\t" + diff);
			}
		}
		insertStatement.close();
		connection.commit();
	}

	public void cleanup() throws Exception {
		String sqlState = "";
		
		connection.close();

		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (SQLException se) {
			sqlState = se.getSQLState();
		}

		if (sqlState.equals("XJ015")) {
			System.err.println("shutdown successful: " + sqlState);
		}
	}

	public static void main (String[] args) throws Exception {
		DBInstanceStore saver = new DBInstanceStore(args[0]);
		
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		
		// Read data from File objects
		pipeList.add(new Input2CharSequence("UTF-8"));
		
		// Regular expression for what constitutes a token.
		//  This pattern includes Unicode letters, Unicode numbers, 
		//   and the underscore character. Alternatives:
		//    "\\S+"   (anything not whitespace)
		//    "\\w+"    ( A-Z, a-z, 0-9, _ )
		//    "[\\p{L}\\p{N}_]+|[\\p{P}]+"   (a group of only letters and numbers OR
		//                                    a group of only punctuation marks)
		Pattern tokenPattern =
			Pattern.compile("\\p{L}[\\p{L}\\p{P}]*\\p{L}");
		
		// Tokenize raw strings
		pipeList.add(new CharSequence2TokenSequence(tokenPattern));
		pipeList.add(new TokenSequence2FeatureSequence());
		
		CsvIterator reader = new CsvIterator(new FileReader(new File(args[1])),
											 "(.*?)\\t(.*?)\\t(.*)", 3, 2, 1);

		Pipe serialPipe = new SerialPipes(pipeList);

		Iterator<Instance> iterator = serialPipe.newIteratorFrom(reader);

		saver.saveInstances(iterator);
		saver.saveAlphabets(serialPipe.getDataAlphabet(), serialPipe.getTargetAlphabet());
		saver.cleanup();
	}

}