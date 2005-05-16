/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

class SVNReporter implements ISVNReporterBaton {

    private SVNWCAccess myWCAccess;
    private boolean myIsRecursive;

    public SVNReporter(SVNWCAccess wcAccess, boolean recursive) {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        // get entry from target
        SVNEntries targetEntries = myWCAccess.getTarget().getEntries();
        SVNEntries anchorEntries = myWCAccess.getAnchor().getEntries();
        SVNEntry targetEntry = targetEntries.getEntry(myWCAccess.getTargetName());
        
        if (targetEntry == null || targetEntry.isHidden() || 
                (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
            long revision = targetEntries.getEntry("").getRevision();
            reporter.setPath("", null, revision, false);
            reporter.deletePath("");
            reporter.finishReport();
            // dispose entries
            return;
        }
        long revision = targetEntry.getRevision();
        if (revision < 0) {
            revision = targetEntries.getEntry("").getRevision();
            if (revision < 0) {
                revision = anchorEntries.getEntry("").getRevision();
            }
        }
        reporter.setPath("", null, revision, false);
        boolean missing = !targetEntry.isScheduledForDeletion() &&  
            !myWCAccess.getTarget().getFile(myWCAccess.getTargetName()).exists();
        
        if (targetEntry.isDirectory()) {
            if (missing) {
                reporter.deletePath("");
            } else {
                // dispose everething and 
                // report children.

                // do report all entries from the dir, 
                // then open children dirs and report them. 
                reportEntries(reporter, myWCAccess.getTarget(), myWCAccess.getTargetName(), myIsRecursive);
            }
        } else if (targetEntry.isFile()){
            if (missing) {
                // restore file.
            }
            // report either linked path or entry path
            String url = targetEntry.getURL();
            SVNEntry parentEntry = targetEntries.getEntry("");
            String parentURL = parentEntry.getURL();
            String expectedURL = PathUtil.append(parentURL, PathUtil.encode(targetEntry.getName()));
            if (!expectedURL.equals(url)) {
                reporter.linkPath(SVNRepositoryLocation.parseURL(url), "", targetEntry.getLockToken(), targetEntry.getRevision(), false);
            } else if (targetEntry.getRevision() != parentEntry.getRevision() || targetEntry.getLockToken() != null) {
                reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), false);
            }            
        }
        reporter.finishReport();
        // dispose entries
    }
    
    private void reportEntries(ISVNReporter reporter, SVNDirectory directory, String dirPath, boolean recursive) throws SVNException {
        SVNEntries entries = directory.getEntries();
        long baseRevision = entries.getEntry("").getRevision();
        
        Map childDirectories = new HashMap();
        
        for (Iterator e = entries.entries(); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : PathUtil.append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                reporter.deletePath(path);
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }        
            File file = directory.getFile(entry.getName());
            boolean missing = !file.exists(); 
            if (entry.isFile()) {
                // check svn:special files -> symlinks that could be directory.
                if (file == null || file.isDirectory()) {
                    reporter.deletePath(path);
                    continue;
                }
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    // restore file.
                }
                String url = entry.getURL();
                String parentURL = entries.getPropertyValue("", SVNProperty.URL);
                String expectedURL = PathUtil.append(parentURL, PathUtil.encode(entry.getName()));
                if (!entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, entry.getLockToken(), entry.getRevision(), false);
                } else if (entry.getRevision() != baseRevision || entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                }
            } else if (entry.isDirectory() && recursive) {
                if (missing) {
                    reporter.deletePath(path);
                }
                if (file.isFile()) {
                    SVNErrorManager.error(3, null);
                }
                
                SVNDirectory childDir = directory.getChildDirectory(entry.getName());
                SVNEntry childEntry = childDir.getEntries().getEntry("");
                String url = childEntry.getURL();
                if (!url.equals(entry.getURL())) {
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, childEntry.getLockToken(), childEntry.getRevision(), false);
                } else if (childEntry.getLockToken() != null || childEntry.getRevision() != baseRevision) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), false);
                }
                
                // dispose child entries.
                childDirectories.put(path, childDir);
                childDir.dispose();
            }
        }
        for (Iterator dirs = childDirectories.keySet().iterator(); dirs.hasNext();) {
            String path = (String) dirs.next();
            SVNDirectory dir = (SVNDirectory) childDirectories.get(path);
            reportEntries(reporter, dir, path, recursive);
            dir.dispose();
        }
    }
    
    public static void main(String[] args) {
        ISVNReporter testReporter = new ISVNReporter() {
            public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                System.out.println("set path: '" + path + "' : " + revision);
            }
            public void deletePath(String path) throws SVNException {
                System.out.println("delete path: '" + path + "'");
            }
            public void linkPath(SVNRepositoryLocation repository, String path, String lockToken, long revision, boolean startEmtpy) throws SVNException {
                System.out.println("link path: '" + repository.toString() + "' => '" + path + "' : " + revision);
            }
            public void finishReport() throws SVNException {
                System.out.println("finish report");
            }
            public void abortReport() throws SVNException {
            }
        };
        
        System.out.println("free: " + Runtime.getRuntime().freeMemory()/1024 + " kb");
        try {
//            SVNWCAccess wcAccess = SVNWCAccess.create(new File("e:/subversion/subversion/libsvn_wc/x"));
            SVNWCAccess wcAccess = SVNWCAccess.create(new File("e:/i/prop/wc/dir"));
            System.out.println("anchor dir: " + wcAccess.getAnchor().getFile(""));
            System.out.println("target dir: " + wcAccess.getTarget().getFile(""));
            System.out.println("target: " + wcAccess.getTargetName());
            System.out.println();
            
            SVNReporter reporter = new SVNReporter(wcAccess, true);
            reporter.report(testReporter);
        } catch (SVNException e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("free: " + Runtime.getRuntime().freeMemory()/1024 + " kb");
        Runtime.getRuntime().gc();
        System.out.println("free: " + Runtime.getRuntime().freeMemory()/1024 + " kb");
    }
}
