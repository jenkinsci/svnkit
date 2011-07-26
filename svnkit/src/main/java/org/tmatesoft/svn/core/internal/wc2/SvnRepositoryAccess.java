package org.tmatesoft.svn.core.internal.wc2;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public abstract class SvnRepositoryAccess {
    
    private SVNWCContext context;
    private SvnOperation operation;

    protected SvnRepositoryAccess(SvnOperation operation) throws SVNException {
        this.operation = operation;
        this.context = new SVNWCContext(getOperation().getOptions(), getOperation().getEventHandler());
    }
    
    protected SvnOperation getOperation() {
        return this.operation;
    }
    
    protected SVNWCContext getWCContext() {
        return this.context;
    }
    
    public enum RepositoryInfo {
        repository, revision, url;
    }
    
    public enum LocationsInfo {
        startUrl, startRevision, endUrl, endRevision;
    }
    
    public enum RevisionsPair {
        revNumber, youngestRevision;
    }
    
    public abstract Structure<RepositoryInfo> createRepositoryFor(SvnTarget target, SVNRevision revision, SVNRevision pegRevision, File baseDirectory) throws SVNException;
    
   
    public abstract Structure<RevisionsPair> getRevisionNumber(SVNRepository repository, SvnTarget path, SVNRevision revision, Structure<RevisionsPair> youngestRevision) throws SVNException;
    
    protected enum UrlInfo {
        url, pegRevision, dropRepsitory; 
    }
    
    protected abstract Structure<UrlInfo> getURLFromPath(SvnTarget path, SVNRevision revision, SVNRepository repository) throws SVNException;

    
    protected SVNRevision[] resolveRevisions(SVNRevision pegRevision, SVNRevision revision, boolean isURL, boolean noticeLocalModifications) {
        if (!pegRevision.isValid()) {
            if (isURL) {
                pegRevision = SVNRevision.HEAD;
            } else {
                if (noticeLocalModifications) {
                    pegRevision = SVNRevision.WORKING;
                } else {
                    pegRevision = SVNRevision.BASE;
                }
            }
        }
        if (!revision.isValid()) {
            revision = pegRevision;
        }
        return new SVNRevision[] {
                pegRevision, revision
        };
    }

    public SVNRepository createRepository(SVNURL url, String expectedUuid, boolean mayReuse) throws SVNException {
        SVNRepository repository = null;
        if (getOperation().getRepositoryPool() == null) {
            repository = SVNRepositoryFactory.create(url, null);
            repository.setAuthenticationManager(getOperation().getAuthenticationManager());
        } else {
            repository = getOperation().getRepositoryPool().createRepository(url, mayReuse);
        }
        if (expectedUuid != null) {
            String reposUUID = repository.getRepositoryUUID(true);
            if (!expectedUuid.equals(reposUUID)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_UUID_MISMATCH, "Repository UUID ''{0}'' doesn''t match expected UUID ''{1}''", new Object[] {
                        reposUUID, expectedUuid
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        repository.setCanceller(operation.getCanceller());
        return repository;
    }

    public Structure<LocationsInfo> getLocations(SVNRepository repository, SvnTarget path, SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION), SVNLogType.DEFAULT);
        }
        
        long pegRevisionNumber = -1;
        long startRevisionNumber;
        long endRevisionNumber;
        SVNURL url = null;
        
        if (path.isFile()) {
            Structure<UrlInfo> urlInfo = getURLFromPath(path, revision, repository);
            if (urlInfo.hasValue(UrlInfo.dropRepsitory) && urlInfo.is(UrlInfo.dropRepsitory)) {
                repository = null;
            }
            url = urlInfo.<SVNURL>get(UrlInfo.url);
            if (urlInfo.hasValue(UrlInfo.pegRevision)) {
                pegRevisionNumber = urlInfo.lng(UrlInfo.pegRevision);
            }
            urlInfo.release();
        } else {
            url = path.getURL();
        }
        
        if (repository == null) {
            repository = createRepository(url, null, true);
        }
    
        Structure<RevisionsPair> pair = null;
        if (pegRevisionNumber < 0) {
            pair = getRevisionNumber(repository, path, revision, pair);
            pegRevisionNumber = pair.lng(RevisionsPair.revNumber);
        }
        pair = getRevisionNumber(repository, path, start, pair);
        startRevisionNumber = pair.lng(RevisionsPair.revNumber);
        if (end == SVNRevision.UNDEFINED) {
            endRevisionNumber = startRevisionNumber;
        } else {
            pair = getRevisionNumber(repository, path, end, pair);
            endRevisionNumber = pair.lng(RevisionsPair.revNumber);
        }
        pair.release();
        
        Structure<LocationsInfo> result = Structure.obtain(LocationsInfo.class);
        result.set(LocationsInfo.startRevision, startRevisionNumber);
        if (end != SVNRevision.UNDEFINED) {
            result.set(LocationsInfo.startRevision, endRevisionNumber);
        }
        if (startRevisionNumber == pegRevisionNumber && endRevisionNumber == pegRevisionNumber) {
            result.set(LocationsInfo.startUrl, url);
            if (end != SVNRevision.UNDEFINED) {
                result.set(LocationsInfo.endUrl, url);
            }
            return result;
        }
        
        SVNURL repositoryRootURL = repository.getRepositoryRoot(true);
        long[] revisionsRange = startRevisionNumber == endRevisionNumber ? 
                new long[] {startRevisionNumber} : new long[] {startRevisionNumber, endRevisionNumber};
                
        Map<?,?> locations = null;
        try {
            locations = repository.getLocations("", (Map<?,?>) null, pegRevisionNumber, revisionsRange);
        } catch (SVNException e) {
            throw e;
        }
        
        SVNLocationEntry startPath = (SVNLocationEntry) locations.get(new Long(startRevisionNumber));
        SVNLocationEntry endPath = (SVNLocationEntry) locations.get(new Long(endRevisionNumber));
        if (startPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "Unable to find repository location for ''{0}'' in revision ''{1}''", new Object[] {
                    source, new Long(startRevisionNumber)
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (endPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "The location for ''{0}'' for revision {1} does not exist in the "
                    + "repository or refers to an unrelated object", new Object[] {
                    source, new Long(endRevisionNumber)
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        result.set(LocationsInfo.startUrl, repositoryRootURL.appendPath(startPath.getPath(), false));
        if (end.isValid()) {
            result.set(LocationsInfo.endUrl, repositoryRootURL.appendPath(endPath.getPath(), false));
        }
        return result;
    }
}
