package org.tmatesoft.svn.core;

import java.util.List;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public interface ISVNDirectoryContent extends ISVNEntryContent {
    public List getChildContents() throws SVNException;
}