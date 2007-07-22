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
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCopySource {
    private SVNRevision myPegRevision;
    private SVNRevision myRevision;
    private SVNURL myURL;
    private File myPath;
    private SVNURL myDstURL;
    private File myDstFile;
    private boolean isRessurection;
    private long mySrcRevisionNumber;
    private SVNNodeKind mySrcKind;
    private String mySrcPath;
    private String myDstPath;    
    
    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, File path) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myPath = new File(SVNPathUtil.validateFilePath(path.getAbsolutePath())).getAbsoluteFile();
    }

    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, SVNURL url) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myURL = url;
    }

    public File getPath() {
        return myPath;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }
    
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    public SVNURL getURL() {
        return myURL;
    }
    
    public boolean isURL() {
        return myURL != null;
    }
    
    public String getName() {
        if (isURL()) {
            return SVNPathUtil.tail(myURL.getPath());
        } 
        return myPath.getName();
    }
    
    void setDestinationURL(SVNURL dst) {
        myDstURL = dst;
    }
    
    void setDestinationPath(File dst) {
        myDstFile = dst;
    }
    
    File getDstFile() {
        return myDstFile;
    }
    
    SVNURL getDstURL() {
        return myDstURL;
    }
    
    void setPegRevision(SVNRevision pegRevision) {
        myPegRevision = pegRevision;
    }

    void setRevision(SVNRevision revision) {
        myRevision = revision;
    }
    
    void setPath(File path) {
        myPath = path;
    }
    
    void setURL(SVNURL url) {
        myURL = url;
    }

    boolean isRessurection() {
        return isRessurection;
    }

    void setRessurection(boolean isRessurection) {
        this.isRessurection = isRessurection;
    }

    long getSrcRevisionNumber() {
        return mySrcRevisionNumber;
    }

    void setSrcRevisionNumber(long srcRevisionNumber) {
        mySrcRevisionNumber = srcRevisionNumber;
    }

    SVNNodeKind getSrcKind() {
        return mySrcKind;
    }
    
    void setSrcKind(SVNNodeKind srcKind) {
        mySrcKind = srcKind;
    }

    String getSrcPath() {
        return mySrcPath;
    }

    void setSrcPath(String srcPath) {
        mySrcPath = srcPath;
    }

    String getDstPath() {
        return myDstPath;
    }

    void setDstPath(String dstPath) {
        myDstPath = dstPath;
    }

}
