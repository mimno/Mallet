package cc.mallet.topics.tree;

import java.io.Serializable;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * This class defines a two level hashmap, so a value will be indexed by two keys.
 * The value is int, and two keys are both int.
 * 
 * @author Yuening Hu
 */

public class HIntIntIntHashMap implements Serializable{
	
	TIntObjectHashMap<TIntIntHashMap> data;
	
	public HIntIntIntHashMap() {
		this.data = new TIntObjectHashMap<TIntIntHashMap> ();
	}
	
	/**
	 * If keys do not exist, insert value.
	 * Else update with the new value.
	 */
	public void put(int key1, int key2, int value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntIntHashMap());
		}
		TIntIntHashMap tmp = this.data.get(key1);
		tmp.put(key2, value);
	}
	
	/**
	 * Return the HashMap indexed by the first key.
	 */
	public TIntIntHashMap get(int key1) {
		if(this.contains(key1)) {
			return this.data.get(key1);
		} 
		return null;
	}
	
	/**
	 * Return the value indexed by key1 and key2.
	 */
	public int get(int key1, int key2) {
		if (this.contains(key1, key2)) {
			return this.data.get(key1).get(key2);
		} else {
			System.out.println("HIntIntIntHashMap: key does not exist!");
			return 0;
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
	
	/**
	 * Adjust the value indexed by the key pair (key1, key2) by the specified amount.
	 */
	public void adjustValue(int key1, int key2, int increment) {
		int old = this.get(key1, key2);
		this.put(key1, key2, old+increment);
	}
	
	
	/**
	 * If the key pair (key1, key2) exists, adjust the value by the specified amount,
	 * Or insert the new value.
	 */
	public void adjustOrPutValue(int key1, int key2, int increment, int newvalue) {
		if (this.contains(key1, key2)) {
			int old = this.get(key1, key2);
			this.put(key1, key2, old+increment);
		} else {
			this.put(key1, key2, newvalue);
		}
	}
	
	/**
	 * Remove the first key 
	 */
	public void removeKey1(int key1) {
		this.data.remove(key1);
	}
	
	/**
	 * Remove the second key 
	 */
	public void removeKey2(int key1, int key2) {
		if (this.data.contains(key1)) {
			this.data.get(key1).remove(key2);
		}
	}
	
}
