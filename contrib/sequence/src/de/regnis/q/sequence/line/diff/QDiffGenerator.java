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

import de.regnis.q.sequence.line.QSequenceLineRAData;

import java.io.*;

/**
 * @author TMate Software Ltd.
 */
public interface QDiffGenerator {

	void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException;

	void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException;

    void generateTextDiff(RandomAccessFile left, RandomAccessFile right, String encoding, Writer output) throws IOException;

    void generateTextDiff(QSequenceLineRAData left, QSequenceLineRAData right, String encoding, Writer output) throws IOException;

    void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException;
}
