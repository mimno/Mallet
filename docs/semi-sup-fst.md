# Lightly Supervised and Semi-Supervised Sequence Labeling

## SimpleTaggerWithConstraints

`SimpleTaggerWithConstraints` is a command line interface for training linear chain CRFs with expectation constraints and unlabeled data. It is very similar to `SimpleTagger`, described here. If the data is truly unlabeled, then the easiest way to import it is to assign an arbitrary label to each token, ensuring that each label is used at least once.

## Training CRFs with Generalized Expectation (GE) Criteria

Mallet CRFs can be trained with expectation constraints using Generalized Expectation (GE). For example, parameters can be estimated to match prior distributions over labels for particular words. For more information, see:

    Generalized Expectation Criteria for 
    Semi-Supervised Learning of Conditional Random Fields
    Gideon Mann and Andrew McCallum
    ACL 2008

The implementation uses a new algorithm (see Chapter 6) that is O(NL2) (where L is #labels and N is sequence length) for both one and two state constraints (rather than O(NL3) and O(NL4)).

See also the tutorial for training MaxEnt models with expectation constraints.

### Command Line

To train a CRF with expectation constraints using GE, specify `--learning ge` when running `SimpleTaggerWithConstraints`. Available constraint violation penalties include `--penalty kl` for KL divergence and `--penalty l2` for L2. Note that when using a KL divergence penalty, the constraint must specify a complete target label distribution. `SimpleTaggerWithConstraints` currently does not support transition (two label) constraints.

    java cc.mallet.fst.semi_supervised.tui.SimpleTaggerWithConstraints \
      --train true --test lab --penalty kl --learning ge \
      --threads 4 --orders 0,1 \
      train test constraints

Here train and test contain the training and testing data in `SimpleTagger` format. The format of the constraints file is either

    feature_name label_name=probability label_name=probability ...

or, when using target ranges instead of values (currently only compatible with --learning ge --penalty l2)

    feature_name label_name=lower_probability,upper_probability ...

### API

**Constraint setup**: GE constraints implement the `GEConstraint` interface. There are a few types of constraints implemented in `cc.mallet.fst.semi-supervised.constraints`. Suppose we have constraints as in Mann & McCallum 08 stored in a `HashMap` with `Integer` keys that represent feature indices (obtained from a data `Alphabet`) and values that are `double[]` probability distributions over labels (where array indices correspond to a target `Alphabet`). The `ArrayList<GEConstraint>` required by the trainer can be created using the following code snippet:
  
    OneLabelKLGEConstraints constraints = new OneLabelKLGEConstraints();
    for (int featureIndex : constraints.keySet()) {
      constraints.addConstraint(featureIndex, constraints.get(featureIndex), weight);
    }
    ArrayList constraintsList = new ArrayList();
    constraintsList.add(constraints);
  
The weight variable controls the weight of each constraint in the GE objective function. Changing `OneLabelKLGEConstraints` to `OneLabelL2GEConstraints` minimizes squared difference rather than KL divergence. Changing `OneLabelKLGEConstraints` to `OneLabelL2RangeGEConstraints` allows the use of target ranges, and constraints on only a subset of the labels. Changing `OneLabelKLGEConstraints` to `TwoLabelKLGEConstraints` gives constraints on pairs of consecutive labels. In this case the distributions are `double[][]` rather than `double[]`.

**Implementing new constraints**: To implement a new constraint, create a new class that implements the GEConstraint interface. See documentation in GEConstraint for more information.

**Training**: The following code snippet trains a CRF with the above constraints.

    int numThreads = 1;
    CRFTrainerByGE trainer = new CRFTrainerByGE(crf, constraints, numThreads);
    trainer.setGaussianPriorVariance(gaussianPriorVariance);
    trainer.train(unlabeled, Integer.MAX_VALUE);
    
The `InstanceList` `unlabeled` contains the unlabeled data to be used in GE training.

**Multi-threading**: Portions of the GE code are multi-threaded to increase effeciency. To use multi-threading, simply set the number of threads by changing the `numThreads` variable above.

**Labeled data**: To train with both labeled data and constraints, use `cc.mallet.fst.CRFOptimizableByGradientValues`, an optimizable objective that is the sum of multiple other objectives, with `cc.mallet.fst.CRFOptimizableByLabelLikelihood` and `cc.mallet.fst.semi_supervised.CRFOptimizableByGE`.

Notes and Tips:
* The labels of the unlabeled data are never considered by the code, so the targets for unlabeled instances could be present (so that `TransducerEvaluators` can use them), or they could be null.
* If using this method with no labeled data, use a CRF with dense weights and fully connected transitions.
* The built-in `GEConstraints` use constraint features that are binary and normalized by the total count of the input feature. This means the targets and expectations are probability distributions. However, constraint features that are not binary or normalized can be created by implementing a new `GEConstraint`.
* The included two label constraints disregard the transition into the first position to avoid complications with the start state.
* The `StateLabelMap` maps between CRF states and labels. In a most cases, a default one-to-one StateToLabelMap is sufficient. This type of map is created by default by `CRFTrainerByGE`. However, a custom `StateLabelMap` can be specified using the `setStateLabelMap` method of `CRFTrainerByGE`.
* If using a special CRF start state that is not included in the label set, create a `StateLabelMap`, call `addStartState` with the state index of the start state, and specify this mapping to `CRFTrainerByGE` using `setStateLabelMap`.
* In some cases it may be necessary to tweak the optimization code (by for example setting convergence tolerances or step sizes) in order to obtain good results.
* As a rule of thumb, try to specify a set of constraints that is balanced among labels and covers many tokens.

## Training CRFs with Posterior Regularization (PR)

Mallet CRFs can also be trained with expectation constraints and unlabeled data using Posterior Regularization (PR). For example, parameters can be estimated to match prior distributions over labels for particular words. For more information Bellare, Druck, and McCallum 2009 and Ganchev, Graça, Gillenwater, and Taskar 2010. See also the tutorial for [training MaxEnt models with expectation constraints](ge-classification).

### Command Line

To train a CRF with expectation constraints using PR, specify `--learning pr` when running `SimpleTaggerWithConstraints`. Currently only `--penalty l2` is available and range constraints are not supported.

    java cc.mallet.fst.semi_supervised.tui.SimpleTaggerWithConstraints \
      --train true --test lab --penalty l2 --learning pr \                                         
      --threads 4 --orders 0,1 \
      train test constraints

Here train and test contain the training and testing data in `SimpleTagger` format. The format of the constraints file is:

    feature_name label_name=probability label_name=probability ...

### API

Constraint setup: PR constraints implement the PRConstraint interface. Suppose we have constraints as in Mann & McCallum 08 stored in a `HashMap` with `Integer` keys that represent feature indices (obtained from a data `Alphabet`) and values that are `double[]` probability distributions over labels (where array indices correspond to a target `Alphabet`). The `ArrayList<PRConstraint>` required by the trainer can be created using the following code snippet:

    OneLabelL2PRConstraints constraints = new OneLabelL2PRConstraints();
    for (int featureIndex : constraints.keySet()) {
      constraints.addConstraint(featureIndex, constraints.get(featureIndex), weight);
    }
    ArrayList constraintsList = new ArrayList();
    constraintsList.add(constraints);
    
The weight variable controls the weight of each constraint in the PR objective function.

Implementing new constraints: To implement a new constraint, create a new class that implements the `PRConstraint` interface. See documentation in `PRConstraint` for more information.

Training: The following code snippet trains a CRF with the above constraints using 100 iterations of PR.

    int numThreads = 1;
    CRFTrainerByPR trainer = new CRFTrainerByPR(crf, constraints, numThreads);
    trainer.setPGaussianPriorVariance(gaussianPriorVariance);
    trainer.train(unlabeled, 100, 100);

The InstanceList unlabeled contains the unlabeled data to be used in PR criteria.

Multi-threading: Portions of the PR code are multi-threaded to increase effeciency. To use multi-threading, simply set the number of threads by changing the numThreads variable above.

Notes and Tips (see also the GE notes above):
* The current implementation only supports fully connected finite state machines.
* In some cases it may be necessary to tweak the optimization code (by for example setting convergence tolerances, step sizes, number of iterations) in order to obtain good results.
* As a rule of thumb, try to specify a set of constraints that is balanced among labels and covers many tokens.
* For PR training, in our experience large values for the constraint weight and small values for `pGaussianPriorVariance` work best.

## Training CRFs with Entropy Regularization (ER)

This semi-supervised learning method aims to maximize the conditional log-likelihood of labeled data while minimizing the conditional entropy of the model's predictions on unlabeled data. For more information, see the following papers:

    Semi-Supervised Conditional Random Fields for 
    Improved Sequence Segmentation and Labeling
    Feng Jiao, Shaojun Wang, Chi-Hoon Lee, Russell Greiner, Dale Schuurmans
    ACL 2006

    Efficient Computation of Entropy Gradient for 
    Semi-Supervised Conditional Random Fields
    Gideon Mann, Andrew McCallum
    HLT/NAACL 2007

Mallet includes an implementation of Entropy Regularization for training CRFs. The implementation is based on the O(nS2) algorithm of Mann and McCallum 07. As in Jiao et al. 06, the Mallet implementation uses the maximum likelihood parameter estimate as a starting point for optimizing the complete objective function. The weight of the ER term in the objective function can be set using the `setEntropyWeight` method in the `CRFTrainerByEntropyRegularization` class.
Example code:

    CRFTrainerByEntropyRegularization trainer = 
    new CRFTrainerByEntropyRegularization(crf);
    trainer.setEntropyWeight(gamma);
    trainer.setGaussianPriorVariance(sigma);
    trainer.addEvaluator(eval);
    trainer.train(trainingData, unlabeledData, Integer.MAX_VALUE);


Notes:

* You must use the method `train(InstanceList trainingData, InstanceList unlabeledData, int numIterations)` to perform training.
* Labeled data is only used in the likelihood term, and unlabeled data is only used in the ER term. This means the labels of the unlabeled data are never considered by the code, so the targets for unlabeled instances could be present (so that `TransducerEvaluators` can use them), or they could be null.
* In our experience, the performance of this method is highly dependent on the weighting factor. We have often observed ER decrease performance because the entropy term dominates the objective function (or gradient).
