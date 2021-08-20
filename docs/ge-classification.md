# Document Classification with Expectation Constraints

In this tutorial we describe training maximum entropy document classifiers with expectation constraints that specify affinities between words and labels. See [Druck, Mann, and McCallum 2008] for more information. We assume that the task is classifying baseball and hockey documents and that we have processed data sets baseball-hockey.train.vectors and baseball-hockey.test.vectors.
These methods require unlabeled training data. We can hide labels using Vectors2Vectors.

    java cc.mallet.classify.tui.Vectors2Vectors \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.unlabeled.vectors \
    --hide-targets

If the data is truly unlabeled, then the easiest way to import it is to assign an arbitrary label to each document, ensuring that each label is used at least once.

## Generalized Expectation

Suppose we know a priori that the words baseball and puck are good indicators of labels baseball and hockey respectively. Specifically, suppose that we estimate that 90% of the documents in which the word puck occurs should be labeled hockey, and similarly for baseball. We may specify these constraints in a file as follows.

    baseball hockey:0.1 baseball:0.9
    puck hockey:0.9 baseball:0.1

The general format for a constraints file is:

    feature_name label_name=probability label_name=probability ...

The number of probabilities must be equal to the number of labels. The feature and label names must match the names in the data and target alphabets exactly.

The following command trains a MaxEnt classifier with the above constraints (assumed to be in file `baseball-hockey.constraints`) using Generalized Expectation (GE) (as described in Druck, Mann, and McCallum 2008). We specify the constraints file using constraintsFile and specify a regularization penalty with `gasussianPriorVariance`.

    mallet train-classifier \
    --training-file   baseball-hockey.unlabeled.vectors \
    --testing-file    baseball-hockey.test.vectors \
    --trainer "MaxEntGETrainer,gaussianPriorVariance=0.1,
      constraintsFile=\"baseball-hockey.constraints\"" \
    --report test:accuracy

### L2 Penalty

By default, the difference between the target and model expectations is penalized using KL divergence (as in Druck, Mann, and McCallum 2008). Instead, we can impose an L2 penalty using the L2 option.

    mallet train-classifier \
    --training-file   baseball-hockey.unlabeled.vectors \
    --testing-file    baseball-hockey.test.vectors \
    --trainer "MaxEntGETrainer,gaussianPriorVariance=0.1,L2=true,
      constraintsFile=\"baseball-hockey.constraints\"" \
    --report test:accuracy

### API

The underlying trainer is `cc.mallet.classify.MaxEntGETrainer`. New GE constraints and penalties for training MaxEnt models can be defined by implementing `cc.mallet.classify.constraints.ge.MaxEntGEConstraint`.

## Generalized Expectation with Target Ranges

It is also possible to specify L2 constraints that do not impose a penalty if the model expectation is within some target range. For example, we can encourage model expectations to be in the range 90-100%.

    baseball baseball:0.9,1 
    hockey hockey:0.9,1

In general, the format for range constraints is:

    feature_name label_name=lower_probability,upper_probability ...

Support for such constraints is provided by MaxEntGERangeTrainer.

    mallet train-classifier \
    --training-file   baseball-hockey.unlabeled.vectors \
    --testing-file    baseball-hockey.test.vectors \
    --trainer "MaxEntGERangeTrainer,gaussianPriorVariance=0.1,
      constraintsFile=\"baseball-hockey.range_constraints\"" \
    --report test:accuracy
    
### API

The underlying trainer is `cc.mallet.classify.MaxEntGERangeTrainer`. New GE constraints and penalties for training MaxEnt models can be defined by implementing `cc.mallet.classify.constraints.ge.MaxEntGEConstraint`.

## Posterior Regularization

There is also support for training MaxEnt models with Posterior Regularization (PR) Ganchev, Graça, Gillenwater, and Taskar 2010. The following command trains a MaxEnt classifier using the above constraints (assumed to be in file `baseball-hockey.constraints`) with PR for 100 iterations. We specify the constraints file using `constraintsFile` and specify a regularization penalty for each step (c.f. Bellare, Druck, and McCallum 2009) with `pGasussianPriorVariance` and `qGaussianPriorVariance`.

    mallet train-classifier \
    --training-file   baseball-hockey.unlabeled.vectors \
    --testing-file    baseball-hockey.test.vectors \
    --trainer "MaxEntPRTrainer,minIterations=100,maxIterations=100,
      pGaussianPriorVariance=0.1,qGaussianPriorVariance=1000,
      constraintsFile=\"baseball-hockey.constraints\"" \
    --report test:accuracy

