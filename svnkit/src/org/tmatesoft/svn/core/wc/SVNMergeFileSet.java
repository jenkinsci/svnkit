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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeFileSet {
    
    private String myLocalFilePath;
    private String myBaseFilePath;
    private String myRepositoryFilePath;
    private String myWCFilePath;
    private String myMergeResultFilePath;
    
    private boolean myIsBinary;
    private String myMimeType;
    private SVNAdminArea myAdminArea;
    private SVNLog myLog;
    
    private String myLocalLabel;
    private String myBaseLabel;
    private String myRepositoryLabel;

    private File myLocalFile;
    private File myBaseFile;
    private File myRepositoryFile;
    private File myMergeResultFile;
    
    private Collection myTmpPaths = new ArrayList();

    public SVNMergeFileSet(SVNAdminArea adminArea, SVNLog log,
            File baseFile, 
            File localFile, 
            String wcPath, 
            File reposFile, 
            File resultFile, 
            String mimeType, 
            boolean binary) {
        myAdminArea = adminArea;
        myLog = log;
        myLocalFile = localFile;
        myBaseFile = baseFile;
        myRepositoryFile = reposFile;
        myWCFilePath = wcPath;
        myMergeResultFile = resultFile;
        myMimeType = mimeType;
        myIsBinary = binary;
        
        myBaseFilePath = SVNPathUtil.isChildOf(myAdminArea.getAdminDirectory(), myBaseFile) ? SVNFileUtil.getBasePath(myBaseFile) : null;
        myLocalFilePath = SVNFileUtil.getBasePath(myLocalFile);
        myRepositoryFilePath = SVNPathUtil.isChildOf(myAdminArea.getAdminDirectory(), myRepositoryFile) ? SVNFileUtil.getBasePath(myRepositoryFile) : null;
        myMergeResultFilePath = SVNFileUtil.getBasePath(myMergeResultFile);
    }
    
    public void setMergeLabels(String baseLabel, String localLabel, String repositoryLabel) {
        myLocalLabel = localLabel == null ? ".working" : localLabel;
        myBaseLabel = baseLabel == null ? ".old" : baseLabel;
        myRepositoryLabel = repositoryLabel == null ? ".new" : repositoryLabel;
    }

    public SVNLog getLog() {
        return myLog;
    }
    
    public String getBaseLabel() {
        return myBaseLabel;
    }
    
    public String getLocalLabel() {
        return myLocalLabel;
    }
    
    public String getRepositoryLabel() {
        return myRepositoryLabel;
    }
    
    public String getBasePath() throws SVNException {
        if (myBaseFilePath == null) {
            File tmp = SVNAdminUtil.createTmpFile(myAdminArea);
            SVNFileUtil.copyFile(myBaseFile, tmp, false);
            myBaseFilePath = SVNFileUtil.getBasePath(tmp);
            myTmpPaths.add(myBaseFilePath);
        }
        return myBaseFilePath;
    }
    
    public String getLocalPath() {
        return myLocalFilePath;
    }
    
    public String getWCPath() {
        return myWCFilePath;
    }
    
    public String getRepositoryPath() throws SVNException {
        if (myRepositoryFilePath == null) {
            File tmp = SVNAdminUtil.createTmpFile(myAdminArea);
            SVNFileUtil.copyFile(myRepositoryFile, tmp, false);
            myRepositoryFilePath = SVNFileUtil.getBasePath(tmp);
            myTmpPaths.add(myRepositoryFilePath);
        }
        return myRepositoryFilePath;
    }
    
    public String getResultPath() {
        return myMergeResultFilePath;
    }
    
    public File getBaseFile() {
        return myBaseFile;
    }
    
    public File getWCFile() {
        return myAdminArea.getFile(myWCFilePath);
    }
    
    public File getLocalFile() {
        return myLocalFile;
    }
    
    public File getRepositoryFile() {
        return myRepositoryFile;
    }
    
    public File getResultFile() {
        return myMergeResultFile;
    }
    
    public boolean isBinary() {
        return myIsBinary;
    }
    
    public String getMimeType() {
        return myMimeType;
    }
    
    public SVNAdminArea getAdminArea() {
        return myAdminArea;
    }
    
    public void dispose() throws SVNException {
        // add deletion commands to the log file.
        Map command = new HashMap();
        for (Iterator paths = myTmpPaths.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            command.put(SVNLog.NAME_ATTR, path);
            myLog.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
    }
}