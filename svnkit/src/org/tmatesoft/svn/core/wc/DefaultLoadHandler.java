/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSCommitter;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSHooks;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DefaultLoadHandler implements ISVNLoadHandler {
    
    private FSFS myFSFS;
    private RevisionBaton myCurrentRevisionBaton;
    private NodeBaton myCurrentNodeBaton;
    private boolean myIsUsePreCommitHook;
    private boolean myIsUsePostCommitHook;
    private Map myRevisionsMap;
    private String myParentDir;
    private SVNUUIDAction myUUIDAction;
    
    public DefaultLoadHandler(boolean usePreCommitHook, boolean usePostCommitHook, SVNUUIDAction uuidAction, String parentDir) {
        myIsUsePreCommitHook = usePreCommitHook;
        myIsUsePostCommitHook = usePostCommitHook;
        myUUIDAction = uuidAction;
        myParentDir = SVNPathUtil.canonicalizeAbsPath(parentDir);
        myRevisionsMap = new HashMap();
    }
    
    protected void setFSFS(FSFS fsfs) {
        myFSFS = fsfs;
    }
    
    public void closeRevision() throws SVNException {
        if (myCurrentRevisionBaton != null) {
            RevisionBaton baton = myCurrentRevisionBaton;
            myCurrentRevisionBaton = null;
            
            if (baton.myRevision <= 0) {
                return;
            }
            
            long oldRevision = baton.myRevision;
            if (myIsUsePreCommitHook) {
                try {
                    FSHooks.runPreCommitHook(myFSFS.getRepositoryRoot(), baton.myTxn.getTxnId());
                } catch (SVNException svne) {
                    try {
                        FSCommitter.abortTransaction(myFSFS, baton.myTxn.getTxnId());
                    } catch (SVNException svne2) {
                        //
                    }
                    throw svne;
                }
            }
            
            long newRevision = -1;
            try {
                newRevision = baton.myCommitter.commitTxn();
            } catch (SVNException svne) {
                try {
                    FSCommitter.abortTransaction(myFSFS, baton.myTxn.getTxnId());
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            
            if (myIsUsePostCommitHook) {
                try {
                    FSHooks.runPostCommitHook(myFSFS.getRepositoryRoot(), newRevision);
                } catch (SVNException svne) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED, "Commit succeeded, but post-commit hook failed");
                    SVNErrorManager.error(err, svne);
                }
            }
            
            myRevisionsMap.put(new Long(oldRevision), new Long(newRevision));
            if (baton.myDatestamp != null) {
                myFSFS.setRevisionProperty(newRevision, SVNRevisionProperty.DATE, baton.myDatestamp);
            }
            
            if (newRevision == baton.myRevision) {
                SVNDebugLog.getDefaultLog().info("\n------- Committed revision " + newRevision + " >>>\n\n");
            } else {
                SVNDebugLog.getDefaultLog().info("\n------- Committed new rev " + newRevision + " (loaded from original rev " + baton.myRevision + ") >>>\n\n");
            }
        }
    }

    public void openRevision(Map headers) throws SVNException {
        myCurrentRevisionBaton = new RevisionBaton();
        long revision = -1;
        if (headers.containsKey(SVNAdminClient.DUMPFILE_REVISION_NUMBER)) {
            try {
                revision = Long.parseLong((String) headers.get(SVNAdminClient.DUMPFILE_REVISION_NUMBER)); 
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Cannot parse revision ({0}) in dump file", headers.get(SVNAdminClient.DUMPFILE_REVISION_NUMBER));
                SVNErrorManager.error(err);
            }
        }
        
        myCurrentRevisionBaton.myRevision = revision;
        long headRevision = myFSFS.getYoungestRevision();
        myCurrentRevisionBaton.myRevisionOffset = revision - (headRevision + 1);
        
        if (revision > 0) {
            myCurrentRevisionBaton.myTxn = FSTransactionRoot.beginTransaction(headRevision, 0, myFSFS);
            myCurrentRevisionBaton.myTxnRoot = myFSFS.createTransactionRoot(myCurrentRevisionBaton.myTxn.getTxnId()); 
            SVNDebugLog.getDefaultLog().info("<<< Started new transaction, based on original revision " + revision + "\n");
        }
        myCurrentRevisionBaton.myCommitter = new FSCommitter(myFSFS, myCurrentRevisionBaton.myTxnRoot, myCurrentRevisionBaton.myTxn, null, null);
    }

    public void openNode(Map headers) throws SVNException {
        if (myCurrentRevisionBaton.myRevision == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpstream: Revision 0 must not contain node records");
            SVNErrorManager.error(err);
        }
        
        myCurrentNodeBaton = createNodeBaton(headers);
        switch (myCurrentNodeBaton.myAction) {
            case NodeBaton.NODE_ACTION_CHANGE:
                SVNDebugLog.getDefaultLog().info("     * editing path : " + myCurrentNodeBaton.myPath + " ...");
                break;
            case NodeBaton.NODE_ACTION_DELETE:
                SVNDebugLog.getDefaultLog().info("     * deleting path : " + myCurrentNodeBaton.myPath + " ...");
                myCurrentRevisionBaton.myCommitter.deleteNode(myCurrentNodeBaton.myPath);
                break;
            case NodeBaton.NODE_ACTION_ADD:
                SVNDebugLog.getDefaultLog().info("     * adding path : " + myCurrentNodeBaton.myPath + " ...");
                maybeAddWithHistory(myCurrentNodeBaton);
                break;
            case NodeBaton.NODE_ACTION_REPLACE:
                SVNDebugLog.getDefaultLog().info("     * replacing path : " + myCurrentNodeBaton.myPath + " ...");
                myCurrentRevisionBaton.myCommitter.deleteNode(myCurrentNodeBaton.myPath);
                maybeAddWithHistory(myCurrentNodeBaton);
                break;
            default:
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNRECOGNIZED_DATA, "Unrecognized node-action on node ''{0}''", myCurrentNodeBaton.myPath);
                SVNErrorManager.error(err);
        }
    }

    public void parseUUID(String uuid) throws SVNException {
        if (myUUIDAction == SVNUUIDAction.INORE_UUID) {
            return;
        }
        
        if (myUUIDAction != SVNUUIDAction.FORCE_UUID) {
            long latestRevision = myFSFS.getYoungestRevision();
            if (latestRevision != 0) {
                return;
            }
        }

        myFSFS.setUUID(uuid);
    }

    public void closeNode() throws SVNException {
        
    }

    public void removeNodeProperties() throws SVNException {
        FSTransactionRoot txnRoot = myCurrentRevisionBaton.myTxnRoot;
        FSRevisionNode node = txnRoot.getRevisionNode(myCurrentNodeBaton.myPath);
        Map props = node.getProperties(myFSFS);
        
        for (Iterator propNames = props.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            myCurrentRevisionBaton.myCommitter.changeNodeProperty(myCurrentNodeBaton.myPath, propName, null);
        }
    }

    public void setNodeProperty(String propertyName, String propertyValue) throws SVNException {
        myCurrentRevisionBaton.myCommitter.changeNodeProperty(myCurrentNodeBaton.myPath, propertyName, propertyValue);
    }

    public void setRevisionProperty(String propertyName, String propertyValue) throws SVNException {
        if (myCurrentRevisionBaton.myRevision > 0) {
            myFSFS.setTransactionProperty(myCurrentRevisionBaton.myTxn.getTxnId(), propertyName, propertyValue);
            if (SVNRevisionProperty.DATE.equals(propertyName)) {
                myCurrentRevisionBaton.myDatestamp = propertyValue;
            }
        } else if (myCurrentRevisionBaton.myRevision == 0) {
            long youngestRevision = myFSFS.getYoungestRevision();
            if (youngestRevision == 0) {
                myFSFS.setRevisionProperty(0, propertyName, propertyValue);
            }
        }
    }

    private void maybeAddWithHistory(NodeBaton nodeBaton) throws SVNException {
        if (nodeBaton.myCopyFromPath == null) {
            if (nodeBaton.myKind == SVNNodeKind.FILE) {
                myCurrentRevisionBaton.myCommitter.makeFile(nodeBaton.myPath);
            } else if (nodeBaton.myKind == SVNNodeKind.DIR) {
                myCurrentRevisionBaton.myCommitter.makeDir(nodeBaton.myPath);
            }
        } else {
            long srcRevision = nodeBaton.myCopyFromRevision - myCurrentRevisionBaton.myRevisionOffset;
            Long copyFromRevision = new Long(nodeBaton.myCopyFromRevision);
            
            if (myRevisionsMap.containsKey(copyFromRevision)) {
                Long revision = (Long) myRevisionsMap.get(copyFromRevision);
                srcRevision = revision.longValue();
            }
            
            if (!SVNRevision.isValidRevisionNumber(srcRevision)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Relative source revision {0,number,integer} is not available in current repository", new Long(srcRevision));
                SVNErrorManager.error(err);
            }
            
            FSRevisionRoot copyRoot = myFSFS.createRevisionRoot(srcRevision);
            myCurrentRevisionBaton.myCommitter.makeCopy(copyRoot, nodeBaton.myCopyFromPath, nodeBaton.myPath, true);
            SVNDebugLog.getDefaultLog().info("COPIED...");
        }
    }
    
    private NodeBaton createNodeBaton(Map headers) throws SVNException {
        NodeBaton baton = new NodeBaton();
        baton.myKind = SVNNodeKind.UNKNOWN;
        if (headers.containsKey(SVNAdminClient.DUMPFILE_NODE_PATH)) {
            String nodePath = (String) headers.get(SVNAdminClient.DUMPFILE_NODE_PATH); 
            if (myParentDir != null) {
                baton.myPath = SVNPathUtil.concatToAbs(myParentDir, nodePath.startsWith("/") ? nodePath.substring(1) : nodePath);
            } else {
                baton.myPath = SVNPathUtil.canonicalizeAbsPath(nodePath);
            }
        }
        
        if (headers.containsKey(SVNAdminClient.DUMPFILE_NODE_KIND)) {
            baton.myKind = SVNNodeKind.parseKind((String) headers.get(SVNAdminClient.DUMPFILE_NODE_KIND));
        }
        
        baton.myAction = NodeBaton.NODE_ACTION_UNKNOWN;
        if (headers.containsKey(SVNAdminClient.DUMPFILE_NODE_ACTION)) {
            String action = (String) headers.get(SVNAdminClient.DUMPFILE_NODE_ACTION);
            if ("change".equals(action)) {
                baton.myAction = NodeBaton.NODE_ACTION_CHANGE;
            } else if ("add".equals(action)) {
                baton.myAction = NodeBaton.NODE_ACTION_ADD;
            } else if ("delete".equals(action)) {
                baton.myAction = NodeBaton.NODE_ACTION_DELETE;
            } else if ("replace".equals(action)) {
                baton.myAction = NodeBaton.NODE_ACTION_REPLACE;
            }
        }
        
        baton.myCopyFromRevision = -1;
        if (headers.containsKey(SVNAdminClient.DUMPFILE_NODE_COPYFROM_REVISION)) {
            try {
                baton.myCopyFromRevision = Long.parseLong((String) headers.get(SVNAdminClient.DUMPFILE_NODE_COPYFROM_REVISION)); 
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Cannot parse revision ({0}) in dump file", headers.get(SVNAdminClient.DUMPFILE_NODE_COPYFROM_REVISION));
                SVNErrorManager.error(err);
            }
        }
        
        if (headers.containsKey(SVNAdminClient.DUMPFILE_NODE_COPYFROM_PATH)) {
            String copyFromPath = (String) headers.get(SVNAdminClient.DUMPFILE_NODE_COPYFROM_PATH);
            if (myParentDir != null) {
                baton.myCopyFromPath = SVNPathUtil.concatToAbs(myParentDir, copyFromPath.startsWith("/") ? copyFromPath.substring(1) : copyFromPath);
            } else {
                baton.myCopyFromPath = SVNPathUtil.canonicalizeAbsPath(copyFromPath);
            }
        }
        
        if (headers.containsKey(SVNAdminClient.DUMPFILE_TEXT_CONTENT_LENGTH)) {
            baton.myTextChecksum = (String) headers.get(SVNAdminClient.DUMPFILE_TEXT_CONTENT_LENGTH);
        }        
        return baton;
    }
    
    private class RevisionBaton {
        FSTransactionInfo myTxn;
        FSTransactionRoot myTxnRoot;
        long myRevision;
        long myRevisionOffset;
        String myDatestamp;
        FSCommitter myCommitter;
    }
    
    private class NodeBaton {
        public static final int NODE_ACTION_UNKNOWN = -1;
        public static final int NODE_ACTION_CHANGE = 0;
        public static final int NODE_ACTION_ADD = 1;
        public static final int NODE_ACTION_DELETE = 2;
        public static final int NODE_ACTION_REPLACE = 3;

        String myPath;
        SVNNodeKind myKind;
        int myAction;
        long myCopyFromRevision;
        String myCopyFromPath;
        String myTextChecksum;
    }
}
