package cc.mallet.util;

import java.util.*;
import java.io.*;
import java.text.NumberFormat;

import java.util.zip.*;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

/**
 * Sometimes you have a topic sampling state, but not the original 
 * instance list file. This class is a command line tool that reads
 * a state.gz file and creates an instance list file that is compatible
 * with that state.
 * */

public class StateToInstances {

    static CommandOption.File inputFile =   new CommandOption.File
        (StateToInstances.class, "input", "FILE", true, null,
         "The gzipped state file containing one row per token", null);

    static CommandOption.File outputFile = new CommandOption.File
        (StateToInstances.class, "output", "FILE", true, new File("mallet.data"),
         "Write the instance list to this file", null);

    public static void main(String[] args) throws Exception {
        
        // Process the command-line options
        CommandOption.setSummary (StateToInstances.class,
                                  "Tool for recovering an instance list file from an LDA state file.");
        CommandOption.process (StateToInstances.class, args);


        Alphabet alphabet = new Alphabet();
        Pipe pipe = new Noop(alphabet, null);
        InstanceList instances = new InstanceList(pipe);

        String line;
        String[] fields;

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile.value))));
        line = reader.readLine();

        // Skip some lines starting with "#" that describe the format and specify hyperparameters
        while (line.startsWith("#")) {
            line = reader.readLine();
        }
                
        fields = line.split(" ");
        int[] tokenBuffer = new int[10000];
        int documentLength = 0;
        int currentDocument = 0;
        
        while (line != null) {
            int document = Integer.parseInt(fields[0]);
            int position = Integer.parseInt(fields[2]);
            int type = Integer.parseInt(fields[3]);
            int alphabetType = alphabet.lookupIndex(fields[4]);

            if (type != alphabetType) { System.err.println("Expecting " + type + " for " + fields[4] + ", got " + alphabetType); }

            if (document != currentDocument) {
                
                int[] types = new int[documentLength];
                System.arraycopy(tokenBuffer, 0, types, 0, documentLength);

                instances.addThruPipe(new Instance(new FeatureSequence(alphabet, types), null, null, null));

                documentLength = 0;
                
                // Sometimes there are empty documents in the instance list
                //  If there are any document IDs between the previous and the current, add empty FeatureSequences
                currentDocument++;

                while (currentDocument < document) {
                    instances.addThruPipe(new Instance(new FeatureSequence(alphabet, new int[0]), null, null, null));
                    currentDocument++;
                }

                currentDocument = document;
            }
            
            // Expand the buffer if necessary
            if (tokenBuffer.length <= position) {
                int[] biggerBuffer = new int[ tokenBuffer.length * 2 ];
                System.arraycopy(tokenBuffer, 0, biggerBuffer, 0, tokenBuffer.length);
                tokenBuffer = biggerBuffer;
            }

            if (documentLength != position) { System.err.println("Expecting position " + documentLength + ", got " + position); }
            tokenBuffer[position] = type;
            documentLength++;

            line = reader.readLine();
            if (line != null) {
                fields = line.split(" ");
            }
        }
 
        // Add the last document
        int[] types = new int[documentLength];
        System.arraycopy(tokenBuffer, 0, types, 0, documentLength);
        
        instances.addThruPipe(new Instance(new FeatureSequence(alphabet, types), null, null, null));
    
        instances.save(outputFile.value);
    }
}