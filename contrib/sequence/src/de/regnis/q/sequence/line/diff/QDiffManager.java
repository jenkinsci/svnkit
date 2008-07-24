/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
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

    public static void generateTextDiff(RandomAccessFile left, RandomAccessFile right, String encoding, Writer output, QDiffGenerator generator) throws IOException {
		if (generator == null || output == null) {
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
