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
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
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
    
    public String getPath(File file) {
        String inPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = getFile().getAbsolutePath().replace(File.separatorChar, '/');
        if (equals(inPath, basePath)) {
            return myTarget;
        } else if (inPath.length() > basePath.length() && startsWith(inPath, basePath + "/")) {
            if ("".equals(myTarget)) {
                return inPath.substring(basePath.length() + 1);
            }
            return myTarget + inPath.substring(basePath.length());
        }
        String commonRoot = getCommonAncestor(inPath, basePath);
        if (commonRoot != null) {
            if (inPath.length() == commonRoot.length()) {
                return inPath;
            } else if (startsWith(inPath, commonRoot + "/")) {
                return inPath.substring(commonRoot.length() + 1);
            }
        }
        return inPath;
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
    
    private static boolean startsWith(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().startsWith(p2.toLowerCase());
        }
        return p1.startsWith(p2);
    }

    private static boolean equals(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().equals(p2.toLowerCase());
        }
        return p1.equals(p2);
    }

    private static String getCommonAncestor(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            String ancestor = SVNPathUtil.getCommonPathAncestor(p1.toLowerCase(), p2.toLowerCase());
            if (equals(ancestor, p1)) {
                return p1;
            } else if (equals(ancestor, p2)) {
                return p2;
            } else if (startsWith(p1, ancestor)) {
                return p1.substring(0, ancestor.length());
            }
            return ancestor;
        }
        return SVNPathUtil.getCommonPathAncestor(p1, p2);
    }
}
