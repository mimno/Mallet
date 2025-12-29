package cc.mallet.extract;

import java.util.List;
import java.util.ArrayList;
import java.util.Formatter;

public class Element {
	public String tag;
	public ArrayList<Element> children;

	public Element(String s) {
		tag = s;
		children = new ArrayList<Element>();
	}

	public void addContent(Element e) {
		children.add(e);
	}

	public void setContent(Element e) {
		children.add(e);
	}

	public void addContent(List list) {
		children.addAll(list);
	}

	public String toString() {
		Formatter buffer = new Formatter();
		buffer.format("<%s>", tag);
		for (Element e: children) {
			buffer.format(e.toString());
		}
		buffer.format("</%s>", tag);
		return buffer.toString();
	}
}