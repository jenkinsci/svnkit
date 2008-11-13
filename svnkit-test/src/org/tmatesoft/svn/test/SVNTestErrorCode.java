/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.SVNErrorCode;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestErrorCode extends SVNErrorCode {

    private static final int ERR_BASE = 120000;
    private static final int ERR_CATEGORY_SIZE = 5000;

    public static final int TEST_CATEGORY = ERR_BASE + 30 * ERR_CATEGORY_SIZE;


    public static final SVNTestErrorCode UNKNOWN = new SVNTestErrorCode(CL_CATEGORY, 1, "Undefined test failure");

    protected SVNTestErrorCode(int category, int index, String description) {
        super(category, index, description);
    }
}
