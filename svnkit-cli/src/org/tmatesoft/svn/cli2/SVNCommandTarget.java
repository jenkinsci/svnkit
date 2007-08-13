/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandTarget {
    
    private boolean myHasPegRevision;
    private String myTarget;
    private SVNRevision myPegRevision = SVNRevision.UNDEFINED;
    private File myFile;

    public SVNCommandTarget(String target) throws SVNException {
        this(target, false);
    }

    public SVNCommandTarget(String target, File file) {
        myTarget = SVNPathUtil.canonicalizePath(target);
        myFile = file;
    }

    public SVNCommandTarget(String target, boolean hasPegRevision) throws SVNException {
        myTarget = target;
        myHasPegRevision = hasPegRevision;
        if (myHasPegRevision) {
            parsePegRevision();
        } 
        myTarget = SVNPathUtil.canonicalizePath(myTarget);
    }
    
    public String getTarget() {
        return myTarget;
    }

    public boolean isURL() {
        return SVNCommandUtil.isURL(myTarget);
    }
    
    public boolean isFile() { 
        return !isURL();
    }
    
    public File getFile() {
        if (myFile != null) {
            return myFile;
        }
        if (isFile()) {            
            return new File(myTarget).getAbsoluteFile();
        }
        return null;
    }
    
    public SVNURL getURL() throws SVNException {
        if (isURL()) {
            return SVNURL.parseURIEncoded(myTarget);
        }
        return null;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }

    private void parsePegRevision() throws SVNException {
        int index = myTarget.lastIndexOf('@');
        if (index > 0) {
            String revStr = myTarget.substring(index + 1);
            if (revStr.indexOf('/') >= 0) {
                return;
            }
            if (revStr.length() == 0) {
                myPegRevision = isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
                myTarget = myTarget.substring(0, index);
                return;
            }
            if (isURL() && revStr.length() > 6 && 
                    revStr.toLowerCase().startsWith("%7b") && revStr.toLowerCase().endsWith("%7d")) {
                revStr = SVNEncodingUtil.uriDecode(revStr);
            }
            SVNRevision revision = SVNRevision.parse(revStr);
            if (revision != SVNRevision.UNDEFINED) {
                myPegRevision = revision;
                myTarget = myTarget.substring(0, index);
                return;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error parsing revision ''{0}''", myTarget.substring(index + 1));
            SVNErrorManager.error(err);
        }
    }
}
