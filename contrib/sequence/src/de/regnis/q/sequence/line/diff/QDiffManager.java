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

/**
 * @author TMate Software Ltd.
 */
public final class QDiffManager {

	// Constants ==============================================================

	public static final String DEFAULT_TYPE = QDiffNormalGenerator.TYPE;

	// Static =================================================================

	private static Map ourDiffGeneratorFactories;

	public static void setup() {
		QDiffNormalGenerator.setup();
		QDiffUniGenerator.setup();
	}

	public static QDiffGenerator getDiffGenerator(String type, Map properties) {
		if (ourDiffGeneratorFactories == null || !ourDiffGeneratorFactories.containsKey(type)) {
			return null;
		}
		return ((QDiffGeneratorFactory)ourDiffGeneratorFactories.get(type)).createGenerator(properties);
	}

	public static void generateDiffHeader(String path, String leftInfo, String rightInfo, Writer output, QDiffGenerator generator) throws IOException {
		if (generator == null || output == null) {
			throw new NullPointerException("null argument is not accepted by SVNDiffManager.generateDiff()");
		}
		generator.generateDiffHeader(path, leftInfo, rightInfo, output);
	}

	public static void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output, QDiffGenerator generator) throws IOException {
		if (generator == null || left == null || right == null || output == null) {
			throw new NullPointerException("null argument is not accepted by SVNDiffManager.generateDiff()");
		}
		if (encoding == null) {
			encoding = System.getProperty("file.encoding", "US-ASCII");
		}
		generator.generateTextDiff(left, right, encoding, output);
	}

	public static void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output, QDiffGenerator generator) throws IOException {
		if (generator == null || left == null || right == null || output == null) {
			throw new NullPointerException("null argument is not accepted by SVNDiffManager.generateDiff()");
		}
		if (encoding == null) {
			encoding = System.getProperty("file.encoding", "US-ASCII");
		}
		generator.generateBinaryDiff(left, right, encoding, output);
	}

	public static void registerDiffGeneratorFactory(QDiffGeneratorFactory factory, String type) {
		if (factory == null || type == null) {
			return;
		}
		if (ourDiffGeneratorFactories != null && ourDiffGeneratorFactories.containsKey(type)) {
			return;
		}
		if (ourDiffGeneratorFactories == null) {
			ourDiffGeneratorFactories = new HashMap();
		}
		ourDiffGeneratorFactories.put(type, factory);
	}

	// Setup ==================================================================

	private QDiffManager() {
	}
}
