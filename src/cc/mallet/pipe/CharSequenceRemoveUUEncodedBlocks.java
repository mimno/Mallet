package cc.mallet.pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;



public class CharSequenceRemoveUUEncodedBlocks extends Pipe {
    /**
     Given a string, remove lines that begin with M and are 61 characters long.
     Note that there are some UUEncoded blocks that do not match this.  
     I have seen some that are 64 characters long, and have no regular prefix character,
     but this filter gets most of them in 20 Newsgroups.

  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/
    public static final Pattern UU_ENCODED_LINE= Pattern.compile ("^M.{60}$");
    
    public CharSequenceRemoveUUEncodedBlocks ()
    {
    }

    @Override public Instance pipe (Instance carrier)
    {
        String string = ((CharSequence)carrier.getData()).toString();
        Matcher m = UU_ENCODED_LINE.matcher(string);
        carrier.setData(m.replaceAll (""));
        return carrier;
    }

    //Serialization
    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;
    
    private void writeObject (ObjectOutputStream out) throws IOException {
        out.writeInt (CURRENT_SERIAL_VERSION);
    }
    
    private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
        @SuppressWarnings("unused")
        int version = in.readInt ();
    }

}
