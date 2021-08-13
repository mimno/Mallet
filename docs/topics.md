## Topic Modeling

Topic models provide a simple way to analyze large volumes of unlabeled text. A "topic" consists of a cluster of words that frequently occur together. Using contextual clues, topic models can connect words with similar meanings and distinguish between uses of words with multiple meanings. For a general introduction to topic modeling, see for example Blei's [Probabilistic Topic Modeling](http://www.cs.columbia.edu/~blei/papers/Blei2012.pdf).

Shawn Graham, Scott Weingart, and Ian Milligan have written an excellent [tutorial on Mallet topic modeling](http://programminghistorian.org/lessons/topic-modeling-and-mallet).

Mallet runs in Java. If you are working in Python, see Antoniak's [Little Mallet Wrapper](https://github.com/maria-antoniak/little-mallet-wrapper).

For an example showing how to use the Java API to import data, train models, and infer topics for new documents, see the [topic model developer's guide](topics-devel).

The MALLET topic model package includes an extremely fast and highly scalable implementation of Gibbs sampling, efficient methods for document-topic hyperparameter optimization, and tools for inferring topics for new documents given trained models.

### Importing Documents
Once MALLET has been downloaded and installed, the next step is to import text files into MALLET's internal format. The following instructions assume that the documents to be used as input to the topic model are in separate files, in a directory that contains no other files. See the introduction to importing data in MALLET for more information and other import methods.

Change to the MALLET directory and run the command
    bin/mallet import-dir --input /data/topic-input --output topic-input.mallet \
      --keep-sequence --remove-stopwords

To learn more about options for the `import-dir` command use the argument `--help`.

### Building Topic Models
Once you have imported documents into MALLET format, you can use the train-topics command to build a topic model, for example:
    bin/mallet train-topics --input topic-input.mallet \
      --num-topics 100 --output-state topic-state.gz
      
Use the option `--help` to get a complete list of options for the train-topics command. Commonly used options include:

`--input [FILE]` Use this option to specify the MALLET collection file you created in the previous step.

`--num-topics [NUMBER]` The number of topics to use. The best number depends on what you are looking for in the model. The default (10) will provide a broad overview of the contents of the corpus. The number of topics should depend to some degree on the size of the collection, but 200 to 400 will produce reasonably fine-grained results.

`--num-iterations [NUMBER]` The number of sampling iterations should be a trade off between the time taken to complete sampling and the quality of the topic model.

### Hyperparameter Optimization
`--optimize-interval [NUMBER]` This option turns on hyperparameter optimization, which allows the model to better fit the data by allowing some topics to be more prominent than others. Optimization every 10 iterations is reasonable.
`--optimize-burn-in [NUMBER]` The number of iterations before hyperparameter optimization begins. Default is twice the optimize interval.

### Model Output
`--output-model [FILENAME]` This option specifies a file to write a serialized MALLET topic trainer object. This type of output is appropriate for pausing and restarting training, but does not produce data that can easily be analyzed.

`--output-state [FILENAME]` Similar to output-model, this option outputs a compressed text file containing the words in the corpus with their topic assignments. This file format can easily be parsed and used by non-Java-based software. Note that the state file will be GZipped, so it is helpful to provide a filename that ends in .gz.

`--output-doc-topics [FILENAME]` This option specifies a file to write the topic composition of documents. See the --help options for parameters related to this file.

`--output-topic-keys [FILENAME]` This file contains a "key" consisting of the top k words for each topic (where k is defined by the `--num-top-words` option). This output can be useful for checking that the model is working as well as displaying results of the model. In addition, this file reports the Dirichlet parameter of each topic. If hyperparamter optimization is turned on, this number will be roughly proportional to the overall portion of the collection assigned to a given topic.

### Topic Inference
`--inferencer-filename [FILENAME]` Create a topic inference tool based on the current, trained model. Use the MALLET command bin/mallet infer-topics --help to get information on using topic inference.

Note that you must make sure that the new data is compatible with your training data. Use the option `--use-pipe-from [MALLET TRAINING FILE]` in the MALLET command `bin/mallet import-file` or `import-dir` to specify a training file.

### Topic Held-out probability
`--evaluator-filename [FILENAME]` The previous section describes how to get topic proportions for new documents. We often want to estimate the log probability of new documents, marginalized over all topic configurations. Use the MALLET command `bin/mallet evaluate-topics --help` to get information on using held-out probability estimation.

As with topic inference, you must make sure that the new data is compatible with your training data. Use the option `--use-pipe-from [MALLET TRAINING FILE]` in the MALLET command `bin/mallet import-file` or `import-dir` to specify a training file.
