/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;

/**
 * @author Alexander Kitaev
 */
public interface ISVNEditor extends ISVNDeltaConsumer {
    
    public void targetRevision(long revision) throws SVNException;
    
    public void openRoot(long revision) throws SVNException;
    
    public void deleteEntry(String path, long revision) throws SVNException;
    
    public void absentDir(String path) throws SVNException;
    
    public void absentFile(String path) throws SVNException;
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException;
    
	public void openDir(String path, long revision) throws SVNException;
    
    public void changeDirProperty(String name, String value) throws SVNException;
    
    public void closeDir() throws SVNException;
    
    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException;
    
    public void openFile(String path, long revision) throws SVNException;
    
    public void applyTextDelta(String baseChecksum) throws SVNException;
    
    public void changeFileProperty(String name, String value) throws SVNException;
    
    public void closeFile(String textChecksum) throws SVNException;
    
    public SVNCommitInfo closeEdit() throws SVNException;
    
    public void abortEdit() throws SVNException;
}
