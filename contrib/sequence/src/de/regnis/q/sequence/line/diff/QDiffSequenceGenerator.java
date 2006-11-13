/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package de.regnis.q.sequence.line.diff;

import java.io.*;
import java.util.*;

import de.regnis.q.sequence.*;
import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.line.*;
import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Ian Sullivan
 * @author TMate Software Ltd.
 */
public abstract class QDiffSequenceGenerator implements QDiffGenerator {

	// Abstract ===============================================================

	protected abstract void processBlock(QSequenceDifferenceBlock[] segment, QSequenceLineCache sourceLines, QSequenceLineCache targetLines, String encoding,
	                                     Writer output) throws IOException;

	// Fields =================================================================

	private final String header;

	private Map myProperties;

	// Setup ==================================================================

	protected QDiffSequenceGenerator(Map properties, String header) {
		this.header = header;
		myProperties = properties == null ? Collections.EMPTY_MAP : properties;
		myProperties = Collections.unmodifiableMap(myProperties);
	}

	// Implemented ============================================================

	public void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
		println("Binary files are different", output);
	}

	public void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
		final QSequenceLineResult result;
		try {
			result = QSequenceLineMedia.createBlocks(QSequenceLineRAByteData.create(left), QSequenceLineRAByteData.create(right), getSimplifier());
		}
		catch (QSequenceException ex) {
			throw new IOException(ex.getMessage());
		}

		try {
			final List combinedBlocks = combineBlocks(result.getBlocks(), getGutter());

			boolean headerWritten = false;
			for (Iterator it = combinedBlocks.iterator(); it.hasNext();) {
				List segment = (List)it.next();
				if (segment.isEmpty()) {
					continue;
				}

				if (!headerWritten && header != null) {
					headerWritten = true;
					output.write(header);
				}

				QSequenceDifferenceBlock[] segmentBlocks = (QSequenceDifferenceBlock[])segment.toArray(new QSequenceDifferenceBlock[segment.size()]);
				processBlock(segmentBlocks, result.getLeftCache(), result.getRightCache(), encoding, output);
			}
		}
		finally {
			result.close();
		}
	}

	// Accessing ==============================================================

	protected Map getProperties() {
		return myProperties;
	}

	protected String getEOL() {
		if (getProperties().get(QDiffGeneratorFactory.EOL_PROPERTY) instanceof String) {
			return (String)getProperties().get(QDiffGeneratorFactory.EOL_PROPERTY);
		}
		return System.getProperty("line.separator", "\n");
	}

	protected QSequenceLineSimplifier getSimplifier() {
		final Object ignore = getProperties().get(QDiffGeneratorFactory.IGNORE_SPACE_PROPERTY);
		final QSequenceLineSimplifier baseSimplifier;
		if (QDiffGeneratorFactory.IGNORE_ALL_SPACE.equals(ignore)) {
			baseSimplifier = new QSequenceLineWhiteSpaceSkippingSimplifier();
		}
		else if (QDiffGeneratorFactory.IGNORE_SPACE_CHANGE.equals(ignore)) {
			baseSimplifier = new QSequenceLineWhiteSpaceReducingSimplifier();
		}
		else {
			baseSimplifier = new QSequenceLineDummySimplifier();
		}

		if (getProperties().containsKey(QDiffGeneratorFactory.IGNORE_EOL_PROPERTY)) {
			return new QSequenceLineTeeSimplifier(baseSimplifier, new QSequenceLineEOLUnifyingSimplifier());
		}

		return baseSimplifier;
	}

	protected int getGutter() {
		Object gutterStr = getProperties().get(QDiffGeneratorFactory.GUTTER_PROPERTY);
		if (gutterStr == null) {
			return 0;
		}
		try {
			return Integer.parseInt(gutterStr.toString());
		}
		catch (NumberFormatException e) {
		}
		return 0;
	}

	protected String printLine(QSequenceLine line, String encoding) throws IOException {
		String str = new String(line.getContentBytes(), encoding);
		return str;
	}

	protected void println(Writer output) throws IOException {
		output.write(getEOL());
	}

	protected void println(String str, Writer output) throws IOException {
		if (str != null) {
			output.write(str);
		}
		output.write(getEOL());
	}

	protected void print(String str, Writer output) throws IOException {
		if (str != null) {
			output.write(str);
		}
	}

	// Utils ==================================================================

	private static List combineBlocks(List blocksList, int gutter) {
		List combinedBlocks = new LinkedList();
		List currentList = new LinkedList();

		QSequenceDifferenceBlock lastBlock = null;
		for (Iterator blocks = blocksList.iterator(); blocks.hasNext();) {
			QSequenceDifferenceBlock currentBlock = (QSequenceDifferenceBlock)blocks.next();
			if (lastBlock != null) {
				if (currentBlock.getLeftFrom() - 1 - lastBlock.getLeftTo() > gutter && currentBlock.getRightFrom() - 1 - lastBlock.getRightTo() > gutter) {
					combinedBlocks.add(currentList);
					currentList = new LinkedList();
				}
			}
			currentList.add(currentBlock);
			lastBlock = currentBlock;
		}
		if (!combinedBlocks.contains(currentList)) {
			combinedBlocks.add(currentList);
		}
		return combinedBlocks;
	}
}
