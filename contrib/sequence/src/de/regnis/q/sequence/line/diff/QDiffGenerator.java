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

/**
 * @author TMate Software Ltd.
 */
public interface QDiffGenerator {

	void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException;

	void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException;

	void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException;
}
