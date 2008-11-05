/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class DAVResource {

    public static final long INVALID_REVISION = SVNRepository.INVALID_REVISION;

    public static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=\"utf-8\"";
    public static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    protected DAVResourceURI myResourceURI;

    protected FSRepository myRepository;
    protected long myRevision;
    protected long myLatestRevision = INVALID_REVISION;

    protected boolean myIsExists;
    protected boolean myIsCollection;
    protected boolean myIsSVNClient;
    protected boolean myIsAutoCheckedOut;
    protected boolean myIsVersioned;
    protected boolean myIsWorking;
    protected boolean myIsBaseLined;
    protected String myDeltaBase;
    protected long myVersion;
    protected String myClientOptions;
    protected String myBaseChecksum;
    protected String myResultChecksum;
    protected String myUserName;
    protected SVNProperties mySVNProperties;
    protected Collection myDeadProperties;
    protected Collection myEntries;
    protected File myActivitiesDB;
    protected FSFS myFSFS;
    protected String myTxnName;
    protected FSRoot myRoot;
    protected FSTransactionInfo myTxnInfo;
    
    /**
     * DAVResource  constructor
     *
     * @param repository   repository resource connect to
     * @param context      contains requested url requestContext and name of repository if servlet use SVNParentPath directive.
     * @param uri          special uri for DAV requests can be /path or /SPECIAL_URI/xxx/path
     * @param label        request's label header
     * @param useCheckedIn special case for VCC resource
     * @throws SVNException if an error occurs while fetching repository properties.
     */
    protected DAVResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        myRepository = (FSRepository) repository;
        myFSFS = myRepository.getFSFS();
        myResourceURI = resourceURI;
        myIsSVNClient = isSVNClient;
        myDeltaBase = deltaBase;
        myVersion = version;
        myClientOptions = clientOptions;
        myBaseChecksum = baseChecksum;
        myResultChecksum = resultChecksum;
        myRevision = resourceURI.getRevision();
        myIsExists = resourceURI.exists();
        myIsVersioned = resourceURI.isVersioned();
        myIsWorking = resourceURI.isWorking();
        myIsBaseLined = resourceURI.isBaseLined();
        myUserName = userName;
        myActivitiesDB = activitiesDB;
        prepare();
    }

    protected DAVResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        myResourceURI = resourceURI;
        myRepository = (FSRepository) repository;
        myFSFS = myRepository.getFSFS();
        myRevision = revision;
        myIsSVNClient = isSVNClient;
        myDeltaBase = deltaBase;
        myVersion = version;
        myClientOptions = clientOptions;
        myBaseChecksum = baseChecksum;
        myResultChecksum = resultChecksum;
        myIsVersioned = myResourceURI.isVersioned();
        myIsWorking = myResourceURI.isWorking();
        myIsExists = myResourceURI.exists();
        myIsBaseLined = myResourceURI.isBaseLined();
        myUserName = userName;
        myActivitiesDB = activitiesDB;
    }

    public void setRoot(FSRoot root) {
        myRoot = root;
    }

    public FSRoot getRoot() {
        return myRoot;
    }
    
    public FSTransactionInfo getTxnInfo() {
        return myTxnInfo;
    }
    
    public void setTxnInfo(FSTransactionInfo txnInfo) {
        myTxnInfo = txnInfo;
    }

    public static boolean isValidRevision(long revision) {
        return revision >= 0;
    }

    public DAVResourceURI getResourceURI() {
        return myResourceURI;
    }

    public SVNRepository getRepository() {
        return myRepository;
    }

    public long getRevision() {
        return myRevision;
    }

    public boolean exists() {
        return myIsExists;
    }

    public boolean isVersioned() {
        return myIsVersioned;
    }
    
    public boolean isWorking() {
        return myIsWorking;
    }
    
    public boolean isBaseLined() {
        return myIsBaseLined;
    }
    
    public DAVResourceType getType() {
        return getResourceURI().getType();
    }

    public boolean canBeActivity() {
        return isAutoCheckedOut() || (getType() == DAVResourceType.ACTIVITY && !exists());
    }
    
    public boolean isCollection() {
        return myIsCollection;
    }

    public boolean isSVNClient() {
        return myIsSVNClient;
    }

    public String getUserName() {
        return myUserName;
    }

    public String getDeltaBase() {
        return myDeltaBase;
    }

    public long getVersion() {
        return myVersion;
    }

    public String getClientOptions() {
        return myClientOptions;
    }

    public String getBaseChecksum() {
        return myBaseChecksum;
    }

    public String getResultChecksum() {
        return myResultChecksum;
    }

    public File getActivitiesDB() {
        return myActivitiesDB;
    }

    public Iterator getChildren() throws SVNException {
        return new Iterator() {
            Iterator entriesIterator = getEntries().iterator();

            public void remove() {
            }

            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            public Object next() {
                SVNDirEntry entry = (SVNDirEntry) entriesIterator.next();
                String childURI = DAVPathUtil.append(getResourceURI().getURI(), entry.getName());
                try {
                    DAVResourceURI newResourceURI = new DAVResourceURI(getResourceURI().getContext(), childURI, null, false);
                    return DAVResourceFactory.createDAVResourceChild(getRepository(), newResourceURI, getRevision(), isSVNClient(), getDeltaBase(), 
                            getVersion(), getClientOptions(), null, null, getUserName(), getActivitiesDB());
                } catch (SVNException e) {
                    return null;
                }
            }
        };
    }

    public Collection getEntries() throws SVNException {
        if (isCollection() && myEntries == null) {
            myEntries = new LinkedList();
            getRepository().getDir(getResourceURI().getPath(), getRevision(), null, SVNDirEntry.DIRENT_KIND, myEntries);
        }
        return myEntries;
    }

    public long getCreatedRevision() throws SVNException {
        String revisionParameter = getProperty(SVNProperty.COMMITTED_REVISION);
        try {
            return Long.parseLong(revisionParameter);
        } catch (NumberFormatException e) {
            return getRevision();
        }
    }

    public long getCreatedRevision(String path, long revision) throws SVNException {
        if (path == null) {
            return INVALID_REVISION;
        } else if (path.equals(getResourceURI().getPath())) {
            return getCreatedRevision();
        } else {
            SVNDirEntry currentEntry = getRepository().getDir(path, revision, false, null);
            return currentEntry.getRevision();
        }

    }

    public Date getLastModified() throws SVNException {
        if (lacksETagPotential()) {
            return null;
        }
        return getRevisionDate(getCreatedRevision());
    }

    public Date getRevisionDate(long revision) throws SVNException {
        return SVNDate.parseDate(getRevisionProperty(revision, SVNRevisionProperty.DATE));
    }

    public String getETag() throws SVNException {
        if (lacksETagPotential()) {
            return null;
        }
        StringBuffer eTag = new StringBuffer();
        eTag.append(isCollection() ? "W/" : "");
        eTag.append("\"");
        eTag.append(getCreatedRevision());
        eTag.append("/");
        eTag.append(SVNEncodingUtil.uriEncode(getResourceURI().getPath()));
        eTag.append("\"");
        return eTag.toString();
    }

    public String getRepositoryUUID(boolean forceConnect) throws SVNException {
        return getRepository().getRepositoryUUID(forceConnect);
    }

    public String getContentType() throws SVNException {
        if (getResourceURI().isBaseLined() && getResourceURI().getType() == DAVResourceType.VERSION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (getResourceURI().getType() == DAVResourceType.PRIVATE && getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (isCollection()) {
            return DEFAULT_COLLECTION_CONTENT_TYPE;
        }
        String contentType = getProperty(SVNProperty.MIME_TYPE);
        if (contentType != null) {
            return contentType;
        }
        return DEFAULT_FILE_CONTENT_TYPE;
    }

    public long getLatestRevision() throws SVNException {
        if (!isValidRevision(myLatestRevision)) {
            myLatestRevision = getRepository().getLatestRevision();
        }
        return myLatestRevision;
    }

    public long getContentLength() throws SVNException {
        SVNDirEntry entry = getRepository().getDir(getResourceURI().getPath(), getRevision(), false, null);
        return entry.getSize();
    }

    public SVNLock[] getLocks() throws SVNException {
        if (getResourceURI().getPath() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "get-locks-report run on resource which doesn't represent a path within a repository."), SVNLogType.NETWORK);
        }
        return getRepository().getLocks(getResourceURI().getPath());
    }

    public String getAuthor(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.AUTHOR);
    }

    public String getMD5Checksum() throws SVNException {
        return getProperty(SVNProperty.CHECKSUM);
    }

    public Collection getDeadProperties() throws SVNException {
        if (myDeadProperties == null) {
            myDeadProperties = new ArrayList();
            for (Iterator iterator = getSVNProperties().nameSet().iterator(); iterator.hasNext();) {
                String propertyName = (String) iterator.next();
                if (SVNProperty.isRegularProperty(propertyName)) {
                    myDeadProperties.add(propertyName);
                }
            }
        }
        return myDeadProperties;
    }

    public String getLog(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.LOG);
    }

    public String getProperty(String propertyName) throws SVNException {
        return getSVNProperties().getStringValue(propertyName);
    }

    public String getRevisionProperty(long revision, String propertyName) throws SVNException {
        SVNPropertyValue value = getRepository().getRevisionPropertyValue(revision, propertyName);
        return value == null ? null : value.getString();
    }

    public void writeTo(OutputStream out) throws SVNException {
        if (isCollection()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED), SVNLogType.NETWORK);
        }
        getRepository().getFile(getResourceURI().getPath(), getRevision(), null, out);
    }

    public boolean isAutoCheckedOut() {
        return myIsAutoCheckedOut;
    }

    public void setIsAutoCkeckedOut(boolean isAutoCheckedOut) {
        myIsAutoCheckedOut = isAutoCheckedOut;
    }
    
    public String getTxnName() {
        return myTxnName;
    }

    public void setExists(boolean exists) {
        myIsExists = exists;
    }
    
    public void setTxnName(String txnName) {
        myTxnName = txnName;
    }
    
    protected abstract void prepare() throws DAVException;

    protected String getTxn() {
        DAVResourceURI resourceURI = getResourceURI();
        DAVServletUtil.getTxn(getActivitiesDB(), resourceURI.getActivityID());
        File activityFile = DAVPathUtil.getActivityPath(getActivitiesDB(), resourceURI.getActivityID());
        try {
            return DAVServletUtil.readTxn(activityFile);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e.getMessage());
        }
        return null;
    }

    private SVNProperties getSVNProperties() throws SVNException {
        if (mySVNProperties == null) {
            mySVNProperties = new SVNProperties();
            if (getResourceURI().getType() == DAVResourceType.REGULAR) {
                if (isCollection()) {
                    getRepository().getDir(getResourceURI().getPath(), getRevision(), mySVNProperties, (ISVNDirEntryHandler) null);
                } else {
                    getRepository().getFile(getResourceURI().getPath(), getRevision(), mySVNProperties, null);
                }
            }
        }
        return mySVNProperties;
    }

    protected void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
    }

    private boolean lacksETagPotential() {
        return (!exists() || (getResourceURI().getType() != DAVResourceType.REGULAR && getResourceURI().getType() != DAVResourceType.VERSION)
                || getResourceURI().getType() == DAVResourceType.VERSION && getResourceURI().isBaseLined());
    }

}