### API

The underlying trainer is `cc.mallet.classify.MaxEntPRTrainer`. New PR constraints and penalties for training MaxEnt models can be defined by implementing `cc.mallet.classify.constraints.pr.MaxEntPRConstraint`.

## Automated Methods for Obtaining Constraints

Below, we discuss machine-assisted methods for obtaining constraints. Note that these methods do not yet support target ranges.

### User-provided Labeled Features

Rather than specifying the target expectations directly, we may instead specify "labels" for features, and have these converted into target expectations. Suppose we know that the word puck is associated with hockey, and the word baseball is associated with the label baseball. We may specify these labeled features in a file (`baseball-hockey.labeled_features`) as follows.

    baseball baseball
    puck hockey

The general format for a file with labeled features is:

    feature_name label_name label_name ...

`Vectors2FeatureConstraints` can estimate target expectations from a file with labeled features. A simple heuristic for obtaining expectations from labeled features is to uniformly divide constant probability mass among the labels for a feature. By default, 0.9 probability is allocated to the labels for a feature. This estimation method can be specified using heuristic for the targets command option.

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.constraints \
    --features-file baseball-hockey.labeled_features \
    --targets heuristic 

The option majority-prob can be used to specify a value other than 0.9. We can use the constraints file `baseball-hockey.constraints` to perform GE training as above.

### Machine-provided Candidate Features

We may obtain a set of candidate features for which constraints may be expressed using the Latent Dirichlet Allocation (LDA) based method of Druck, Mann, and McCallum 2008.

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.features \
    --feature-selection lda \
    --lda-file baseball-hockey.train.lda \
    --targets none \
    --num-constraints 10 

The lda-file is a serialized LDA model file. See the topic modeling tutorial for more information. Setting targets to none tells `Vectors2FeatureConstraints` to output candidate features only. `baseball-hockey.features` will then contain a list of ten candidate features, one per line.

The above method is unsupervised (i.e. does not look at the true labels). We can also select candidate features using an "oracle" information gain method (infogain) that looks at the true labels. (Note that when using true labels obtaining constraints, `baseball-hockey.train.vectors`, rather than `baseball-hockey.unlabeled.vectors`, must be used.)

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.features \
    --feature-selection infogain \
    --targets none \
    --num-constraints 10

### Machine-provided Target Expectations

Given a set of candidate features, we may estimate constraints using two methods. The first method is to have the machine label the features (by revealing the true labels and using the method of Druck, Mann, and McCallum 2008), and convert these labels into expectations using the same heuristic as above.

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.constraints \
    --features-file baseball-hockey.features \
    --targets heuristic

Note that if the candidate features are also machine-provided, we may perform both steps at the same time using, for example, the command:

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.constraints \
    --feature-selection lda \
    --lda-file baseball-hockey.train.lda \
    --num-constraints 10 \
    --targets heuristic

Finally, we may estimate the expectations using the exact target expectations from the labeled data. The targets option to do this is oracle.

    java cc.mallet.classify.tui.Vectors2FeatureConstraints \
    --input baseball-hockey.train.vectors \
    --output baseball-hockey.constraints \
    --features-file baseball-hockey.features \
    --targets oracle

Note that when using heuristic targets, the machine may discard candidate features in the labeling process (c.f. Druck, Mann, and McCallum 2008). However, the machine does not discard any candidate features when using `--targets oracle` .

## Tips

* For GE training, a `gaussianPriorVariance` of 1 is a reasonable default choice.
* For PR training, in our experience large values for `qGaussianPriorVariance` and small values for `pGaussianPriorVariance` work best.
* The command line interfaces only provide basic functionality. In some cases it may be necessary to tweak the optimization code (by for example setting convergence tolerances or step sizes) in order to obtain good results.
* As a rule of thumb, try to specify a set of constraints that is balanced among labels and covers many documents.
