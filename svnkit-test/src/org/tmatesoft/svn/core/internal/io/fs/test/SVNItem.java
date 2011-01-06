/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs.test;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNItem {

    private String myRepositoryPath;
    private SVNNodeKind myKind;
    private SVNProperties myProperties;
    private String myChecksum;
    private int myNumberOfDeltaChunks;

    public SVNItem(String path, SVNNodeKind kind) {
        myRepositoryPath = path;
        myKind = kind;
        myNumberOfDeltaChunks = 0;
    }

    public String getChecksum() {
        return myChecksum;
    }

    public void setChecksum(String checksum) {
        myChecksum = checksum;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public void setKind(SVNNodeKind kind) {
        myKind = kind;
    }

    public SVNProperties getProperties() {
        return myProperties;
    }

    public void changeProperty(String propName, SVNPropertyValue propVal) {
        if (propVal == null) {
            return;
        }
        if (myProperties == null) {
            myProperties = new SVNProperties();
        }
        myProperties.put(propName, propVal);
    }

     public void changeProperty(String propName, String propVal) {
        if (propVal == null) {
            return;
        }
        if (myProperties == null) {
            myProperties = new SVNProperties();
        }
        myProperties.put(propName, propVal);
    }

    public String getRepositoryPath() {
        return myRepositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        myRepositoryPath = repositoryPath;
    }

    public void incrementDeltaChunk() {
        myNumberOfDeltaChunks++;
    }

    public int getNumberOfDeltaChunks() {
        return myNumberOfDeltaChunks;
    }
}
