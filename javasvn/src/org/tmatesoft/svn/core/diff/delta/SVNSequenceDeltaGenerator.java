package org.tmatesoft.svn.core.diff.delta;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.*;

import de.regnis.q.sequence.QSequenceDifferenceBlock;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceDeltaGenerator implements ISVNDeltaGenerator {

	// Fields =================================================================

	private static final SVNAllDeltaGenerator ALL_DELTA_GENERATOR = new SVNAllDeltaGenerator();

	// Implemented ============================================================

	public void generateDiffWindow(ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
		try {
			if (!canProcess(workFile, baseFile)) {
				ALL_DELTA_GENERATOR.generateDiffWindow(consumer, workFile, baseFile);
				return;
			}

			doGenerateDiffWindow(workFile, baseFile, consumer);
		}
		catch (IOException ex) {
			throw new SVNException(ex);
		}
	}

	// Utils ==================================================================

	private static void doGenerateDiffWindow(ISVNRAData workFile, ISVNRAData baseFile, ISVNDeltaConsumer consumer) throws IOException, SVNException {
		final SVNSequenceLine[] workLines = new SVNSequenceLineReader(false).read(workFile.read(0, workFile.length()));
		final SVNSequenceLine[] baseLines = new SVNSequenceLineReader(false).read(baseFile.read(0, baseFile.length()));

		final List instructions = new ArrayList();
		final List newDatas = new ArrayList();
		createInstructions(baseLines, workLines, instructions, newDatas);

		final long sourceLength = baseLines.length > 0 ? baseLines[baseLines.length - 1].getTo() + 1 : 0;
		final long targetLength = workLines.length > 0 ? workLines[workLines.length - 1].getTo() + 1 : 0;
		final long newDataLength = determineNewDataLength(newDatas);
		final SVNDiffInstruction[] instructionsArray = (SVNDiffInstruction[])instructions.toArray(new SVNDiffInstruction[instructions.size()]);
		final OutputStream stream = consumer.textDeltaChunk(new SVNDiffWindow(0, sourceLength, targetLength, instructionsArray, newDataLength));
		sendData(newDatas, stream);
		stream.close();
		consumer.textDeltaEnd();
	}

	private static boolean canProcess(ISVNRAData workFile, ISVNRAData baseFile) throws IOException {
		final Reader workReader = new InputStreamReader(workFile.read(0, workFile.length()));
		try {
			if (!FileTypeUtil.isTextFile(workReader, Integer.MAX_VALUE)) {
				return false;
			}
		}
		finally {
			workReader.close();
		}

		final Reader baseReader = new InputStreamReader(baseFile.read(0, baseFile.length()));
		try {
			if (!FileTypeUtil.isTextFile(baseReader, Integer.MAX_VALUE)) {
				return false;
			}
		}
		finally {
			baseReader.close();
		}

		return true;
	}

	private static void createInstructions(final SVNSequenceLine[] baseLines, final SVNSequenceLine[] workLines, final List instructions, final List bytesToSend) {
		final List blocks = SVNSequenceMedia.createBlocks(baseLines, workLines);
		int lastBase = 0;

		for (Iterator it = blocks.iterator(); it.hasNext();) {
			final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock)it.next();
			final int baseFrom = block.getLeftFrom();
			final int baseTo = block.getLeftTo();
			final int workFrom = block.getRightFrom();
			final int workTo = block.getRightTo();

			if (lastBase < baseFrom) {
				final long charFrom = baseLines[lastBase].getFrom();
				final long charTo = baseLines[(baseFrom - 1)].getTo();
				SVNAssert.assertTrue(charFrom <= charTo);
				instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, charTo - charFrom + 1, charFrom));
			}

			if (workTo >= workFrom) {
				final long charFrom = workLines[workFrom].getFrom();
				final long charTo = workLines[workTo].getTo();
				SVNAssert.assertTrue(charFrom <= charTo);
				instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, charTo - charFrom + 1, 0));
				for (int lineIndex = workFrom; lineIndex <= workTo; lineIndex++) {
					bytesToSend.add(workLines[lineIndex].getBytes());
				}
			}

			lastBase = baseTo + 1;
		}

		if (lastBase <= baseLines.length - 1) {
			final long baseFrom = baseLines[lastBase].getFrom();
			final long baseTo = baseLines[(baseLines.length - 1)].getTo();
			SVNAssert.assertTrue(baseFrom <= baseTo);
			instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, baseTo - baseFrom + 1, baseFrom));
		}
	}

	private static long determineNewDataLength(List datas) {
		long length = 0;
		for (Iterator it = datas.iterator(); it.hasNext();) {
			final byte[] data = (byte[])it.next();
			length += data.length;
		}
		return length;
	}

	private static void sendData(List datas, OutputStream stream) throws IOException {
		for (Iterator it = datas.iterator(); it.hasNext();) {
			final byte[] bytes = (byte[])it.next();
			stream.write(bytes);
		}
	}
}