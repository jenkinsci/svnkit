package de.regnis.q.sequence.media;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public abstract class QSequenceIntMedia implements QSequenceMedia, QSequenceMediaComparer {

	// Abstract ===============================================================

	public abstract int getSymbolCount();

	public abstract int[] getLeftSymbols();

	public abstract int[] getRightSymbols();

	// Fields =================================================================

	private final QSequenceCanceller canceller;

	// Setup ==================================================================

	protected QSequenceIntMedia(QSequenceCanceller canceller) {
		this.canceller = canceller;
	}

	// Implemented ============================================================

	public final boolean equalsLeft(int left1, int left2) throws QSequenceCancelledException {
		checkCancelled();
		return getLeftSymbols()[left1] == getLeftSymbols()[left2];
	}

	public final boolean equalsRight(int right1, int right2) throws QSequenceCancelledException {
		checkCancelled();
		return getRightSymbols()[right1] == getRightSymbols()[right2];
	}

	// Accessing ==============================================================

	public final void checkCancelled() throws QSequenceCancelledException {
		canceller.checkCancelled();
	}
}