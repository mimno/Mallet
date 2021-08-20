## Mallet: MAchine Learning for LanguagE Toolkit

MALLET is a Java-based package for statistical natural language processing, document classification, clustering, topic modeling, information extraction, and other machine learning applications to text.

MALLET includes sophisticated tools for document classification: efficient routines for converting text to "features", a wide variety of algorithms (including Naïve Bayes, Maximum Entropy, and Decision Trees), and code for evaluating classifier performance using several commonly used metrics. [Quick Start](classification) / [Developer's Guide](classifier-devel)

In addition to classification, MALLET includes tools for sequence tagging for applications such as named-entity extraction from text. Algorithms include Hidden Markov Models, Maximum Entropy Markov Models, and Conditional Random Fields. These methods are implemented in an extensible system for finite state transducers. [Quick Start](sequences) / [Developer's Guide](fst)

Topic models are useful for analyzing large collections of unlabeled text. The MALLET topic modeling toolkit contains efficient, sampling-based implementations of Latent Dirichlet Allocation, Pachinko Allocation, and Hierarchical LDA. [Quick Start](topics)

Many of the algorithms in MALLET depend on numerical optimization. MALLET includes an efficient implementation of Limited Memory BFGS, among many other optimization methods. [Developer's Guide](optimization)

In addition to sophisticated Machine Learning applications, MALLET includes routines for transforming text documents into numerical representations that can then be processed efficiently. This process is implemented through a flexible system of "pipes", which handle distinct tasks such as tokenizing strings, removing stopwords, and converting sequences into count vectors. [Quick Start](import) / [Developer's Guide](import-devel)

An add-on package to MALLET, called GRMM, contains support for inference in general graphical models, and training of CRFs with arbitrary graphical structure. [About GRMM](https://github.com/mimno/GRMM).

—

The toolkit is Open Source Software, and is released under the Apache 2.0 License. You are welcome to use the code under the terms of the licence for research or commercial purposes, however please acknowledge its use with a citation:

    McCallum, Andrew Kachites.  "MALLET: A Machine Learning for Language Toolkit." http://mallet.cs.umass.edu. 2002.

