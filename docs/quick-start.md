Once you have downloaded and installed MALLET, the easiest way to get started is through the mallet script. If you installed MALLET in the directory `~/Applications/mallet`, this script will be in `~/Applications/mallet/bin`. The following instructions assume that your current working directory is the MALLET directory. To use the script, specify a command and then some number of options, in this pattern:

    bin/mallet [command] --option value --option value ...

Type `bin/mallet` to get a list of commands, and use the option `--help` with any command to get a description of valid options.

**Data Import**: To load all documents in specified directories into a MALLET data file, with class labels specified by the directory, use the command

    bin/mallet import-dir --input [dir1] [dir2] [...] --output data.mallet

For more information, and many more options, see the [data import quick start guide](import).

**Classification**: To evaluate MaxEnt and Naïve Bayes classifiers trained on this data using 10-fold cross validation, use the command

    bin/mallet train-classifier --input data.mallet \
      --trainer MaxEnt --trainer NaiveBayes \
      --training-portion 0.9 --num-trials 10

This command will run 10 trials, in which the input data is randomly split into 90% training instances and 10% testing instances. For each trial, MALLET trains a MaxEnt classifier and a Naïve Bayes classifier on the training instances, then prints accuracy results and a matrix of correct and predicted labels for each classifier. For more information about training and evaluating classifiers, see the [classification quick start guide](classification).
