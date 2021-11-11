# Transformations

Sometimes we want to modify a dataset, but doing so requires knowing global statistics for the dataset itself.
In this case we need to import a data set and then transform it to create a new file.

## Pruning rare or frequent words

To remove low frequency words, we can specify a minimum number of occurrences.

    bin/mallet prune --input input.seq --output pruned.seq --prune-count 5
    
In most natural language collections removing very infrequent words can have a large effect on the vocabulary size, but relatively little effect on the number of tokens.
You can also prune based on document frequency with `--prune-document-freq`,
which may be more robust in cases where one document has a large number of instances of a word that otherwise never occurs.

To remove high frequency words, you can use inverse document frequency (IDF).
A simple way to think of IDF is that if a word occurs in 1 of every 10 documents, its IDF will be `log(10)` = 2.3.
The IDF of a word that occurs in 1 of every 100 = 10<sup>2</sup> documents will have IDF twice that, 4.6, and 1 in 1000 will be 6.9.

    bin/mallet prune --input input.seq --output pruned.seq --min-idf 4.6

Remember that IDF is *inverse*, so to remove **frequent** words set the **min** IDF, and to remove **rare** words set the **max** IDF with `--max-idf`.

## Custom stoplists

Another way to remove words is to create a custom stoplist.
This command will print the token and document counts in tab delimited format for an instance list:

    bin/mallet info --input input.seq --print-feature-counts

If you have access to bash-style commands, one thing I do all the time is:

    bin/mallet info --input sagas_stopped.seq --print-feature-counts | sort -nr -k 3 | head -100 | cut -f 1 > stoplist.txt
    
This sorts the words in descending order by document frequency, takes the first 100, grabs just the first column (the word itself) and saves it as `stoplist.txt`.
I then edit that file manually to make judgements about what I want to keep or not, and to see if there are errors or unexpected words that I might want to know about.

## Train / test splits

Another common task is to split an instance list into multiple sets, for example to train a model and then test its generalization performance.

    bin/mallet split --input input.seq --training-file train.seq --testing-file test.seq --training-portion 0.7

This will copy a random 70% of the instances from the `input.seq` file into `train.seq` and the rest into `test.seq`.
Remember that you are responsible for ensuring that there are not duplicate instances that are in both sets!
The commands `split` and `prune` are actually the same class, so you can do both operations together.

## "Authorless" topic models

This class implements the method from "Authorless Topic Models" by Thompson and Mimno, COLING 2018.

The goal is to reduce the frequency of words that are 
unusually associated with a particular label. This is useful
as a pre-processing step for topic modeling becuase it reduces
the correlation of topics to known class labels. The problem
comes up most often in fiction, where topics tend to simply
reproduce lists of characters.
 
The input is a labeled feature sequence, of the sort used
for topic modeling. Unlike the regular topic modeling system,
labels are required, since we need something to correlate.

The output is another feature sequence with word tokens removed.
Note that some words may disappear from the corpus, but they will
still be present in the alphabet.

The code takes one parameter, equivalent to a p-value where the 
null hypothesis is that a word occurs no more frequently in one 
category than in the collection as a whole.

    bin/mallet run cc.mallet.transform.DownsampleLabelWords --input input.seq --output downsampled.seq
    
This process will by default write a file documenting the number of times each word was removed from each label.
Multiple and overlapping labels are currently not supported.
