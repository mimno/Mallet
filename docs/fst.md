# Sequence Tagging Developer's Guide

MALLET provides implementations of the HMM, MEMM and linear chain CRF algorithms for working with sequences, as often occur in biological and text data. This guide gives examples of training, evaluating and extending linear chain CRFs. HMM and MEMM models follow a similar pattern.

The CRF implementation in the cc.mallet.fst package consist of 3 parts: the model (CRF extending the `Transducer`), the trainer (`CRFTrainerByValueGradients` extending the `TransducerTrainer`) and the evaluator (`MultiSegmentationEvaluator` extending the `TransducerEvaluator`). The trainer is simply a thin wrapper that collects the sufficient statistics required by the [optimization algorithm](optimization) used to train the model. These sufficient statistics are computed by the `CRFOptimizableBy*` objects that represent the terms in the CRF objective function.

The following snippet trains and tests a CRF model in a supervised setting.

    public void run (InstanceList trainingData, InstanceList testingData) {
        // setup:
        //    CRF (model) and the state machine
        //    CRFOptimizableBy* objects (terms in the objective function)
        //    CRF trainer
        //    evaluator and writer

        // model
        CRF crf = new CRF(trainingData.getDataAlphabet(),
                          trainingData.getTargetAlphabet());
        // construct the finite state machine
        crf.addFullyConnectedStatesForLabels();
        // initialize model's weights
        crf.setWeightsDimensionAsIn(trainingData, false);

        //  CRFOptimizableBy* objects (terms in the objective function)
        // objective 1: label likelihood objective
        CRFOptimizableByLabelLikelihood optLabel =
            new CRFOptimizableByLabelLikelihood(crf, trainingData);

        // CRF trainer
        Optimizable.ByGradientValue[] opts =
            new Optimizable.ByGradientValue[]{optLabel};
        // by default, use L-BFGS as the optimizer
        CRFTrainerByValueGradients crfTrainer =
            new CRFTrainerByValueGradients(crf, opts);

        // *Note*: labels can also be obtained from the target alphabet
        String[] labels = new String[]{"I-PER", "I-LOC", "I-ORG", "I-MISC"};
        TransducerEvaluator evaluator = new MultiSegmentationEvaluator(
            new InstanceList[]{trainingData, testingData},
            new String[]{"train", "test"}, labels, labels) {
          @Override
          public boolean precondition(TransducerTrainer tt) {
            // evaluate model every 5 training iterations
            return tt.getIteration() % 5 == 0;
          }
        };
        crfTrainer.addEvaluator(evaluator);

        CRFWriter crfWriter = new CRFWriter("ner_crf.model") {
          @Override
          public boolean precondition(TransducerTrainer tt) {
            // save the trained model after training finishes
            return tt.getIteration() % Integer.MAX_VALUE == 0;
          }
        };
        crfTrainer.addEvaluator(crfWriter);

        // all setup done, train until convergence
        crfTrainer.setMaxResets(0);
        crfTrainer.train(trainingData, Integer.MAX_VALUE);
        // evaluate
        evaluator.evaluate(crfTrainer);

        // save the trained model (if CRFWriter is not used)
        // FileOutputStream fos = new FileOutputStream("ner_crf.model");
        // ObjectOutputStream oos = new ObjectOutputStream(fos);
        // oos.writeObject(crf);
    }
  
### Speed

To take advantage of multi-core processors, the fst package contains classes that compute the sufficient statistics in a multi-threaded setting. Replace the `CRFOptimizableByLabelLikelihood` object in the above snippet by the following code:

    // number of threads to be used, depends on the number of cores and the size
    // of the training data
    int numThreads = 32;
    CRFOptimizableByBatchLabelLikelihood batchOptLabel =
        new CRFOptimizableByBatchLabelLikelihood(crf, trainingData, numThreads);
    ThreadedOptimizable optLabel = new ThreadedOptimizable(
        batchOptLabel, trainingData, crf.getParameters().getNumFactors(),
        new CRFCacheStaleIndicator(crf));
    ...
    ...
    // in the end, after training is done...
    optLabel.shutdown(); // clean exit for all the threads 
  
Or, instead use `CRFTrainerByThreadedLikelihood`.

    CRFTrainerByThreadedLikelihood trainer = 
      new CRFTrainerByThreadedLikelihood(crf, numThreads);
    trainer.train(trainingData);
    trainer.shutdown();
  
### Print Output

Add the following evaluator to the trainer:

    // *Note*: the output size can grow very big very quickly
    ViterbiWriter viterbiWriter = new ViterbiWriter(
        "ner_crf", // output file prefix
        new InstanceList[] { trainingData, testingData },
        new String[] { "train", "test" }) {
      @Override
      public boolean precondition (TransducerTrainer tt) {
        return tt.getIteration() % Integer.MAX_VALUE == 0;
      }
    };
    crfTrainer.addEvaluator(viterbiWriter);
  
### Computing the Probability of an Output Sequence

To compute the probability of an output sequence `outputSeq` for input sequence `inputSeq`, use two `SumLattice` objects. The first computes the log score of `outputSeq`, while the second computes the log-partition function.

    double logScore = new SumLatticeDefault(crf,inputSeq,outputSeq).getTotalWeight();
    double logZ = new SumLatticeDefault(crf,inputSeq).getTotalWeight();
    double prob = Math.exp(logScore - logZ);

### Computing Marginal Probabilities

To compute marginal probabilities over states for input sequence `inputSeq`, use a `SumLattice` object, which runs the forward backward algorithm.

    SumLattice lattice = new SumLatticeDefault(crf,inputSeq);
    // probability of transitioning from state si at input position ip-1
    // to state sj at input position ip
    double twoStateMarginal = lattice.getXiProbability(ip,crf.getState(si),crf.getState(sj));
    // probability of being in state si at input position ip
    double oneStateMarginal = lattice.getGammaProbability(ip+1,crf.getState(si));

### Semi-supervised CRF

Several methods have been proposed for semi-supervised CRFs. From an implementation standpoint, this means adding more terms in the objective function. This can be done by adding more optimizables (for example, implementing a `CRFOptimizableByEntropyRegularization` for [Entropy Regularization](http://www.cs.umass.edu/~mccallum/papers/entropygradient-naacl2007.pdf) and `CRFOptimizableByGE` for [Generalized Expectation Criteria](http://www.cs.umass.edu/~mccallum/papers/gecrf08acl.pdf)).
