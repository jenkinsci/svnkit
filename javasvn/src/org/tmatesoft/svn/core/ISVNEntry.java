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
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;


/**
 * @author TMate Software Ltd.
 */
public interface ISVNEntry {

    public String getPath();
    
    public String getName();
    
    public boolean isDirectory();
    
    public boolean isMissing() throws SVNException;
    
    public boolean isManaged();
    
    public boolean isObstructed();
    
    /* utility methods */
    
    public boolean isScheduledForDeletion() throws SVNException;
    
    public boolean isScheduledForAddition() throws SVNException;
    
    public boolean isPropertiesModified() throws SVNException;
    
    /**
     * Returns entry or working copy property value. 
     * Returns actual custom property value.
     * 
     * @param name
     */
    public String getPropertyValue(String name) throws SVNException;
    
    public Iterator propertyNames() throws SVNException;
    
    /**
     * Modifies entry or working copy property value. 
     * Modifies actual custom propety value making this entry modified. 
     * 
     * @param name
     * @param value
     */
    public void setPropertyValue(String name, String value) throws SVNException;
    
    /**
     * Sends modified properties (i.e. custom properties that were changed since the last merge)
     * to the editor
     */
    public boolean sendChangedProperties(ISVNEditor editor) throws SVNException;

    /**
     * Saves custom properties as base properties file to merge later
     */
    public int applyChangedProperties(Map changedProperties) throws SVNException;
    
    /**
     * Save information kept in memory.
     */
    public void save() throws SVNException;
    public void save(boolean recursive) throws SVNException;
    
    /**
     * Make actual contents and properties be the same as base version.
     */
    public void merge(boolean recursive) throws SVNException;
    
    /**
     * Make base properties and contents same as actual. Called after commit.
     */
    public void commit() throws SVNException;
    
    /**
     * Dismiss all the information stored in memory. Called after save() or export or import operations.
     */
    public void dispose() throws SVNException;
    
    public boolean isConflict() throws SVNException;
    
    public void markResolved() throws SVNException;
    
    public ISVNDirectoryEntry asDirectory();
    
    public ISVNFileEntry asFile();

    public ISVNEntryContent getContent() throws SVNException;

    public String getAlias();
    
    public void setAlias(String alias);
}
