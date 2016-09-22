package cc.mallet.extract;

public class Text extends Element {
	public String contents;

	public Text(String s) {
		super("text");
		contents = s;
	}

	public String toString() {
		return contents;
	}
}