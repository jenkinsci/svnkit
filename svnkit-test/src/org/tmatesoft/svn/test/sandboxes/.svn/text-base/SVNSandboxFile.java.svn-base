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
package org.tmatesoft.svn.test.sandboxes;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNSandboxFile {

    private String myFile;
    private String myContent;
    private String myLinkTarget;

    public SVNSandboxFile(String file) {
        myFile = file;
    }

    public SVNSandboxFile(String file, String content, boolean createLink) {
        myFile = file;
        if (createLink) {
            myLinkTarget = content;
        } else {
            myContent = content;
        }
    }

    public String getFile() {
        return myFile;
    }

    public String getContent() {
        return myContent;
    }

    public String getLinkTarget() {
        return myLinkTarget;
    }

    public void create(File tmp) throws SVNException {
        if (getContent() == null && getLinkTarget() == null) {
            new File(tmp, getFile()).mkdir();
        } else if (getContent() != null) {
            SVNFileUtil.createFile(new File(tmp, getFile()), getContent(), "UTF-8");
        } else if (getLinkTarget() != null) {
            String target = SVNPathUtil.getRelativePath(getFile(), getLinkTarget());
            SVNFileUtil.createSymlink(new File(tmp, getFile()), target);
        }
    }
}
