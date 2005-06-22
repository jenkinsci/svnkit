/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNOptions;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SVNBasicClient implements ISVNEventListener {

    private ISVNRepositoryFactory myRepositoryFactory;
    private ISVNOptions myOptions;
    private ISVNEventListener myEventDispatcher;
    private List myPathPrefixesStack;
    private boolean myIsIgnoreExternals;
    private boolean myIsDoNotSleepForTimeStamp;
    private boolean myIsCommandRunning;

    protected SVNBasicClient() {
        this((ISVNCredentialsProvider) null, null, null);
    }

    protected SVNBasicClient(ISVNEventListener eventDispatcher) {
        this((ISVNCredentialsProvider) null, null, eventDispatcher);
    }

    protected SVNBasicClient(ISVNCredentialsProvider credentialsProvider) {
        this(credentialsProvider, null, null);
    }

    protected SVNBasicClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        this(credentialsProvider, null, eventDispatcher);
    }

    protected SVNBasicClient(final ISVNCredentialsProvider credentialsProvider, ISVNOptions options, ISVNEventListener eventDispatcher) {
        this(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
                if (repos != null && credentialsProvider != null) {
                    repos.setCredentialsProvider(credentialsProvider);
                }
                return repos;
            }
        }, options, eventDispatcher);
    }

    protected SVNBasicClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options, ISVNEventListener eventDispatcher) {
        myRepositoryFactory = repositoryFactory;
        myOptions = options;
        myEventDispatcher = eventDispatcher;
        myPathPrefixesStack = new LinkedList();
        if (myOptions == null)  {
            myOptions = new SVNOptions();
        }
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
        if (myRepositoryFactory == null) {
            return SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(PathUtil.decode(url)));
        }
        return myRepositoryFactory.createRepository(PathUtil.decode(url));
    }
    
    protected void dispatchEvent(SVNEvent event) {
        dispatchEvent(event, ISVNEventListener.UNKNOWN);
        
    }
    protected void dispatchEvent(SVNEvent event, double progress) {
        if (myEventDispatcher != null) {
            String path = "";
            if (!myPathPrefixesStack.isEmpty()) {
                for(Iterator paths = myPathPrefixesStack.iterator(); paths.hasNext();) {
                    String segment = (String) paths.next();
                    path = PathUtil.append(path, segment);
                }
            }
            if (path != null && !PathUtil.isEmpty(path)) {
                path = PathUtil.append(path, event.getPath());
                path = PathUtil.removeLeadingSlash(path);
                path = PathUtil.removeTrailingSlash(path);
                event.setPath(path);
            }
            myEventDispatcher.svnEvent(event, progress);
        }
    }

    protected void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null){
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventListener getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess(File file) throws SVNException {
        return createWCAccess(file, null);
    }
    
    protected SVNWCAccess createWCAccess(File file, final String pathPrefix) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        if (pathPrefix != null) {
            wcAccess.setEventDispatcher(new ISVNEventListener() {
                public void svnEvent(SVNEvent event, double progress) {
                    String fullPath = PathUtil.append(pathPrefix, event.getPath());
                    fullPath = PathUtil.removeTrailingSlash(fullPath);
                    fullPath = PathUtil.removeLeadingSlash(fullPath);
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

    protected long getRevisionNumber(File file, SVNRevision revision) throws SVNException {
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        }
        SVNWCAccess wcAccess = SVNWCAccess.create(file);

        if (revision.getDate() != null || revision == SVNRevision.HEAD) {
            String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            SVNRepository repository = createRepository(url);            
            return revision.getDate() != null ? 
                    repository.getDatedRevision(revision.getDate()) : repository.getLatestRevision();
        }
        
        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            String revStr = wcAccess.getTargetEntryProperty(SVNProperty.REVISION);
            return revStr != null ? Long.parseLong(revStr) : -1;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.PREVIOUS) {
            String revStr = wcAccess.getTargetEntryProperty(SVNProperty.COMMITTED_REVISION);
            long number = revStr != null ? Long.parseLong(revStr) : -1;
            if (revision == SVNRevision.PREVIOUS) {
                number--;
            }
            return number;            
        }
        return -1;
    }
    
    protected long getRevisionNumber(String url, SVNRevision revision) throws SVNException {
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
    
    public String getURL(String url, SVNRevision peg, SVNRevision rev) throws SVNException {
        if (rev == null || !rev.isValid()) {
            rev = SVNRevision.HEAD;
        }
        if (rev.equals(peg)) {
            return url;
        }        
        if (peg == null || !peg.isValid()) {
            return url;
        }
        DebugLog.log("creating repos for " + url);
        SVNRepository repos = createRepository(url);
        DebugLog.log("repos location " + repos.getLocation());
        long pegRevNumber = getRevisionNumber(url, peg);
        long revNumber = getRevisionNumber(url, rev);
        SVNNodeKind kind = repos.checkPath("", pegRevNumber);
        DebugLog.log("node kind: " + kind);
        List locations = new ArrayList(1);
        try {
            locations = (List) repos.getLocations("", locations, pegRevNumber, new long[] {revNumber});
            if (locations == null || locations.size() != 1) {
                SVNErrorManager.error("svn: Unable to find repository location for '" + url + "' in revision " + revNumber);
                return null;
            }
        } catch (SVNException e) {
            DebugLog.error(e);
            SVNErrorManager.error("svn: Unable to find repository location for '" + url + "' in revision " + revNumber);
        }
        SVNLocationEntry location = (SVNLocationEntry) locations.get(0);
        String path = PathUtil.encode(location.getPath());
        DebugLog.log("fetched path: " + path);
        String rootPath = repos.getRepositoryRoot();
        DebugLog.log("root path: " + rootPath);
        String fullPath = SVNRepositoryLocation.parseURL(PathUtil.decode(url)).getPath();
        DebugLog.log("full path: " + fullPath);
        url = url.substring(0, url.length() - fullPath.length());
        DebugLog.log("host: " + url);
        url = PathUtil.append(url, rootPath);
        url = PathUtil.append(url, path);
        DebugLog.log("fetched location: " + url);
        return url;
    }
    
    protected String validateURL(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public void svnEvent(SVNEvent event, double progress) {
        dispatchEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        if (myEventDispatcher != null) {
            myEventDispatcher.checkCancelled();
        }
    }

    protected SVNDirectory createVersionedDirectory(File dstPath, String url, String uuid, long revNumber) throws SVNException {
        SVNDirectory.createVersionedDirectory(dstPath);
        // add entry first.
        SVNDirectory dir = new SVNDirectory(null, "", dstPath);
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry("", true);
        if (entry == null) {
            entry = entries.addEntry("");
        }
        entry.setURL(url);
        entry.setUUID(uuid);
        entry.setKind(SVNNodeKind.DIR);
        entry.setRevision(revNumber);
        entry.setIncomplete(true);;
        entries.save(true);
        return dir;
    }

    protected SVNRepository createRepository(File path, SVNRevision pegRevision, SVNRevision revision, long[] actualRevision) throws SVNException {
        // get entry URL from path

        return null;
    }

    protected SVNRepository createRepository(File path, String url, SVNRevision pegRevision, SVNRevision revision, long[] actualRevision) throws SVNException {
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        // defaults to HEAD.
        if (path == null) {
            revision = !revision.isValid() ? SVNRevision.HEAD : revision; // start (?)
            pegRevision = !pegRevision.isValid() ? SVNRevision.HEAD : pegRevision; // peg
        } else {
            revision = !revision.isValid() ? SVNRevision.WORKING : revision; // start (?)
            pegRevision = !pegRevision.isValid() ? SVNRevision.BASE : pegRevision; // peg
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
        String rootPath = repos.getRepositoryRoot(true);
        List locations = (List) repos.getLocations("", new ArrayList(2), pegRevision, startRev == endRev ? new long[] {startRev} : new long[] {startRev, endRev});
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
            SVNErrorManager.error("svn: Unable to find repository location for '" + path != null ? path.toString() : url + "' in revision " + startRev);
            return null;
        }
        if (endLocation == null) {
            SVNErrorManager.error("svn: The location for '" + path != null ? path.toString() : url + "' for revision " + endRev  + " does not exist in repository" +
                " or refers to an unrelated object");
            return null;
        }
        String host = url.substring(0, url.indexOf('/', url.indexOf("://") + 3));
        String startPath = host + PathUtil.encode(PathUtil.append(rootPath, startLocation.getPath()));
        String endPath = host + PathUtil.encode(PathUtil.append(rootPath,  endLocation.getPath()));
        return new RepositoryReference[] {new RepositoryReference(startPath, startRev), new RepositoryReference(endPath, endRev)};
    }

    protected SVNEntry getEntry(File path) throws SVNException {
        SVNEntry entry;
        SVNWCAccess wcAccess = createWCAccess(path);
        if (wcAccess.getTarget() != wcAccess.getAnchor()) {
            entry = wcAccess.getTarget().getEntries().getEntry("", false);
        } else {
            entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false);
        }
        return entry;
    }

    protected long getRevisionNumber(File path, String url, SVNRepository repos, SVNRevision revision) throws SVNException {
        if (repos == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            if (url == null) {
                SVNErrorManager.error("svn: getRevisionNumber needs valid URL to fetch revision number for '" + revision + "'");
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
        } else if (revision == SVNRevision.COMMITTED ||
                    revision == SVNRevision.BASE ||
                    revision == SVNRevision.PREVIOUS ||
                    revision == SVNRevision.WORKING) {
            if (path == null) {
                SVNErrorManager.error("svn: getRevisionNumber needs valid Path to fetch revision number for '" + revision + "'");
            }
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry;
            if (wcAccess.getTarget() != wcAccess.getAnchor()) {
                entry = wcAccess.getTarget().getEntries().getEntry("", false);
            } else {
                entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false);
            }
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
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
            SVNErrorManager.error("svn: Unrecognized revision type requested for '" + revision + "'");
        }
        return -1;
    }

    protected static class RepositoryReference {

        public RepositoryReference(String url, long rev) {
            URL = url;
            Revision = rev;
        }

        public String URL;
        public long Revision;
    }

}