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

package org.tmatesoft.svn.core.test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVN {

    public static void main(String[] args) {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSEntryFactory.setup();
        
        String url = "http://72.9.228.230:8080/svn/jsvn/trunk/";
        try {
            SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
            SVNRepository repository = SVNRepositoryFactory.create(location);
            
            ISVNReporterBaton reporterBaton = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", 21, false);
                    reporter.finishReport();
                }
            };
            
            ISVNEditor editor = new ISVNEditor() {

                public void targetRevision(long revision) throws SVNException {
                }

                public void openRoot(long revision) throws SVNException {
                    DebugLog.log("OPEN ROOT: " + revision);
                }

                public void deleteEntry(String path, long revision) throws SVNException {
                    DebugLog.log("DELETE");
                }

                public void absentDir(String path) throws SVNException {
                }

                public void absentFile(String path) throws SVNException {
                }

                public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
                    DebugLog.log("ADD DIR");
                }

                public void openDir(String path, long revision) throws SVNException {
                    DebugLog.log("OPEN DIR");
                }

                public void changeDirProperty(String name, String value) throws SVNException {
                    DebugLog.log("CHANGE DIR PROPERTY: " + name + "=" + value);
                }

                public void closeDir() throws SVNException {
                    DebugLog.log("CLOSE DIR");
                }

                public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
                    DebugLog.log("ADD FILE");
                }

                public void openFile(String path, long revision) throws SVNException {
                    DebugLog.log("OPEN FILE: " + path);
                }

                public void applyTextDelta(String baseChecksum) throws SVNException {
                    DebugLog.log("APPLY DELTA");
                }

                public void changeFileProperty(String name, String value) throws SVNException {
                    DebugLog.log("CHANGE FILE PROPERTY: " + name + "=" + value);
                }

                public void closeFile(String textChecksum) throws SVNException {
                    DebugLog.log("CLOSE FILE");
                }

                public SVNCommitInfo closeEdit() throws SVNException {
                    DebugLog.log("CLOSE EDIT");
                    return null;
                }

                public void abortEdit() throws SVNException {
                    DebugLog.log("ABORT EDIT");
                }

                public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
                    DebugLog.log("RECORDING DELTA CHUNK ");
                    return new ByteArrayOutputStream();
                }

                public void textDeltaEnd() throws SVNException {
                }
                
            };
            
            String targetURL = url + "changelog.txt";
            repository.diff(targetURL, 23, "changelog.txt", false, false, reporterBaton, editor);
        } catch (SVNException e) {
            DebugLog.error(e);
        }
    }
}
