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

import java.util.ResourceBundle;

import org.tmatesoft.svn.core.SVNException;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public interface ISVNTestOptions {

    public static ISVNTestOptions EMPTY = new ISVNTestOptions() {
        public void load(ResourceBundle bundle) throws SVNException {
        }
    };

    public void load(ResourceBundle bundle) throws SVNException;
}
