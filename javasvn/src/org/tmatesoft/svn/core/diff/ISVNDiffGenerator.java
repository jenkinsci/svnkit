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

package org.tmatesoft.svn.core.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNDiffGenerator {

    public void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException;

    public void generateDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException;
    
}
