/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.util.HashMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNStatusReporter17 implements ISVNReporterBaton, ISVNReporter {

    private final SVNRepository repository;
    private final SVNReporter17 reportBaton;
    private final SVNStatusEditor17 editor;
    private final SVNURL repositoryLocation;
    private final HashMap<String, SVNLock> locks;
    private ISVNReporter reporter;

    public SVNStatusReporter17(SVNRepository repository, SVNReporter17 reportBaton, SVNStatusEditor17 editor) {
        this.repository = repository;
        this.reportBaton = reportBaton;
        this.editor = editor;
        this.repositoryLocation = repository.getLocation();
        this.locks = new HashMap<String, SVNLock>();
    }

    public void report(ISVNReporter reporter) throws SVNException {
        this.reporter = reporter;
        reportBaton.report(this);
    }

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        setPath(path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
    }

    public void setPath(String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void deletePath(String path) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void finishReport() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public void abortReport() throws SVNException {
        throw new UnsupportedOperationException();
    }

}
