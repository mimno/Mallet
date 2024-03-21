package cc.mallet.topics.tree;

import java.io.Serializable;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * This class defines a two level hashmap, so a value will be indexed by two keys.
 * The value is int, and two keys are both int.
 * 
 * @author Yuening Hu
 */

public class HIntIntObjectHashMap<V> implements Serializable{
	TIntObjectHashMap<TIntObjectHashMap<V>> data;
	
	public HIntIntObjectHashMap () {
		this.data = new TIntObjectHashMap<TIntObjectHashMap<V>>();
	}
	
	/**
	 * If keys do not exist, insert value.
	 * Else update with the new value.
	 */
	public void put(int key1, int key2, V value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntObjectHashMap<V>());
		}
		TIntObjectHashMap<V> tmp = this.data.get(key1);
		tmp.put(key2, value);
	}
	
	/**
	 * Return the HashMap indexed by the first key.
	 */
	public TIntObjectHashMap<V> get(int key1) {
		return this.data.get(key1);
	}
	
	/**
	 * Return the value indexed by key1 and key2.
	 */
	public V get(int key1, int key2) {
		if (this.contains(key1, key2)) {
			return this.data.get(key1).get(key2);
		} else {
			System.out.println("HIntIntObjectHashMap: key does not exist! " + key1 + " " + key2);
			return null;
		}
	}
	
	/**
	 * Return the first key set.
	 */
	public int[] getKey1Set() {
		return this.data.keys();
	}
	
	/**
	 * Check whether key1 is contained in the first key set or not.
	 */
	public boolean contains(int key1) {
		return this.data.contains(key1);
	}
	
	/**
	 * Check whether the key pair (key1, key2) is contained or not.
	 */
	public boolean contains(int key1, int key2) {
		if (this.data.contains(key1)) {
			return this.data.get(key1).contains(key2);
		} else {
			return false;
		}
	}
	
}
