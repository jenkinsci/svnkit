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
package org.tmatesoft.svn.test.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestFileDescriptor extends AbstractSVNTestFile {

    public SVNTestFileDescriptor(String path, SVNFileType fileType, byte[] content) {
        super(path, fileType, content);
    }

    public SVNTestFileDescriptor(String path) {
        super(path);
    }

    public SVNTestFileDescriptor(String path, byte[] content) {
        super(path, content);
    }

    public SVNTestFileDescriptor(String path, String content) {
        super(path, content);
    }

    public AbstractSVNTestFile reload() throws SVNException {
        return this;
    }

    public AbstractSVNTestFile dump(File wcRoot) throws SVNException {
        return new SVNTestFile(wcRoot, this).dump(wcRoot);
    }
}
