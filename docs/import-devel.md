# Data Import for Java Developers

If you require greater flexibility than the [command-line data import tools](import) offer or you would like to embed MALLET into a larger application, it is useful to understand the MALLET data import API. This API is based around two types of classes: data, in the form of Instance objects contained in InstanceList objects; and classes that operate on data, iterators and pipes.

A MALLET Instance consists of four fields, which can be of any Object type.

* **Name**: This field should contain a value that identifies this instance, usually a String.
* **Label**: The label is primarily used in classification applications. It is usually a value within a finite set of labels, a LabelAlphabet.
* **Data**: Generally a FeatureVector (unordered feature-value pairs) or a FeatureSequence (an ordered list of features, for example a sequence of words).
* **Source**: A representation of the original state of the instance. Often a File object or a String containing the original, untokenized text. Frequently null.

Instances are usually stored in InstanceList objects, which inherit from ArrayList. Currently the standard format for storing MALLET data on disk is serialized `InstanceList`s.

When importing data, it is common to pass an instance through a series of processing steps. These steps are represented in MALLET using the Pipe interface. There are a large number of Pipes &mdash; see the MALLET javadoc API for details. A typical import pipeline begins with an Iterator, such as a `FileIterator` for data that is in one file per instance or a `CsvIterator` for data that is in a single file, which is then passed through a `SerialPipes` object that wraps a sequence of Pipes.

The following example code takes the name of a directory as input and recurses through all sub-directories, loading files that end in `.txt` as instances. Each instance is passed through a series of pipes, defined in the `buildPipe()` method. The label of each instance is taken from the name of the directory that contains the instance.

    package edu.umass.cs.iesl.mimno;

    import java.io.*;
    import java.util.*;
    import java.util.regex.*;

    import cc.mallet.pipe.*;
    import cc.mallet.pipe.iterator.*;
    import cc.mallet.types.*;

    public class ImportExample {

        Pipe pipe;

        public ImportExample() {
            pipe = buildPipe();
        }

        public Pipe buildPipe() {
            ArrayList pipeList = new ArrayList();

            // Read data from File objects
            pipeList.add(new Input2CharSequence("UTF-8"));

            // Regular expression for what constitutes a token.
            //  This pattern includes Unicode letters, Unicode numbers, 
            //   and the underscore character. Alternatives:
            //    "\\S+"   (anything not whitespace)
            //    "\\w+"    ( A-Z, a-z, 0-9, _ )
            //    "[\\p{L}\\p{N}_]+|[\\p{P}]+"   (a group of only letters and numbers OR
            //                                    a group of only punctuation marks)
            Pattern tokenPattern =
                Pattern.compile("[\\p{L}\\p{N}_]+");

            // Tokenize raw strings
            pipeList.add(new CharSequence2TokenSequence(tokenPattern));

            // Normalize all tokens to all lowercase
            pipeList.add(new TokenSequenceLowercase());

            // Remove stopwords from a standard English stoplist.
            //  options: [case sensitive] [mark deletions]
            pipeList.add(new TokenSequenceRemoveStopwords(false, false));

            // Rather than storing tokens as strings, convert 
            //  them to integers by looking them up in an alphabet.
            pipeList.add(new TokenSequence2FeatureSequence());

            // Do the same thing for the "target" field: 
            //  convert a class label string to a Label object,
            //  which has an index in a Label alphabet.
            pipeList.add(new Target2Label());

            // Now convert the sequence of features to a sparse vector,
            //  mapping feature IDs to counts.
            pipeList.add(new FeatureSequence2FeatureVector());

            // Print out the features and the label
            pipeList.add(new PrintInputAndTarget());

            return new SerialPipes(pipeList);
        }

        public InstanceList readDirectory(File directory) {
            return readDirectories(new File[] {directory});
        }

        public InstanceList readDirectories(File[] directories) {

            // Construct a file iterator, starting with the 
            //  specified directories, and recursing through subdirectories.
            // The second argument specifies a FileFilter to use to select
            //  files within a directory.
            // The third argument is a Pattern that is applied to the 
            //   filename to produce a class label. In this case, I've 
            //   asked it to use the last directory name in the path.
            FileIterator iterator =
                new FileIterator(directories,
                                 new TxtFilter(),
                                 FileIterator.LAST_DIRECTORY);

            // Construct a new instance list, passing it the pipe
            //  we want to use to process instances.
            InstanceList instances = new InstanceList(pipe);

            // Now process each instance provided by the iterator.
            instances.addThruPipe(iterator);

            return instances;
        }

        public static void main (String[] args) throws IOException {

            ImportExample importer = new ImportExample();
            InstanceList instances = importer.readDirectory(new File(args[0]));
            instances.save(new File(args[1]));

        }

        /** This class illustrates how to build a simple file filter */
        class TxtFilter implements FileFilter {

            /** Test whether the string representation of the file 
             *   ends with the correct extension. Note that {@ref FileIterator}
             *   will only call this filter if the file is not a directory,
             *   so we do not need to test that it is a file.
             */
            public boolean accept(File file) {
                return file.toString().endsWith(".txt");
            }
        }

    }
