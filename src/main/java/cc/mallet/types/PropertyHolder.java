package cc.mallet.types;

import cc.mallet.util.PropertyList;

/**
 * Author: saunders Created Nov 15, 2005 Copyright (C) Univ. of Massachusetts Amherst, Computer Science Dept.
 */
public interface PropertyHolder {
	public void setProperty(String key, Object value);

	public Object getProperty(String key);

	public void setNumericProperty(String key, double value);

	public double getNumericProperty(String key);

	public PropertyList getProperties();

	public void setProperties(PropertyList newProperties);

	public boolean hasProperty(String key);

	public void setFeatureValue (String key, double value);

	public double getFeatureValue (String key);

	public PropertyList getFeatures ();

	public void setFeatures (PropertyList pl);
	
	public FeatureVector toFeatureVector (Alphabet dict, boolean binary);
}
