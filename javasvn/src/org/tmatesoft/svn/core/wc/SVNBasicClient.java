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
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

public class SVNBasicClient implements ISVNEventListener {

    private ISVNRepositoryFactory myRepositoryFactory;
    private SVNOptions myOptions;
    private ISVNEventListener myEventDispatcher;
    private List myPathPrefixesStack;

    protected SVNBasicClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        myRepositoryFactory = repositoryFactory;
        myOptions = options;
        myEventDispatcher = eventDispatcher;
        myPathPrefixesStack = new LinkedList();
        if (myOptions == null)  {
            myOptions = new SVNOptions();
        }
        
    }
    
    protected SVNRepository createRepository(String url) throws SVNException {
        return myRepositoryFactory.createRepository(url);
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
            SVNRepository repository = myRepositoryFactory.createRepository(url);            
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
        SVNRepository repository = myRepositoryFactory.createRepository(url);            
        if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        }
        long number = repository.getLatestRevision();
        if (revision == SVNRevision.PREVIOUS) {
            number--;
        }
        return number;        
    }
    
    protected String getURL(String url, SVNRevision peg, SVNRevision rev) throws SVNException {
        if (peg.equals(rev) || !peg.isValid()) {
            return url;
        }
        SVNRepository repos = myRepositoryFactory.createRepository(url);
        
        long pegRevNumber = getRevisionNumber(url, peg);
        long revNumber = getRevisionNumber(url, rev);
        List locations = (List) repos.getLocations("", new ArrayList(1), pegRevNumber, new long[] {revNumber});
        if (locations == null || locations.size() != 1) {
            return url;
        }
        SVNLocationEntry location = (SVNLocationEntry) locations.get(0);
        String path = location.getPath();
        String rootPath = repos.getRepositoryRoot();
        String fullPath = SVNRepositoryLocation.parseURL(url).getPath();
        url = url.substring(0, url.length() - fullPath.length());
        url = PathUtil.append(url, rootPath);
        url = PathUtil.append(url, path);
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

}
