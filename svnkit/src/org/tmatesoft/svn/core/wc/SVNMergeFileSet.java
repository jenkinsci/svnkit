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
    private SVNMergeResult myMergeResult;
    private SVNMergeAction myMergeAction;
    
    public SVNMergeFileSet(SVNAdminArea adminArea, SVNLog log,
            String basePath, 
            String localPath, 
            String wcPath, 
            String reposPath, 
            String resultPath, 
            String mimeType, 
            boolean binary) {
        myAdminArea = adminArea;
        myLog = log;
        myLocalFilePath = localPath;
        myBaseFilePath = basePath;
        myRepositoryFilePath = reposPath;
        myWCFilePath = wcPath;
        myMergeResultFilePath = resultPath;
        myMimeType = mimeType;
        myIsBinary = binary;
    }
    
    public void setMergeLabels(String baseLabel, String localLabel, String repositoryLabel) {
        myLocalLabel = localLabel == null ? ".working" : localLabel;
        myBaseLabel = baseLabel == null ? ".old" : baseLabel;
        myRepositoryLabel = repositoryLabel == null ? ".new" : repositoryLabel;
    }
    
    public void setMergeResult(SVNMergeResult result) {
        myMergeResult = result;
    }
    
    public SVNMergeResult getMergeResult() {
        return myMergeResult;
    }

    public void setMergeAction(SVNMergeAction action) {
        myMergeAction = action;
    }
    
    public SVNMergeAction getMergeAction() {
        return myMergeAction;
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
    
    public String getBasePath() {
        return myBaseFilePath;
    }
    
    public String getLocalPath() {
        return myLocalFilePath;
    }
    
    public String getWCPath() {
        return myWCFilePath;
    }
    
    public String getRepositoryPath() {
        return myRepositoryFilePath;
    }
    
    public String getResultPath() {
        return myMergeResultFilePath;
    }
    
    public File getBaseFile() {
        return myAdminArea.getFile(myBaseFilePath);
    }
    
    public File getWCFile() {
        return myAdminArea.getFile(myWCFilePath);
    }
    
    public File getLocalFile() {
        return myAdminArea.getFile(myLocalFilePath);
    }
    
    public File getRepositoryFile() {
        return myAdminArea.getFile(myRepositoryFilePath);
    }
    
    public File getResultFile() {
        return myAdminArea.getFile(myMergeResultFilePath);
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
}