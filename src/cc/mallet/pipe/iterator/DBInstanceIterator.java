package cc.mallet.pipe.iterator;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import cc.mallet.types.*;
import cc.mallet.pipe.*;

public class DBInstanceIterator implements Iterator<Instance> {

	// Supported types

	public static final int EMPTY = 0;
	public static final int STRING = 1;
	public static final int FEATURE_VECTOR = 2;
	public static final int FEATURE_SEQUENCE = 3;
	public static final int FEATURE_VECTOR_SEQUENCE = 4;
	public static final int LABEL = 5;

	boolean debug = false;

	Connection connection = null;
	Statement statement = null;

	Alphabet dataAlphabet = null;
	Alphabet targetAlphabet = null;
	ResultSet instanceResults;

	int instancesReturned = 0;
	boolean atLeastOneMore = false;

	public DBInstanceIterator (String dbName) throws Exception {
		
		Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
		
		String connectionURL = "jdbc:derby:" + dbName + ";create=true";
		connection = DriverManager.getConnection(connectionURL);

		dataAlphabet = new Alphabet();
		targetAlphabet = new Alphabet(); // How should I distinguish label alphabets?

		statement = connection.createStatement();
		ResultSet alphabetResults = statement.executeQuery("SELECT * FROM data_alphabet ORDER BY entry_id");

		while (alphabetResults.next()) {
			int entryID = alphabetResults.getInt(1);
			String entryValue = alphabetResults.getString(2);

			int insertedID = dataAlphabet.lookupIndex(entryValue);
			if (entryID != insertedID) {
				throw new Exception("Index mismatch in data alphabet for " + entryValue + " expecting " + entryID + " got " + insertedID);
			}
		}
		alphabetResults.close();

		alphabetResults = statement.executeQuery("SELECT * FROM target_alphabet ORDER BY entry_id");

		while (alphabetResults.next()) {
			int entryID = alphabetResults.getInt(1);
			String entryValue = alphabetResults.getString(2);

			int insertedID = targetAlphabet.lookupIndex(entryValue);
			if (entryID != insertedID) {
				throw new Exception("Index mismatch in target alphabet for " + entryValue + " expecting " + entryID + " got " + insertedID);
			}
		}
		alphabetResults.close();

		// Avoid alphabets don't match errors
		if (targetAlphabet.size() == 0) {
			targetAlphabet = null;
		}
		
		instanceResults = statement.executeQuery("SELECT * FROM instances ORDER BY instance_id");
		atLeastOneMore = instanceResults.next();
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

	public Pipe getPipe() {
		return new Noop(dataAlphabet, targetAlphabet);
	}

	public boolean hasNext() {
		return atLeastOneMore;
	}

	public Instance next() {
		Object name = null;
		Object data = null;
		Object target = null;
		Object source = null;
		
		try { 
			int instanceID = instanceResults.getInt(1);
			if (instanceID != instancesReturned) {
				throw new Exception("Expecting instance " + instancesReturned + ", found instance " + instanceID);
			}
			
			int nameType = instanceResults.getInt(3);
			if (nameType == STRING) {
				name = instanceResults.getString(2);
			}
			
			int targetType = instanceResults.getInt(5);
			if (targetType == STRING) {
				target = instanceResults.getString(4);
			}
			
			int dataType = instanceResults.getInt(7);
			if (dataType == FEATURE_SEQUENCE) {
				int[] features = byteArrayToIntArray(instanceResults.getBytes(6));

				for (int i=0; i < features.length; i++) {
					if (features[i] >= dataAlphabet.size()) {
						System.err.println("found " + features[i] + ", expecting size " + dataAlphabet.size());
					}
				}
				data = new FeatureSequence(dataAlphabet, features);
			}
			
			atLeastOneMore = instanceResults.next();
		} catch (Exception e) {
			System.err.println("problem returning instance " + instancesReturned + ": " + e.getMessage());
		}

		instancesReturned++;
		return new Instance(data, target, name, source);
	}

	public static InstanceList getInstances(String dbName) throws Exception {
		DBInstanceIterator dbIterator = new DBInstanceIterator(dbName);

        InstanceList instances = new InstanceList(dbIterator.getPipe());
        instances.addThruPipe(dbIterator);
        dbIterator.cleanup();

		return instances;
	}

	public void cleanup() throws Exception {
		String sqlState = "";
		
		instanceResults.close();
		statement.close();
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

	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}
}