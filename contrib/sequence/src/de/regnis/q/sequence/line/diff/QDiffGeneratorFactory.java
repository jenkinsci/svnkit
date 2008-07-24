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

import java.util.*;

/**
 * @author TMate Software Ltd.
 */
public interface QDiffGeneratorFactory {
	public static final String GUTTER_PROPERTY = "gutter";
	public static final String EOL_PROPERTY = "eol";
	public static final String WHITESPACE_PROPERTY = "whitespace";
	public static final String IGNORE_EOL_PROPERTY = "ignore-eol-style";
	public static final String IGNORE_SPACE_PROPERTY = "ignore-space";
	public static final String IGNORE_SPACE_CHANGE = "space-change";
	public static final String IGNORE_ALL_SPACE = "all-space";

	public QDiffGenerator createGenerator(Map properties);
}
