[![Build Status](https://travis-ci.com/MNCC/Mallet.svg?branch=master)](https://travis-ci.com/MNCC/Mallet)
[![codecov](https://codecov.io/gh/MNCC/Mallet/branch/master/graph/badge.svg)](https://codecov.io/gh/MNCC/Mallet)

Mallet
======

Website: http://https://mimno.github.io/Mallet/

MALLET is a Java-based package for statistical natural language processing, document classification, clustering, topic modeling, information extraction, and other machine learning applications to text.

MALLET includes sophisticated tools for document classification: efficient routines for converting text to "features", a wide variety of algorithms (including Naïve Bayes, Maximum Entropy, and Decision Trees), and code for evaluating classifier performance using several commonly used metrics.

In addition to classification, MALLET includes tools for sequence tagging for applications such as named-entity extraction from text. Algorithms include Hidden Markov Models, Maximum Entropy Markov Models, and Conditional Random Fields. These methods are implemented in an extensible system for finite state transducers.

Topic models are useful for analyzing large collections of unlabeled text. The MALLET topic modeling toolkit contains efficient, sampling-based implementations of Latent Dirichlet Allocation, Pachinko Allocation, and Hierarchical LDA.

Many of the algorithms in MALLET depend on numerical optimization. MALLET includes an efficient implementation of Limited Memory BFGS, among many other optimization methods.

In addition to sophisticated Machine Learning applications, MALLET includes routines for transforming text documents into numerical representations that can then be processed efficiently. This process is implemented through a flexible system of "pipes", which handle distinct tasks such as tokenizing strings, removing stopwords, and converting sequences into count vectors.

An add-on package to MALLET, called GRMM, contains support for inference in general graphical models, and training of CRFs with arbitrary graphical structure.

## Installation

To build a Mallet 2.0 development release, you must have the Apache ant build tool installed. From the command prompt, first change to the mallet directory, and then type
`ant`

If `ant` finishes with `"BUILD SUCCESSFUL"`, Mallet is now ready to use.

If you would like to deploy Mallet as part of a larger application, it is helpful to create a single ".jar" file that contains all of the compiled code. Once you have compiled the individual Mallet class files, use the command:
`ant jar`

This process will create a file "mallet.jar" in the "dist" directory within Mallet.

## Usage

Once you have installed Mallet you can use it using the following command:
```
bin/mallet [command] --option value --option value ...
```
Type `bin/mallet` to get a list of commands, and use the option `--help` with any command to get a description of valid options.

For details about the commands please visit the API documentation and website at: https://mimno.github.io/Mallet/


## List of Algorithms:

* Topic Modelling
  * LDA
  * Parallel LDA
  * DMR LDA
  * Hierarchical LDA
  * Labeled LDA
  * Polylingual Topic Model
  * Hierarchical Pachinko Allocation Model (PAM)
  * Weighted Topic Model
  * LDA with integrated phrase discovery
  * Word Embeddings (word2vec) using skip-gram with negative sampling
* Classification
  * AdaBoost
  * Bagging
  * Winnow
  * C45 Decision Tree
  * Ensemble Trainer
  * Maximum Entropy Classifier (Multinomial Logistic Regression)
  * Naive Bayes
  * Rank Maximum Entropy Classifier
  * Posterior Regularization Auxiliary Model
* Clustering
  * Greedy Agglomerative
  * Hill Climbing
  * K-Means
  * K-Best
* Sequence Prediction Models
  * Conditional Random Fields
  * Maximum Entropy Markov Models
  * Hidden Markov Models
  * Semi-Supervised Sequence Prediction Models
* Linear Regression



