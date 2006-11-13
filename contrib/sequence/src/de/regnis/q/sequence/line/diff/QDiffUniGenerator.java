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
import de.regnis.q.sequence.line.*;

/**
 * @author Ian Sullivan
 * @author TMate Software Ltd.
 */
public final class QDiffUniGenerator extends QDiffSequenceGenerator implements QDiffGeneratorFactory {

	// Constants ==============================================================

	public static final String TYPE = "unified";

	// Static =================================================================

	public static void setup() {
		QDiffManager.registerDiffGeneratorFactory(new QDiffUniGenerator(), QDiffUniGenerator.TYPE);
	}

	// Fields =================================================================

	private Map myGeneratorsCache;

	// Setup ==================================================================

	public QDiffUniGenerator(Map properties, String header) {
		super(initProperties(properties), header);
	}

	private QDiffUniGenerator() {
		super(null, null);
	}

	// Implemented ============================================================

	public void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException {
		leftInfo = leftInfo == null ? "" : "\t" + leftInfo;
		rightInfo = rightInfo == null ? "" : "\t" + rightInfo;
		println("--- " + item + leftInfo, output);
		println("+++ " + item + rightInfo, output);
	}

	protected void processBlock(QSequenceDifferenceBlock[] segment, QSequenceLineCache sourceLines, QSequenceLineCache targetLines,
	                            String encoding, Writer output) throws IOException {
		int gutter = getGutter();
		// header
		StringBuffer header = new StringBuffer();
		header.append("@@");
		int sourceStartLine = segment[0].getLeftFrom();
		int sourceEndLine = segment[segment.length - 1].getLeftTo();
		int targetStartLine = segment[0].getRightFrom();
		int targetEndLine = segment[segment.length - 1].getRightTo();

		int leftStart = Math.max(sourceStartLine - gutter, 0);
		int rightStart = Math.max(targetStartLine - gutter, 0);
		int leftEnd = Math.min(sourceEndLine + gutter, sourceLines.getLineCount() - 1);
		int rightEnd = Math.min(targetEndLine + gutter, targetLines.getLineCount() - 1);

		if (leftStart + 1 >= 0 && (leftEnd - leftStart + 1) >= 0) {
			header.append(" -");

            if (leftStart == 0 && leftEnd < 0) {
                header.append("0,0");
            } else {
                header.append(leftStart + 1);
                if (leftEnd - leftStart + 1 > 1) {
                    header.append(",");
                    header.append(leftEnd - leftStart + 1);
                }
            }

		}
		if (rightStart + 1 > 0 && rightEnd - rightStart + 1 > 0) {
			header.append(" +");
			header.append(rightStart + 1);
			if (rightEnd - rightStart + 1 > 1) {
				header.append(",");
				header.append(rightEnd - rightStart + 1);
			}
		} else {
            header.append(" +0,0");
        }
		header.append(" @@");
		println(header.toString(), output);

		// print gutter context lines before blocks.
		for (int i = leftStart; i < sourceStartLine; i++) {
			print(" " + printLine(sourceLines.getLine(i), encoding), output);
		}
		for (int i = 0; i < segment.length; i++) {
			QSequenceDifferenceBlock block = segment[i];
			for (int j = block.getLeftFrom(); j <= block.getLeftTo(); j++) {
				String line = printLine(sourceLines.getLine(j), encoding);
				print("-" + line, output);
				if (j == sourceLines.getLineCount() - 1) {
					printNoNewLine(output, line);
				}
			}
			for (int j = block.getRightFrom(); j <= block.getRightTo(); j++) {
				String line = printLine(targetLines.getLine(j), encoding);
				print("+" + line, output);
				if (j == targetLines.getLineCount() - 1) {
					printNoNewLine(output, line);
				}
			}
			// print glue lines
			int end = Math.min(block.getLeftTo() + gutter, sourceLines.getLineCount() - 1);
			if (i + 1 < segment.length) {
				end = Math.min(end, segment[i + 1].getLeftFrom() - 1);
			}
			for (int j = block.getLeftTo() + 1; j <= end; j++) {
				String line = printLine(sourceLines.getLine(j), encoding);
				print(" " + printLine(sourceLines.getLine(j), encoding), output);
				if (j == sourceLines.getLineCount() - 1) {
					printNoNewLine(output, line);
				}
			}
		}
	}

	public QDiffGenerator createGenerator(Map properties) {
		if (myGeneratorsCache == null) {
			myGeneratorsCache = new HashMap();
		}
		QDiffGenerator generator = (QDiffGenerator)myGeneratorsCache.get(properties);
		if (generator != null) {
			return generator;
		}
		generator = new QDiffUniGenerator(properties, null);
		myGeneratorsCache.put(properties, generator);
		return generator;
	}

	// Utils ==================================================================

	private void printNoNewLine(Writer output, String line) throws IOException {
		if (!line.endsWith("\n") && !line.endsWith("\r")) {
			println(output);
			println("\\ No newline at end of file", output);
		}
	}

	private static Map initProperties(Map properties) {
		if (properties == null || !properties.containsKey(QDiffGeneratorFactory.GUTTER_PROPERTY)) {
			properties = new HashMap(properties == null ? Collections.EMPTY_MAP : properties);
			properties.put(QDiffGeneratorFactory.GUTTER_PROPERTY, "3");
		}
		return properties;
	}
}
