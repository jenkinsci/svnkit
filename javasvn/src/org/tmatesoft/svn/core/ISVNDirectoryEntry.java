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

package org.tmatesoft.svn.core;

import java.util.Iterator;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNDirectoryEntry extends ISVNEntry {
    
    /* utility methods, called during update */
    
    public ISVNFileEntry addFile(String name, long revision) throws SVNException;
    
    public ISVNDirectoryEntry addDirectory(String name, long revision) throws SVNException;
    
    public void deleteChild(String name, boolean keepInfo) throws SVNException ;
    
    /* wc modification methods */
    
    public ISVNEntry copy(String name, ISVNEntry toCopy) throws SVNException;
    
    public ISVNEntry scheduleForAddition(String name, boolean mkdir, boolean recurse) throws SVNException;

    public ISVNEntry scheduleForDeletion(String name) throws SVNException;

    public ISVNEntry scheduleForDeletion(String name, boolean moved) throws SVNException;
    
    public boolean revert(String name) throws SVNException;

    public void unschedule(String name) throws SVNException;
    
    /* RO operations */

    public Iterator childEntries() throws SVNException;
    
    public Iterator deletedEntries() throws SVNException;
    
    public Iterator unmanagedChildEntries(boolean includeIgnored) throws SVNException;

    public ISVNEntry getChild(String name) throws SVNException;

    public ISVNEntry getUnmanagedChild(String name) throws SVNException;
    
    public boolean isIgnored(String name) throws SVNException;
}
