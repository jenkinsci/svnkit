/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.admin.ISVNGNUDiffGenerator;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNGNUDiffGenerator extends DefaultSVNDiffGenerator implements ISVNGNUDiffGenerator {

    private String myHeader;
    private boolean myIsHeaderWritten;
    private FSRoot myOriginalRoot;
    private String myOriginalPath;
    private FSRoot myNewRoot;
    private String myNewPath;

    public void displayHeader(int type, String path, String copyFromPath, long copyFromRevision, OutputStream result) throws SVNException {
        switch (type) {
            case ADDED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Added: " + path;
                    writeHeader(result);
                }
                break;
            case DELETED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Deleted: " + path;
                    writeHeader(result);
                }
                break;
            case MODIFIED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    myHeader = "Modified: " + path;
                    writeHeader(result);
                }
                break;
            case COPIED:
                if (!myIsHeaderWritten) {
                    path = path.startsWith("/") ? path.substring(1) : path;
                    copyFromPath = copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath;
                    myHeader = "Copied: " + path + " (from rev " + copyFromRevision + ", " + copyFromPath + ")";
                    writeHeader(result);
                }
                break;
            case NO_DIFF:
                try {
                    result.write(EOL);
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                }
                break;
        }
    }

    public void displayFileDiff(String path, File file1, File file2,
            String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
        super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, result);
        try {
            result.write(EOL);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
    }

    protected boolean displayHeader(OutputStream os, String path, boolean deleted) throws IOException {
        if (!myIsHeaderWritten) {
            path = path.startsWith("/") ? path.substring(1) : path;
            myHeader = "Index: " + path;
            os.write(myHeader.getBytes(getEncoding())); 
            os.write(EOL);
            myIsHeaderWritten = true;
        }
        os.write(HEADER_SEPARATOR);
        os.write(EOL);
        return false;
    }
    
    protected void displayBinary(OutputStream os, String mimeType1, String mimeType2) throws IOException {
        os.write("(Binary files differ)".getBytes(getEncoding()));
        os.write(EOL);
    }

    protected void displayHeaderFields(OutputStream os, String path1, String rev1, String path2, String rev2) throws IOException {
        os.write("--- ".getBytes(getEncoding()));
        String originalLabel = null;
        String newLabel = null;
        try {
            originalLabel = generateLabel(myOriginalRoot, myOriginalPath); 
            newLabel = generateLabel(myNewRoot, myNewPath);
        } catch (SVNException svne) {
            throw new IOException(svne.getLocalizedMessage());
        }
        os.write(originalLabel.getBytes(getEncoding()));
        os.write(EOL);
        os.write("+++ ".getBytes(getEncoding()));
        os.write(newLabel.getBytes(getEncoding()));
        os.write(EOL);
    }

    protected void setOriginalFile(FSRoot originalRoot, String originalPath) {
        myOriginalRoot = originalRoot;
        myOriginalPath = originalPath;
    }

    protected void setNewFile(FSRoot newRoot, String newPath) {
        myNewRoot = newRoot;
        myNewPath = newPath;
    }

    private String generateLabel(FSRoot root, String path) throws SVNException {
        String date = null;
        String txnName = null;
        long rev = 0;
        if (root != null) {
            FSFS fsfs = root.getOwner();
            Map props = null;
            if (root instanceof FSRevisionRoot) {
                FSRevisionRoot revisionRoot = (FSRevisionRoot) root;
                rev = revisionRoot.getRevision();
                props = fsfs.getRevisionProperties(rev);
            } else {
                FSTransactionRoot txnRoot = (FSTransactionRoot) root;
                txnName = txnRoot.getTxnID();
                props = fsfs.getTransactionProperties(txnName);
            }
            date = (String) props.get(SVNRevisionProperty.DATE);
        } 
        
        String dateString = null;
        if (date != null) {
            int tInd = date.indexOf('T');
            dateString = date.substring(0, tInd) + " " + date.substring(tInd + 1, tInd + 9) + " UTC";
            
        } else {
            dateString = "                       ";
        }
        
        if (txnName != null) {
            return path + '\t' + dateString + " (txn " + txnName + ")";
        }
        return path + '\t' + dateString + " (rev " + rev + ")";
    }
    
    private void writeHeader(OutputStream result) throws SVNException {
        try {
            result.write(myHeader.getBytes(getEncoding())); 
            result.write(EOL);
            myIsHeaderWritten = true;
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        }
    }
    
    protected boolean isHeaderForced(File file1, File file2) {
        return true;
    }

}
