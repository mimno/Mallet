package cc.mallet.fst;


/**
 * Indicates when the value/gradient becomes stale based on updates to CRF's
 * parameters.
 *
 * @author Gaurav Chandalia
 */
public class CRFCacheStaleIndicator implements CacheStaleIndicator {
	protected CRF crf;

	protected int cachedValueChangeStamp = -1;
	protected int cachedGradientChangeStamp = -1;

	public CRFCacheStaleIndicator(CRF crf) {
		this.crf = crf;
		cachedValueChangeStamp = -1;
		cachedGradientChangeStamp = -1;
	}

	/**
	 * Returns true if the value is stale, also updates the cacheValueStamp.
	 */
	public boolean isValueStale() {
		if (crf.weightsValueChangeStamp != cachedValueChangeStamp) {
			cachedValueChangeStamp = crf.weightsValueChangeStamp;
			return true;
		}
    return false;
	}

	/**
	 * Returns true if the gradient is stale, also updates the cacheGradientStamp.
	 */
	public boolean isGradientStale() {
		if (crf.weightsValueChangeStamp != cachedGradientChangeStamp) {
			cachedGradientChangeStamp = crf.weightsValueChangeStamp;
			return true;
		}
    return false;
	}
}
