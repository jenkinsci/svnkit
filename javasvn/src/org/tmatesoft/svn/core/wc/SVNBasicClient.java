/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNBasicClient implements ISVNEventHandler {

    private ISVNRepositoryFactory myRepositoryFactory;
    private ISVNOptions myOptions;
    private ISVNEventHandler myEventDispatcher;
    private List myPathPrefixesStack;
    private boolean myIsIgnoreExternals;
    private boolean myIsDoNotSleepForTimeStamp;
    private boolean myIsCommandRunning;
    private boolean myIsLeaveConflictsUnresolved;

    protected SVNBasicClient(final ISVNAuthenticationManager authManager, ISVNOptions options) {
        this(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(SVNURL url) throws SVNException {
                SVNRepository repository = SVNRepositoryFactory.create(url);
                repository.setAuthenticationManager(authManager == null ? 
                        SVNWCUtil.createDefaultAuthenticationManager() : authManager);
                return repository;
            }
            
        }, options);
    }

    protected SVNBasicClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        myRepositoryFactory = repositoryFactory;
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        myPathPrefixesStack = new LinkedList();
    }

    public ISVNOptions getOptions() {
        return myOptions;
    }

    public void setIgnoreExternals(boolean ignore) {
        myIsIgnoreExternals = ignore;
    }

    public boolean isIgnoreExternals() {
        return myIsIgnoreExternals;
    }
    
    public void setLeaveConflictsUnresolved(boolean leave) {
        myIsLeaveConflictsUnresolved = leave;
    }
    
    public boolean isLeaveConflictsUnresolved() {
        return myIsLeaveConflictsUnresolved;
    }

    public void setEventHandler(ISVNEventHandler dispatcher) {
        myEventDispatcher = dispatcher;
    }

    public void runCommand(ISVNRunnable command) throws SVNException {
        try {
            myIsCommandRunning = true;
            command.run();
        } finally {
            myIsCommandRunning = false;
            SVNFileUtil.sleepForTimestamp();
        }
    }

    protected void setDoNotSleepForTimeStamp(boolean doNotSleep) {
        myIsDoNotSleepForTimeStamp = doNotSleep;
    }

    protected boolean isCommandRunning() {
        return myIsCommandRunning;
    }

    protected boolean isDoNotSleepForTimeStamp() {
        return isCommandRunning() || myIsDoNotSleepForTimeStamp;
    }

    protected SVNRepository createRepository(String url) throws SVNException {
        SVNURL svnURL = SVNURL.parseURIEncoded(url);
        if (myRepositoryFactory == null) {
            return SVNRepositoryFactory.create(svnURL);
        }
        return myRepositoryFactory.createRepository(svnURL);
    }

    protected SVNRepository createRepository(SVNURL url) throws SVNException {
        if (myRepositoryFactory == null) {
            return SVNRepositoryFactory.create(url);
        }
        return myRepositoryFactory.createRepository(url);
    }
    
    protected ISVNRepositoryFactory getRepositoryFactory() {
        return myRepositoryFactory;
    }

    protected void dispatchEvent(SVNEvent event) {
        dispatchEvent(event, ISVNEventHandler.UNKNOWN);

    }

    protected void dispatchEvent(SVNEvent event, double progress) {
        if (myEventDispatcher != null) {
            String path = "";
            if (!myPathPrefixesStack.isEmpty()) {
                for (Iterator paths = myPathPrefixesStack.iterator(); paths
                        .hasNext();) {
                    String segment = (String) paths.next();
                    path = SVNPathUtil.append(path, segment);
                }
            }
            if (path != null && !"".equals(path)) {
                path = SVNPathUtil.append(path, event.getPath());
                event.setPath(path);
            }
            myEventDispatcher.handleEvent(event, progress);
        }
    }

    protected void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null) {
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventHandler getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess(File file) throws SVNException {
        return createWCAccess(file, null);
    }

    protected SVNWCAccess createWCAccess(File file, final String pathPrefix)
            throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        if (pathPrefix != null) {
            wcAccess.setEventDispatcher(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) {
                    String fullPath = SVNPathUtil.append(pathPrefix, event.getPath());
                    event.setPath(fullPath);
                    dispatchEvent(event, progress);
                }

                public void checkCancelled() throws SVNCancelException {
                    SVNBasicClient.this.checkCancelled();
                }
            });
        } else {
            wcAccess.setEventDispatcher(this);
        }
        wcAccess.setOptions(myOptions);
        return wcAccess;
    }

    protected long getRevisionNumber(File file, SVNRevision revision)
            throws SVNException {
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        }
        SVNWCAccess wcAccess = SVNWCAccess.create(file);

        if (revision.getDate() != null || revision == SVNRevision.HEAD) {
            String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            SVNRepository repository = createRepository(url);
            return revision.getDate() != null ? repository
                    .getDatedRevision(revision.getDate()) : repository
                    .getLatestRevision();
        }

        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            String revStr = wcAccess
                    .getTargetEntryProperty(SVNProperty.REVISION);
            return revStr != null ? Long.parseLong(revStr) : -1;
        } else if (revision == SVNRevision.COMMITTED
                || revision == SVNRevision.PREVIOUS) {
            String revStr = wcAccess
                    .getTargetEntryProperty(SVNProperty.COMMITTED_REVISION);
            long number = revStr != null ? Long.parseLong(revStr) : -1;
            if (revision == SVNRevision.PREVIOUS) {
                number--;
            }
            return number;
        }
        return -1;
    }

    protected long getRevisionNumber(String url, SVNRevision revision)
            throws SVNException {
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        }
        SVNRepository repository = createRepository(url);
        if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        }
        long number = repository.getLatestRevision();
        if (revision == SVNRevision.PREVIOUS) {
            number--;
        }
        return number;
    }

    public String getURL(String url, SVNRevision peg, SVNRevision rev)
            throws SVNException {
        if (rev == null || !rev.isValid()) {
            rev = SVNRevision.HEAD;
        }
        if (rev.equals(peg)) {
            return url;
        }
        if (peg == null || !peg.isValid()) {
            return url;
        }
        SVNRepository repos = createRepository(url);
        long pegRevNumber = getRevisionNumber(url, peg);
        long revNumber = getRevisionNumber(url, rev);
        List locations = new ArrayList(1);
        try {
            locations = (List) repos.getLocations("", locations, pegRevNumber,
                    new long[] { revNumber });
        } catch (SVNException e) {
            SVNDebugLog.log(e);
            SVNErrorManager
                    .error("svn: Unable to find repository location for '"
                            + url + "' in revision " + revNumber);
            return null;
        }
        if (locations == null || locations.size() != 1) {
            SVNErrorManager
                    .error("svn: Unable to find repository location for '"
                            + url + "' in revision " + revNumber);
            return null;
        }
        SVNLocationEntry location = (SVNLocationEntry) locations.get(0);
        String path = SVNEncodingUtil.uriEncode(location.getPath());
        String rootPath = SVNEncodingUtil.uriEncode(repos.getRepositoryRoot().getPath());
        String fullPath = SVNEncodingUtil.uriEncode(SVNURL.parseURIEncoded(url).getPath());
        url = url.substring(0, url.length() - fullPath.length());
        url = SVNPathUtil.append(url, rootPath);
        url = SVNPathUtil.append(url, path);
        return url;
    }

    protected String validateURL(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public void handleEvent(SVNEvent event, double progress) {
        dispatchEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        if (myEventDispatcher != null) {
            myEventDispatcher.checkCancelled();
        }
    }

    protected SVNDirectory createVersionedDirectory(File dstPath, SVNURL url, String uuid, long revNumber) throws SVNException {
        SVNDirectory.createVersionedDirectory(dstPath);
        // add entry first.
        SVNDirectory dir = new SVNDirectory(null, "", dstPath);
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry("", true);
        if (entry == null) {
            entry = entries.addEntry("");
        }
        entry.setURL(url.toString());
        entry.setUUID(uuid);
        entry.setKind(SVNNodeKind.DIR);
        entry.setRevision(revNumber);
        entry.setIncomplete(true);

        entries.save(true);
        return dir;
    }

    protected SVNRepository createRepository(File path, String url, SVNRevision pegRevision, SVNRevision revision, long[] actualRevision) throws SVNException {
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        // defaults to HEAD.
        if (path == null) {
            revision = !revision.isValid() ? SVNRevision.HEAD : revision; 
            pegRevision = !pegRevision.isValid() ? SVNRevision.HEAD : pegRevision;
        } else {
            revision = !revision.isValid() ? SVNRevision.WORKING : revision;
            pegRevision = !pegRevision.isValid() ? SVNRevision.BASE : pegRevision;
        }

        // get locations of 'url' (or path) in revision 'revision'.
        RepositoryReference[] range = getURLRange(path, url, pegRevision, revision, SVNRevision.UNDEFINED);
        long realRevision = range[0].Revision;
        url = range[0].URL;

        SVNRepository repos = createRepository(url);
        if (actualRevision != null && actualRevision.length > 0) {
            if (realRevision < 0) {
                realRevision = repos.getLatestRevision();
            }
            actualRevision[0] = realRevision;
        }
        return repos;
    }

    protected RepositoryReference[] getURLRange(File path, String url, SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error("svn: bad revision for getURL2");
        }
        long pegRevision = -1;
        if (path != null) {
            SVNEntry entry = getEntry(path);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
                return null;
            }
            if (entry.getCopyFromURL() != null && revision == SVNRevision.WORKING) {
                url = entry.getCopyFromURL();
                pegRevision = entry.getCopyFromRevision();
            } else if (entry.getURL() != null) {
                url = entry.getURL();
            } else if (url == null) {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
        }
        SVNRepository repos = createRepository(url);
        long startRev = getRevisionNumber(path, url, repos, start);
        long endRev = startRev;
        if (end.isValid()) {
            endRev = getRevisionNumber(path, url, repos, end);
        }
        if (pegRevision < 0) {
            pegRevision = getRevisionNumber(path, url, repos, revision);
        }
        String rootPath = repos.getRepositoryRoot(true).getPath();
        List locations = (List) repos.getLocations("", new ArrayList(2), pegRevision, 
                startRev == endRev ? new long[] { startRev } : new long[] { startRev, endRev });
        SVNLocationEntry endLocation = null;
        SVNLocationEntry startLocation = null;
        if (locations != null && startRev == endRev && locations.size() == 1) {
            startLocation = (SVNLocationEntry) locations.get(0);
            endLocation = (SVNLocationEntry) locations.get(0);
        } else if (locations != null && startRev != endRev && locations.size() == 2) {
            startLocation = (SVNLocationEntry) locations.get(0);
            endLocation = (SVNLocationEntry) locations.get(1);
            if (startLocation.getRevision() != startRev) {
                SVNLocationEntry tmp = startLocation;
                startLocation = endLocation;
                endLocation = tmp;
            }
        }
        if (startLocation == null) {
            SVNErrorManager
                    .error("svn: Unable to find repository location for '"
                            + (path != null ? path.toString() : url)
                            + "' in revision " + startRev);
            return null;
        }
        if (endLocation == null) {
            SVNErrorManager.error("svn: The location for '"
                    + (path != null ? path.toString() : url)
                    + "' for revision " + endRev
                    + " does not exist in repository"
                    + " or refers to an unrelated object");
            return null;
        }
        String host = url.substring(0, url.indexOf('/', url.indexOf("://") + 3));
        String startPath = host
                + SVNEncodingUtil.uriEncode(SVNPathUtil.append(rootPath, startLocation.getPath()));
        String endPath = host
                + SVNEncodingUtil.uriEncode(SVNPathUtil.append(rootPath, endLocation.getPath()));
        return new RepositoryReference[] {
                new RepositoryReference(startPath, startRev),
                new RepositoryReference(endPath, endRev) };
    }

    protected SVNEntry getEntry(File path) throws SVNException {
        SVNEntry entry;
        SVNWCAccess wcAccess = createWCAccess(path);
        if (wcAccess.getTarget() != wcAccess.getAnchor()) {
            entry = wcAccess.getTarget().getEntries().getEntry("", false);
        } else {
            entry = wcAccess.getAnchor().getEntries().getEntry(
                    wcAccess.getTargetName(), false);
        }
        return entry;
    }

    protected long getRevisionNumber(File path, String url,
            SVNRepository repos, SVNRevision revision) throws SVNException {
        if (repos == null
                && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            if (url == null) {
                SVNErrorManager
                        .error("svn: getRevisionNumber needs valid URL to fetch revision number for '"
                                + revision + "'");
                return -1;
            }
            repos = createRepository(url);
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (repos != null && revision.getDate() != null) {
            return repos.getDatedRevision(revision.getDate());
        } else if (repos != null && revision == SVNRevision.HEAD) {
            return repos.getLatestRevision();
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.COMMITTED
                || revision == SVNRevision.BASE
                || revision == SVNRevision.PREVIOUS
                || revision == SVNRevision.WORKING) {
            if (path == null) {
                SVNErrorManager
                        .error("svn: getRevisionNumber needs valid Path to fetch revision number for '"
                                + revision + "'");
            }
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry;
            if (wcAccess.getTarget() != wcAccess.getAnchor()) {
                entry = wcAccess.getTarget().getEntries().getEntry("", false);
            } else {
                entry = wcAccess.getAnchor().getEntries().getEntry(
                        wcAccess.getTargetName(), false);
            }
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path
                        + "' is not under version control");
                return -1;
            }
            long rev;
            if (revision == SVNRevision.BASE || revision == SVNRevision.WORKING) {
                rev = entry.getRevision();
            } else {
                rev = entry.getCommittedRevision();
                if (revision == SVNRevision.PREVIOUS) {
                    rev--;
                }
            }
            return rev;
        } else {
            SVNErrorManager
                    .error("svn: Unrecognized revision type requested for '"
                            + revision + "'");
        }
        return -1;
    }
    
    protected long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) throws SVNException {
        if (repository == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            SVNErrorManager.error("svn: RA access is required to get revision number for '" + revision + "'");
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        } else if (revision == SVNRevision.HEAD) {
            return repository.getLatestRevision();
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.WORKING || 
                revision == SVNRevision.BASE || revision == SVNRevision.PREVIOUS) {
            if (path == null) {
                SVNErrorManager.error("svn: Path is required to get revision number for '" + revision + "'");
            }
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
            }
            if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
                return entry.getRevision();
            } else {
                return revision == SVNRevision.PREVIOUS ? entry.getCommittedRevision() - 1 : entry.getCommittedRevision();
            }
        } else {
            SVNErrorManager.error("svn: Unrecognized revision type requested for '" + path + "'");
        }
        return -1;
    }
    
    // SVNRepository will contain actual URL as location and actual revision somewhere (so location is not only url, but url+revision). 
    protected SVNRepository createRepository(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        if (url == null) {
            url = getURL(path);
        }
        if (url == null) {
            SVNErrorManager.error("svn: '" + path + "' has no URL");
        }
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        if (path == null) {
            if (!revision.isValid()) {
                revision = SVNRevision.HEAD;
            } 
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.HEAD;
            } 
        } else {
            if (!revision.isValid()) {
                revision = SVNRevision.BASE;
            } 
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.WORKING;
            } 
        }
        
        SVNRepositoryLocation[] locations = getLocations(url, path, pegRevision, revision, SVNRevision.UNDEFINED);
        url = locations[0].getURL();
        long actualRevision = locations[0].getRevisionNumber();
        SVNRepository repository = createRepository(url);
        actualRevision = getRevisionNumber(SVNRevision.create(actualRevision), repository, path);
        if (actualRevision < 0) {
            actualRevision = repository.getLatestRevision();
        }
        repository.setPegRevision(actualRevision);
        return repository;
    }
    
    protected SVNRepositoryLocation[] getLocations(SVNURL url, File path, SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error("svn: Bad revision '" + revision + "' or '" + start + "'");
        }
        long pegRevisionNumber = -1;
        long startRevisionNumber;
        long endRevisionNumber;
        
        if (path != null) {
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry.getCopyFromURL() != null && revision == SVNRevision.WORKING) {
                url = entry.getCopyFromSVNURL();
                pegRevisionNumber = entry.getCopyFromRevision();
            } else if (entry.getURL() != null){
                url = entry.getSVNURL();
            } else {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
        }
        SVNRepository repository = createRepository(url);
        if (pegRevisionNumber < 0) {
            pegRevisionNumber = getRevisionNumber(revision, repository, path);
        }
        startRevisionNumber = getRevisionNumber(start, repository, path);
        if (!end.isValid()) {
            endRevisionNumber = startRevisionNumber;
        } else {
            endRevisionNumber = getRevisionNumber(end, repository, path);
        }
        SVNURL rootURL = repository.getRepositoryRoot(true);
        long[] revisionsRange = startRevisionNumber == endRevisionNumber ? 
                new long[] {startRevisionNumber} : new long[] {startRevisionNumber, endRevisionNumber};
                        
        Map locations = repository.getLocations("", (Map) null, pegRevisionNumber, revisionsRange);
        SVNLocationEntry startPath = (SVNLocationEntry) locations.get(new Long(startRevisionNumber));
        SVNLocationEntry endPath = (SVNLocationEntry) locations.get(new Long(endRevisionNumber));
        
        if (startPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorManager.error("svn: Unable to find repository location for '" + source + "' in revision " + startRevisionNumber);
        }
        if (endPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorManager.error("The location for '" + source + "' for revision " + endRevisionNumber +" does not exist in the " +
                                    "repository or refers to an unrelated object");            
        }
        
        SVNRepositoryLocation[] result = new SVNRepositoryLocation[2];
        SVNURL startURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(startPath.getPath())));
        result[0] = new SVNRepositoryLocation(startURL, startRevisionNumber);
        if (end.isValid()) {
            SVNURL endURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(endPath.getPath())));
            result[1] = new SVNRepositoryLocation(endURL, endRevisionNumber);
        }
        return result;
    }
    
    protected SVNURL getURL(File path) throws SVNException {
        if (path == null) {
            return null;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        SVNEntry entry;
        if (wcAccess.getTarget() != wcAccess.getAnchor()) {
            entry = wcAccess.getTarget().getEntries().getEntry("", false);
        } else {
            entry = wcAccess.getTargetEntry();
        }
        return entry != null ? entry.getSVNURL() : null;        
    }
    

    protected static class RepositoryReference {

        public RepositoryReference(String url, long rev) {
            URL = url;
            Revision = rev;
        }

        public String URL;

        public long Revision;
    }

    protected static class SVNRepositoryLocation {

        private SVNURL myURL;
        private long myRevision;

        public SVNRepositoryLocation(SVNURL url, long rev) {
            myURL = url;
            myRevision = rev;
        }
        public long getRevisionNumber() {
            return myRevision;
        }
        public SVNURL getURL() {
            return myURL;
        }
    }

}