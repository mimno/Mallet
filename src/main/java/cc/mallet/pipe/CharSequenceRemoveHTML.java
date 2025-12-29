/* Copyright (C) 2006 Univ. of Massachusetts Amherst, Computer Science Dept.
 This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
 http://www.cs.umass.edu/~mccallum/mallet 
 This software is provided under the terms of the Common Public License,
 version 1.0, as published by http://www.opensource.org.  For further
 information, see the file `LICENSE' included with this distribution. */

package cc.mallet.pipe;


import javax.swing.text.html.*;

import cc.mallet.pipe.iterator.FileIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import java.io.*;

/**
 * This pipe removes HTML from a CharSequence. The HTML is actually parsed here,
 * so we should have less HTML slipping through... but it is almost certainly
 * much slower than a regular expression, and could fail on broken HTML.
 * 
 * @author Greg Druck <a href="mailto:gdruck@cs.umass.edu">gdruck@cs.umass.edu</a>
 */

public class CharSequenceRemoveHTML extends Pipe {

    @Override public Instance pipe(Instance carrier) {
        String text = ((CharSequence) carrier.getData()).toString();
        
        // I take these out ahead of time because the
        // Java HTML parser seems to die here.
        text = text.replaceAll("\\<NOFRAMES\\>","");
        text = text.replaceAll("\\<\\/NOFRAMES\\>","");
        
        ParserGetter kit = new ParserGetter();
        HTMLEditorKit.Parser parser = kit.getParser();
        HTMLEditorKit.ParserCallback callback = new TagStripper();

        try {
            StringReader r = new StringReader(text);
            parser.parse(r, callback, true);
        } catch (IOException e) {
            System.err.println(e);
        }
        String result = ((TagStripper) callback).getText();
        carrier.setData((CharSequence) result);
        return carrier;
    }

    private class TagStripper extends HTMLEditorKit.ParserCallback {
        private String text;

        public TagStripper() {
            text = "";
        }

        @Override public void handleText(char[] txt, int position) {
            for (int index = 0; index < txt.length; index++) {
                text += txt[index];
            }
            text += "\n";
        }

        public String getText() {
            return text;
        }

    }

    private class ParserGetter extends HTMLEditorKit {
        // purely to make this method public
        public HTMLEditorKit.Parser getParser() {
            return super.getParser();
        }
    }

    public static void main(String[] args) {
        String htmldir = args[0];
        Pipe pipe = new SerialPipes(new Pipe[] { new Input2CharSequence(),
                new CharSequenceRemoveHTML() });
        InstanceList list = new InstanceList(pipe);
        list.addThruPipe(new FileIterator(htmldir, FileIterator.STARTING_DIRECTORIES));

        for (int index = 0; index < list.size(); index++) {
            Instance inst = list.get(index);
            System.err.println(inst.getData());
        }

    }

}
