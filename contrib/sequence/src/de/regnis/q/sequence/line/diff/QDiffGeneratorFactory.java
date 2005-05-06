/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package de.regnis.q.sequence.line.diff;

import java.util.Map;

/**
 * @author TMate Software Ltd.
 */
public interface QDiffGeneratorFactory {
    public static final String GUTTER_PROPERTY = "gutter";
    public static final String EOL_PROPERTY = "eol";
    public static final String WHITESPACE_PROPERTY = "whitespace";
    public static final String COMPARE_EOL_PROPERTY = "compareEOL";
    
    public QDiffGenerator createGenerator(Map properties);
}
