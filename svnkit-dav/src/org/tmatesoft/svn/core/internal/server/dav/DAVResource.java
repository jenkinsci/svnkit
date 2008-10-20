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
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
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
public class DAVResource {

    public static final long INVALID_REVISION = SVNRepository.INVALID_REVISION;

    public static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=\"utf-8\"";
    public static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    private DAVResourceURI myResourceURI;

    private FSRepository myRepository;
    private long myRevision;
    private long myLatestRevision = INVALID_REVISION;

    private boolean myIsExists;
    private boolean myIsCollection;
    private boolean myIsSVNClient;
    private boolean myChecked;
    private boolean myIsAutoCheckedOut;
    private String myDeltaBase;
    private long myVersion;
    private String myClientOptions;
    private String myBaseChecksum;
    private String myResultChecksum;
    private String myUserName;
    private SVNProperties mySVNProperties;
    private Collection myDeadProperties;
    private Collection myEntries;
    private File myActivitiesDB;
    private FSFS myFSFS;
    private FSTransactionRoot myTxnRoot;
    private String myTxnName;
    
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
        myUserName = userName;
        myActivitiesDB = activitiesDB;
        prepare();
    }

    private DAVResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum) {
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

    public long getRevision() throws DAVException {
        if (getResourceURI().getType() == DAVResourceType.REGULAR || getResourceURI().getType() == DAVResourceType.VERSION) {
            if (!isValidRevision(myRevision)) {
                try {
                    myRevision = getLatestRevision();
                } catch (SVNException e) {
                    throw DAVException.convertError(e.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Could not fetch 'youngest' revision to enable accessing the latest baseline resource.");
                }
            }
        }
        return myRevision;
    }

    public boolean exists() throws SVNException {
        DAVResourceType type = getType(); 
        if (type == DAVResourceType.REGULAR || (type == DAVResourceType.WORKING && !getResourceURI().isBaseLined())) {
            checkPath();
        } else if (type == DAVResourceType.VERSION ||
                (type == DAVResourceType.WORKING && getResourceURI().isBaseLined())) {
            myIsExists = true;
        }
        return myIsExists;
    }

    public DAVResourceType getType() {
        return getResourceURI().getType();
    }

    public boolean canBeActivity() throws SVNException {
        return isAutoCheckedOut() || (getType() == DAVResourceType.ACTIVITY && !exists());
    }
    
    public boolean isCollection() throws SVNException {
        if (getResourceURI().getType() == DAVResourceType.REGULAR || (getResourceURI().getType() == DAVResourceType.WORKING && !getResourceURI().isBaseLined())) {
            checkPath();
        }
        return myIsCollection;
    }

    public void setExists(boolean isExist) {
        myIsExists = isExist;
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
                    return new DAVResource(getRepository(), newResourceURI, getRevision(), isSVNClient(), getDeltaBase(), getVersion(), getClientOptions(), null, null);
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

    private void prepare() throws DAVException {
        if (getResourceURI().getType() == DAVResourceType.VERSION) {
            getResourceURI().setURI(DAVPathUtil.buildURI(null, DAVResourceKind.BASELINE, getRevision(), null));
            setExists(true);
        } else if (getResourceURI().getType() == DAVResourceType.WORKING) {
            //TODO: Define filename for ACTIVITY_ID under the repository
            String txnName = getTxn();
            if (txnName == null) {
                throw new DAVException("An unknown activity was specified in the URL. This is generally caused by a problem in the client software.", 
                        HttpServletResponse.SC_BAD_REQUEST, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
            }
            myTxnName = txnName;
            FSTransactionInfo txnInfo = null;
            try {
                txnInfo = myFSFS.openTxn(myTxnName);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_TRANSACTION) {
                    throw new DAVException("An activity was specified and found, but the corresponding SVN FS transaction was not found.", 
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null); 
                }
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "An activity was specified and found, but the corresponding SVN FS transaction was not found.");
            }
            
            if (getResourceURI().isBaseLined()) {
                setExists(true);
                return;
            }
            
            if (myUserName != null) {
                SVNProperties props = null;
                try {
                    props = myFSFS.getTransactionProperties(myTxnName);
                } catch (SVNException svne) {
                    throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Failed to retrieve author of the SVN FS transaction corresponding to the specified activity.");
                }
                
                String currentAuthor = props.getStringValue(SVNRevisionProperty.AUTHOR);
                if (currentAuthor == null) {
                    try {
                        myFSFS.setTransactionProperty(myTxnName, SVNRevisionProperty.AUTHOR, SVNPropertyValue.create(myUserName));
                    } catch (SVNException svne) {
                        throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                                "Failed to set the author of the SVN FS transaction corresponding to the specified activity.");
                    }
                } else if (!currentAuthor.equals(myUserName)) {
                    throw new DAVException("Multi-author commits not supported.", HttpServletResponse.SC_NOT_IMPLEMENTED, null, 
                            SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
                }
            }
            
            try {
                myTxnRoot = myFSFS.createTransactionRoot(txnInfo);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not open the (transaction) root of the repository");
            }
        } else if (getResourceURI().getType() == DAVResourceType.ACTIVITY) {
            String txnName = getTxn();
            setExists(txnName != null);
            //TODO: Define filename for ACTIVITY_ID under the repository
        }
    }

    private String getTxn() {
        DAVResourceURI resourceURI = getResourceURI();
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

    private boolean isChecked() {
        return myChecked;
    }

    private void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
    }

    private void setChecked(boolean checked) {
        myChecked = checked;
    }

    private void checkPath() throws SVNException {
        if (!isChecked()) {
            SVNNodeKind currentNodeKind = getRepository().checkPath(getResourceURI().getPath(), getRevision());
            setExists(currentNodeKind != SVNNodeKind.NONE && currentNodeKind != SVNNodeKind.UNKNOWN);
            setCollection(currentNodeKind == SVNNodeKind.DIR);
            setChecked(true);
        }
    }

    private boolean lacksETagPotential() throws SVNException {
        return (!exists() || (getResourceURI().getType() != DAVResourceType.REGULAR && getResourceURI().getType() != DAVResourceType.VERSION)
                || getResourceURI().getType() == DAVResourceType.VERSION && getResourceURI().isBaseLined());
    }

}
