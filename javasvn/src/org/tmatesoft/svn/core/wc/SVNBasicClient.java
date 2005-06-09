/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

public class SVNBasicClient implements ISVNEventListener {

    private ISVNRepositoryFactory myRepositoryFactory;
    private SVNOptions myOptions;
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

    protected SVNBasicClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
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

    protected SVNBasicClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        myRepositoryFactory = repositoryFactory;
        myOptions = options;
        myEventDispatcher = eventDispatcher;
        myPathPrefixesStack = new LinkedList();
        if (myOptions == null)  {
            myOptions = new SVNOptions();
        }
    }
    
    public SVNOptions getOptions() {
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
        SVNEntry entry = entries.getEntry("");
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
}