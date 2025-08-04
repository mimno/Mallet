package cc.mallet.topics.tree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class Utils {
	/**
	 * Add an item to a map of counts.
	 */
	public static void addToMap(Map<String, Integer> map, String word) {
		int count = 0;
		if (map.containsKey(word))
			count = map.get(word);
		map.put(word, count+1);
	}

	/**
	 * Sort a map by value and return a list of the sorted keys.
	 * 
	 *  adapted from:
	 *  http://www.programmersheaven.com/download/49349/download.aspx
	 * 
	 */
	public static<T, V> List<T> sortByValue(Map<T, V> map) {
		List<Map.Entry<T, V>> list = new LinkedList<Map.Entry<T, V>>(
				map.entrySet());
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o2)).getValue())
						.compareTo(((Map.Entry) (o1)).getValue());
			}
		});
		// logger.info(list);
		List<T> result = new ArrayList<T>();
		for (Iterator<Map.Entry<T, V>> it = list.iterator(); it.hasNext();) {
			Map.Entry<T, V> entry = (Map.Entry<T, V>) it.next();
			result.add(entry.getKey());
		}
		return result;
	}
	
	/**
	 * Read all the lines in a file and return them in a list.
	 */
	public static List<String> readAll(String filename) throws Exception {
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		String line = "";
		while ((line = reader.readLine()) != null)
			lines.add(line);
		reader.close();
		return lines;
	}
	
	/**
	 * Converts a list of strings into a single space-separated string.
	 */
	public static String listToString(List<String> words) {
		String str = "";
		for (String word : words) {
			str += " " + word;
		}
		return str.substring(1);
	}
	
	/**
	 * Converts a space-separated string of words into list form.
	 */
	public static List<String> stringToList(String str) {
		String[] parts = str.toLowerCase().split("\\s+");
		return Arrays.asList(parts);
	}
}
