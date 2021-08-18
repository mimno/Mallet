Document Classification

A classifier is an algorithm that distinguishes between a fixed set of classes, such as "spam" vs. "non-spam", based on labeled training examples. MALLET includes implementations of several classification algorithms, including Naïve Bayes, Maximum Entropy, and Decision Trees. In addition, MALLET provides tools for evaluating classifiers.

Training Maximum Entropy document classifiers using Generalized Expectation Criteria is described in this separate [tutorial](ge-classification).

To get started with classification, first load your data into MALLET format as described in the [importing data](import) section.

**Training a classifier**: To train a classifier on a MALLET data file called `training.mallet`, use the command

    bin/mallet train-classifier --input training.mallet --output-classifier my.classifier

Run the `train-classifier` command with the option `--help` for a full list of options, many of which are described below.

**Choosing an algorithm**: The default classification algorithm is Naïve Bayes. To select another algorithm, use the `--trainer` option:

    bin/mallet train-classifier --input training.mallet --output-classifier my.classifier \
      --trainer MaxEnt

Currently supported algorithms include MaxEnt, NaiveBayes, C45, DecisionTree and many others. See the JavaDoc API for the `cc.mallet.classify` package to see the complete current list of Trainer classes.

**Evaluation**: No classifier is perfect, so it is important to know whether a classifier is producing good results on data not used in training. To split a single set of labeled instances into training and testing lists, you can use a command like this:

    bin/mallet train-classifier --input labeled.mallet --training-portion 0.9

This command will random split the data into 90% training instances, which will be used to train the classifier, and 10% testing instances. MALLET will use the classifier to predict the class labels of the testing instances, compare those to the true labels, and report results.

**Reporting options**: The default report option is a "confusion matrix" showing, for each true class label (one per row), the number of instances assigned to each predicted class label (in the colums). Off-diagonal elements represent prediction errors. In addition, you can choose to report other statistics such as accuracy and F1 (a balance between precision and recall). To report accuracy for the training data and F1 for the class label "sports" on test data, use the command

    bin/mallet train-classifier --input labeled.mallet --training-portion 0.9 \
      --report train:accuracy test:f1:sports

**Multiple random splits / Cross-validation**: Performing multiple test/train splits can provide a better view of the performance of a classifier. To use 10 random 90:10 splits, use the options `--training-portion 0.9 --num-trials 10`. To use 10-fold cross-validation use `--cross-validation 10`.

**Comparing multiple algorithms: It is possible to train more than one classifier for each trial, by simply providing more than one `--trainer` option:

    bin/mallet train-classifier --input labeled.mallet --training-portion 0.9 \
      --trainer MaxEnt --trainer NaiveBayes
      
Note that this option cannot take more than one argument, so you must add `--trainer [type]` for every algorithm.

**Applying a Saved Classifier to New Unlabeled Data**: To apply a saved classifier to new unlabeled data, use `Csv2Classify` (for one-instance-per-line data) or `Text2Classify` (for one-instance-per-file data).

    bin/mallet classify-file --input data --output - --classifier classifier
    bin/mallet classify-dir --input datadir --output - --classifier classifier

Using the above commands, classifications are written to standard output. Note that the input for these commands is a raw text file, not an imported Mallet file. This command is designed to be used in "production" mode, where labels are not available. Any label that is present will be ignored.
