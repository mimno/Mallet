package cc.mallet.types;

/** An interface for objects that contain one or more Alphabets.
 * <p>  
 * The primary kind of type checking among MALLET objects such as Instances, InstanceLists, Classifiers, etc is
 * by checking that their Alphabets match. */
public interface AlphabetCarrying {
	Alphabet getAlphabet();
	Alphabet[] getAlphabets();
	//boolean alphabetsMatch (AlphabetCarrying object);  //Now you should simply call the static method Alphabet.alphabetsMatch().
}
