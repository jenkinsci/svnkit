/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea16 extends SVNAdminArea15 {

    public SVNAdminArea16(File dir) {
        super(dir);
    }

    public boolean hasTreeConflicts(String name) throws SVNException {
        //TODO: implement
        return false;
    }

    public void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException {
        Map attributes = new HashMap();
        if (url != null) {
            String strURL = url.toDecodedString();
            String reposRootStrURL = reposRootURL.toDecodedString();
            String path = strURL.substring(reposRootStrURL.length());
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            attributes.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, pegRevision);
            attributes.put(SVNProperty.FILE_EXTERNAL_REVISION, revision);
            attributes.put(SVNProperty.FILE_EXTERNAL_PATH, path);
        } else {
            attributes.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, SVNRevision.UNDEFINED);
            attributes.put(SVNProperty.FILE_EXTERNAL_REVISION, SVNRevision.UNDEFINED);
            attributes.put(SVNProperty.FILE_EXTERNAL_PATH, null);
        }
        modifyEntry(name, attributes, true, false);
    }

    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        if (super.readExtraOptions(reader, entryAttrs)) {
            return true;
        }
        
        String line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String treeConflictData = parseString(line);
        if (treeConflictData != null) {
            //TODO: parse here tree conflict data and put it into the entry object
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String fileExternalData = parseString(line);
        if (fileExternalData != null) {
            unserializeExternalFileData(entryAttrs, fileExternalData);
        }
        
        return false;
    }

    protected void writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        String treeConflictData = (String) entryAttrs.get(SVNProperty.TREE_CONFLICT_DATA); 
        if (writeString(writer, treeConflictData, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String serializedFileExternalData = serializeExternalFileData(entryAttrs);
        if (writeString(writer, serializedFileExternalData, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
    }

    protected int getFormatVersion() {
        return SVNAdminArea16Factory.WC_FORMAT;
    }
    
    private String serializeExternalFileData(Map entryAttrs) throws SVNException {
        String representation = null;
        String path = (String) entryAttrs.get(SVNProperty.FILE_EXTERNAL_PATH);
        SVNRevision revision = (SVNRevision) entryAttrs.get(SVNProperty.FILE_EXTERNAL_REVISION);
        SVNRevision pegRevision = (SVNRevision) entryAttrs.get(SVNProperty.FILE_EXTERNAL_PEG_REVISION);
        if (path != null) {
            String revStr = asString(revision, path);
            String pegRevStr = asString(pegRevision, path);
            representation = pegRevStr + ":" + revStr + ":" + path;
        }
        return representation;
    }
    
    private String asString(SVNRevision revision, String path) throws SVNException {
        if (revision == SVNRevision.HEAD || 
                SVNRevision.isValidRevisionNumber(revision.getNumber())) {
            return revision.toString();
        }
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Illegal file external revision kind {0} for path ''{1}''", 
        
                new Object[] { revision.toString(), path });
        SVNErrorManager.error(err, SVNLogType.WC);
        return null;
    }
    
    private void unserializeExternalFileData(Map entryAttrs, String rawExternalFileData) throws SVNException {
        SVNRevision pegRevision = SVNRevision.UNDEFINED;
        SVNRevision revision = SVNRevision.UNDEFINED;
        String path = null;
        if (rawExternalFileData != null) {
            StringBuffer buffer = new StringBuffer(rawExternalFileData);
            pegRevision = parseRevision(buffer);
            revision = parseRevision(buffer);
            path = buffer.toString();
        }
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_PATH, path);
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_REVISION, revision);
        entryAttrs.put(SVNProperty.FILE_EXTERNAL_PEG_REVISION, pegRevision);
    }
    
    private SVNRevision parseRevision(StringBuffer str) throws SVNException {
        int ind = str.indexOf(":"); 
        if ( ind == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
                    "Found an unexpected \\0 in the file external ''{0}''", str);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNRevision revision = null;
        String subStr = str.substring(0, ind);
        if (subStr.equals(SVNRevision.HEAD.getName())) {
            revision = SVNRevision.HEAD;
        } else {
            revision = SVNRevision.parse(subStr);
        }
        str = str.delete(0, ind + 1);
        return revision;
    }
}
