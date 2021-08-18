# Working with sequences

Many data sets, such as text collections and genetic databases, consist of sequences of distinct values. MALLET includes implementations of widely used sequence algorithms including hidden Markov models (HMMs) and linear chain conditional random fields (CRFs). These algorithms support applications such as gene finding and named-entity recognition.

For a general introduction to CRFs, there are tutorials such as [Sutton and McCallum (2006)](http://homepages.inf.ed.ac.uk/csutton/publications/crf-tutorial.pdf). A developer's guide is available for [sequence tagging in MALLET](fst). The MALLET Javadoc API contains information for programmers interested in incorporating sequence tagging into their own work, in the `cc.mallet.fst` package. For semi-supervised sequence labeling, see [this tutorial](semi-sup-fst).

## SimpleTagger

SimpleTagger is a command line interface to the MALLET Conditional Random Field (CRF) class. Here we present an extremely simple example showing the use of SimpleTagger to label a sequence of text. Your input file should be in the following format:
        
        Bill CAPITALIZED noun
        slept non-noun
        here LOWERCASE STOPWORD non-noun

That is, each line represents one token, and has the format:

    feature1 feature2 ... featuren label

Then you can train a CRF using SimpleTagger like this (on one line), where `train.txt` is the name of the file with the lines shown above:

    bin/mallet run cc.mallet.fst.SimpleTagger --train true --model-file nouncrf train.txt

The `--train true` option specifies that we are training, and `--model-file nouncrf` specifies where we would like the CRF written to. This produces a trained CRF in the file `nouncrf`.

If we have a file `test.txt` we would like labelled:

    CAPITALIZED Al
            slept
            here

we can do this with the CRF in file `nouncrf` with:

    bin/mallet run cc.mallet.fst.SimpleTagger --model-file nouncrf --include-input true test.txt

which produces the following output:

    Number of predicates: 9
    noun CAPITALIZED Al 
    non-noun  slept 
    non-noun  here 

If we remove `--include-input true` we will only print the output labels (`noun`, `non-noun`).

To use multi-threaded CRF training, specify the number of threads with `--threads`:

    bin/mallet run cc.mallet.fst.SimpleTagger --train true --model-file nouncrf --threads 8 train.txt

A list of all the options available with SimpleTagger can be obtained by specifying the `--help` option:

    bin/mallet run cc.mallet.fst.SimpleTagger --help
