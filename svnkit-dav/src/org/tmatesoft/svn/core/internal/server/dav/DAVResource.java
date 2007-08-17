/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVResource {

    public static final long INVALID_REVISION = SVNRepository.INVALID_REVISION;

    public static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=utf-8";
    public static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    private DAVResourceURI myResourceURI;

    private SVNRepository myRepository;
    private long myRevision;

    private boolean myIsExists = false;
    private boolean myIsCollection;
    private boolean myIsSVNClient;
    private long myVersion;
    private String myClientOptions;
    private String myBaseChecksum;
    private String myResultChecksum;

    private Map mySVNProperties;
    private Collection myEntries;

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
    public DAVResource(SVNRepository repository, String context, String uri, String label, boolean useCheckedIn) throws SVNException {
        myRepository = repository;
        myResourceURI = new DAVResourceURI(context, uri, label, useCheckedIn);
        myRevision = myResourceURI.getRevision();
        myIsExists = myResourceURI.exists();
        prepare();
    }

    public DAVResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String versionName, String clientOptions,
                       String baseChecksum, String resultChecksum) throws SVNException {
        myRepository = repository;
        myResourceURI = resourceURI;
        myIsSVNClient = isSVNClient;
        try {
            myVersion = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
            myVersion = INVALID_REVISION;
        }
        myClientOptions = clientOptions;
        myBaseChecksum = baseChecksum;
        myResultChecksum = resultChecksum;
        myRevision = resourceURI.getRevision();
        myIsExists = resourceURI.exists();
        prepare();
    }

    public DAVResourceURI getResourceURI() {
        return myResourceURI;
    }

    public void setResourceURI(DAVResourceURI resourceURI) {
        myResourceURI = resourceURI;
    }

    public SVNRepository getRepository() {
        return myRepository;
    }

    public long getRevision() {
        return myRevision;
    }

    private void setRevision(long revisionNumber) {
        myRevision = revisionNumber;
    }

    public boolean exists() {
        return myIsExists;
    }

    private void setExists(boolean isExist) {
        myIsExists = isExist;
    }

    public boolean isCollection() {
        return myIsCollection;
    }

    private void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
    }

    public boolean isSVNClient() {
        return myIsSVNClient;
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

    private Map getSVNProperties() {
        if (mySVNProperties == null) {
            mySVNProperties = new HashMap();
        }
        return mySVNProperties;
    }

    private void prepare() throws SVNException {
        long latestRevision = getLatestRevision();
        if (getResourceURI().getType() == DAVResourceType.REGULAR) {
            if (getRevision() == INVALID_REVISION) {
                setRevision(latestRevision);
            }
            checkPath();
            if (isCollection()) {
                getRepository().getDir(getResourceURI().getPath(), getRevision(), getSVNProperties(), (Collection) null);
            } else {
                getRepository().getFile(getResourceURI().getPath(), getRevision(), getSVNProperties(), null);
            }
        } else if (getResourceURI().getType() == DAVResourceType.VERSION) {
            setRevision(latestRevision);
            setExists(true);
            // URI doesn't contain any information about context of requested uri
            getResourceURI().setURI(DAVPathUtil.buildURI(null, DAVResourceKind.BASELINE, getRevision(), ""));
        } else if (getResourceURI().getType() == DAVResourceType.WORKING) {
            //TODO: Define filename for ACTIVITY_ID under the repository
            if (getResourceURI().isBaseLined()) {
                setExists(true);
                return;
            }
            checkPath();
        } else if (getResourceURI().getType() == DAVResourceType.ACTIVITY) {
            //TODO: Define filename for ACTIVITY_ID under the repository
        }
    }

    private void checkPath() throws SVNException {
        SVNNodeKind currentNodeKind = getRepository().checkPath(getResourceURI().getPath(), getRevision());
        setExists(currentNodeKind != SVNNodeKind.NONE && currentNodeKind != SVNNodeKind.UNKNOWN);
        setCollection(currentNodeKind == SVNNodeKind.DIR);
    }

    public Collection getEntries() throws SVNException {
        if (isCollection() && myEntries == null) {
            myEntries = new ArrayList();
            //May be we need to add property mask if any hadler uses not only directory entry size property.
            getRepository().getDir(getResourceURI().getPath(), getRevision(), null, SVNDirEntry.DIRENT_KIND, myEntries);
        }
        return myEntries;
    }

    private boolean lacksETagPotential() {
        return (!exists() || (getResourceURI().getType() != DAVResourceType.REGULAR && getResourceURI().getType() != DAVResourceType.VERSION)
                || getResourceURI().getType() == DAVResourceType.VERSION && getResourceURI().isBaseLined());
    }

    public long getCreatedRevision() {
        String revisionParameter = getProperty(SVNProperty.COMMITTED_REVISION);
        try {
            return Long.parseLong(revisionParameter);
        } catch (NumberFormatException e) {
            return getRevision();
        }
    }

    public Date getLastModified() throws SVNException {
        if (lacksETagPotential()) {
            return null;
        }
        return getRevisionDate(getCreatedRevision());
    }


    public Date getRevisionDate(long revision) throws SVNException {
        return SVNTimeUtil.parseDate(getRevisionProperty(revision, SVNRevisionProperty.DATE));
    }

    public String getETag() {
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (getResourceURI().getType() == DAVResourceType.PRIVATE && getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
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
        return getRepository().getLatestRevision();
    }

    public long getContentLength() throws SVNException {
        SVNDirEntry entry = getRepository().getDir(getResourceURI().getPath(), getRevision(), false, null);
        return entry.getSize();
    }

    public String getAuthor(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.AUTHOR);
    }

    public String getMD5Checksum() {
        return getProperty(SVNProperty.CHECKSUM);
    }

    public Collection getDeadProperties() {
        Collection deadProperties = new ArrayList();
        for (Iterator iterator = getSVNProperties().keySet().iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            if (SVNProperty.isRegularProperty(propertyName)) {
                deadProperties.add(propertyName);
            }
        }
        return deadProperties;
    }

    public String getLog(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.LOG);
    }

    public String getProperty(String propertyName) {
        return (String) getSVNProperties().get(propertyName);
    }

    public String getRevisionProperty(long revision, String propertyName) throws SVNException {
        return getRepository().getRevisionPropertyValue(revision, propertyName);
    }

    public void writeTo(OutputStream out) throws SVNException {
        if (isCollection()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
        }
        getRepository().getFile(getResourceURI().getPath(), getRevision(), null, out);
    }
}
