package cc.mallet.fst.semi_supervised.pr;

import cc.mallet.fst.CRF.State;
import cc.mallet.fst.Transducer;
import cc.mallet.types.Sequence;

public class CachedDotTransitionIterator extends Transducer.TransitionIterator {
  State source;
  int index, nextIndex;
  protected double[] weights;
  Object input;

  public CachedDotTransitionIterator(State source,
      Sequence inputSeq, int inputPosition, String output,
      double[] dots) {
    this(source, inputSeq.get(inputPosition), output, dots);
  }

  protected CachedDotTransitionIterator(State source, Object fv,
      String output, double[] dots) {
    this.source = source;
    this.input = fv;
    this.weights = new double[source.numDestinations()];
    for (int i = 0; i < source.numDestinations(); i++) {
      weights[i] = dots[source.getDestinationState(i).getIndex()];
    }
    // Prepare nextIndex, pointing at the next non-impossible transition
    nextIndex = 0;
    while (nextIndex < source.numDestinations()
        && weights[nextIndex] == Transducer.IMPOSSIBLE_WEIGHT)
      nextIndex++;
  }

  public boolean hasNext() {
    return nextIndex < source.numDestinations();
  }

  public Transducer.State nextState() {
    assert (nextIndex < source.numDestinations());
    index = nextIndex;
    nextIndex++;
    while (nextIndex < source.numDestinations()
        && weights[nextIndex] == Transducer.IMPOSSIBLE_WEIGHT)
      nextIndex++;
    return source.getDestinationState(index);
  }

  // These "final"s are just to try to make this more efficient. Perhaps some of
  // them will have to go away
  public final int getIndex() {
    return index;
  }

  public final Object getInput() {
    return input;
  }

  public final Object getOutput() {
    return source.getLabelName(index);
  }

  public final double getWeight() {
    return weights[index];
  }

  public final Transducer.State getSourceState() {
    return source;
  }

  public final Transducer.State getDestinationState() {
    return source.getDestinationState(index);
  }
  
  private static final long serialVersionUID = 1;
}

