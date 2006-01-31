/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;



/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNCommitParameters implements ISVNCommitParameters {

    public Action onMissingFile(File file) {
        return SKIP;
    }

    public Action onMissingDirectory(File file) {
        return ERROR;
    }

}
