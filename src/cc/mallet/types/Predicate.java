package cc.mallet.types;

/** A boolean test on an object argument, primary for use with anonymous inner classes, 
 * to simulate a closure returning a boolean. */ 
public abstract class Predicate {
	public abstract boolean predicate (Object obj);
	public abstract boolean predicate (Instance obj);
}
