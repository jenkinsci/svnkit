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
package org.tmatesoft.svn.core.internal.io.serf;

import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVBaselineInfo;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVProperties;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVEditorHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SerfEditorHandler extends DAVEditorHandler {

    public static StringBuffer generateEditorRequest(final SerfConnection connection, StringBuffer xmlBuffer, 
            String url, long targetRevision, String target, String dstPath, SVNDepth depth, final Map lockTokens, 
            boolean ignoreAncestry, boolean sendCopyFromArgs, ISVNReporterBaton reporterBaton) throws SVNException {
        xmlBuffer = SVNXMLUtil.addXMLHeader(xmlBuffer);
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        Map attrs = new SVNHashMap();
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-report", 
                SVN_NAMESPACES_LIST, SVNXMLUtil.PREFIX_MAP, attrs, xmlBuffer);
        
        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "src-path", url, xmlBuffer);
        if (targetRevision >= 0) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", 
                    String.valueOf(targetRevision), xmlBuffer);
        }
        
        if (dstPath != null) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "dst-path", dstPath, xmlBuffer);
        }
        
        if (target != null && !"".equals(target)) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-target", target, xmlBuffer);
        }

        if (ignoreAncestry) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "ignore-ancestry", "yes", xmlBuffer);
        }

        if (sendCopyFromArgs) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "send-copyfrom-args", "yes", xmlBuffer);
        }

        if (depth == SVNDepth.FILES || depth == SVNDepth.EMPTY) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "recursive", "no", xmlBuffer);
        }

        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "depth", SVNDepth.asString(depth), xmlBuffer);

        
        final StringBuffer report = xmlBuffer;
        
        reporterBaton.report(new ISVNReporter() {
            public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                setPath(path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
            }
            public void deletePath(String path) {
                
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "missing", path, report);
            }

            public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                linkPath(url, path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
            }

            public void finishReport() {
            }

            public void linkPath(SVNURL url, String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
                Map attrs = new SVNHashMap();
                attrs.put(REVISION_ATTR, String.valueOf(revision));
                if (lockToken != null) {
                    if (lockTokens != null) {
                        lockTokens.put(path, lockToken);
                    }
                    attrs.put("lock-token", lockToken);
                }
                
                attrs.put("depth", SVNDepth.asString(depth));
                if (startEmpty) {
                    attrs.put("start-empty", Boolean.TRUE.toString());
                }
                
                String linkedPath = url.getURIEncodedPath();
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(connection, null, linkedPath, revision, false, 
                        false, null);
                linkedPath = SVNEncodingUtil.uriDecode(info.baselinePath); 
                
                if (!linkedPath.startsWith("/")) {
                    linkedPath = "/" + linkedPath;
                }
                
                attrs.put("linkpath", linkedPath);
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "entry", path, attrs, report);
            }

            public void setPath(String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
                Map attrs = new SVNHashMap();
                attrs.put(REVISION_ATTR, String.valueOf(revision));
                if (lockToken != null) {
                    if (lockTokens != null) {
                        lockTokens.put(path, lockToken);
                    }
                    attrs.put("lock-token", lockToken);
                }

                attrs.put("depth", SVNDepth.asString(depth));

                if (startEmpty) {
                    attrs.put("start-empty", Boolean.TRUE.toString());
                }
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "entry", path, attrs, report);
            }

            public void abortReport() throws SVNException {
            }
        });
        SVNXMLUtil.addXMLFooter(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-report", xmlBuffer);
        return xmlBuffer;
    }

    private State myCurrentState;
    private ReportInfo myCurrentReportInfo;
    private DirInfo myRootDir;
    private long myTargetRevision;
    private SerfConnection myConnection;
    private int myActivePropFinds;
    private Stack myStates;
    private String myDstPath;
    
    public SerfEditorHandler(IHTTPConnectionFactory connectionFactory, SerfConnection connection, 
            SerfRepository owner, ISVNEditor editor, Map lockTokens, boolean fetchContent, boolean hasTarget, 
            long targetRevision, String dstPath) {
        super(connectionFactory, owner, editor, lockTokens, fetchContent, hasTarget);   
        myConnection = connection;
        myCurrentState = State.NONE;
        myTargetRevision = targetRevision;
        myActivePropFinds = 0;
        myDstPath = dstPath;
        myStates = new Stack();
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (myCurrentState == State.NONE && element == TARGET_REVISION) {
            String revision = attrs.getValue(REVISION_ATTR);
            if (revision == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in target-revision element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            long revNumber = Long.parseLong(revision);
            myEditor.targetRevision(revNumber);
        } else if (myCurrentState == State.NONE && element == OPEN_DIRECTORY) {
            String revAttr = attrs.getValue(REVISION_ATTR);
            if (revAttr == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in open-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            long revision = Long.parseLong(revAttr);

            ReportInfo info = pushState(State.OPEN_DIR);
            info.myBaseRevision = revision; 
            info.myDir.myBaseRevision = revision;
            info.myIsFetchProps = true;
            info.myBaseName = info.myDir.myBaseName = "";
            info.myPath = info.myDir.myPath = "";
        } else if (myCurrentState == State.NONE) {
            //..
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && 
                element == OPEN_DIRECTORY) {
            String revAttr = attrs.getValue(REVISION_ATTR);
            if (revAttr == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in open-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            long revision = Long.parseLong(revAttr);

            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in open-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }

            ReportInfo info = pushState(State.OPEN_DIR);
            DirInfo dir = info.myDir;
            info.myBaseRevision = revision;
            dir.myBaseRevision = revision;
            dir.myBaseName = info.myBaseName = name;
            dir.myPath = SVNPathUtil.append(dir.myParentDir.myPath, name);
            info.myPath = dir.myPath;
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && 
                element == ADD_DIRECTORY) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in add-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = SVNRepository.INVALID_REVISION;
            if (copyFromPath != null) {
                String copyFromRevString = attrs.getValue(COPYFROM_REV_ATTR);
                if (copyFromRevString != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing copyfrom-rev attr in add-directory element");
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                copyFromRev = Long.parseLong(copyFromRevString);
            }

            ReportInfo info = pushState(State.ADD_DIR);
            DirInfo dir = info.myDir;
            dir.myBaseName = info.myBaseName = name;
            dir.myPath = SVNPathUtil.append(dir.myParentDir.myPath, name);
            info.myPath = dir.myPath;
            
            info.myCopyFromPath = copyFromPath;
            info.myCopyFromRevision = copyFromRev;
            dir.myIsFetchProps = true;
            dir.myBaseRevision = info.myBaseRevision = SVNRepository.INVALID_REVISION;
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && element == OPEN_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in open-file element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }

            String revAttr = attrs.getValue(REVISION_ATTR);
            if (revAttr == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in open-file element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            long revision = Long.parseLong(revAttr);
            
            ReportInfo info = pushState(State.OPEN_FILE);
            info.myBaseRevision = revision;
            info.myBaseName = name;
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && element == ADD_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in add-file element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }

            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = SVNRepository.INVALID_REVISION;
            if (copyFromPath != null) {
                String copyFromRevString = attrs.getValue(COPYFROM_REV_ATTR);
                if (copyFromRevString != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing copyfrom-rev attr in add-file element");
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                copyFromRev = Long.parseLong(copyFromRevString);
            }
            
            ReportInfo info = pushState(State.ADD_FILE);
            info.myIsFetchProps = true;
            info.myIsFetchFile = true;
            info.myBaseName = name;
            info.myCopyFromPath = copyFromPath;
            info.myCopyFromRevision = copyFromRev;
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && 
                element == DELETE_ENTRY) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in delete-entry element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            DirInfo dir = myCurrentReportInfo.myDir; 
            openDir(dir);
            myEditor.deleteEntry(SVNPathUtil.append(dir.myPath, name), SVNRepository.INVALID_REVISION);
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && 
                element == ABSENT_DIRECTORY) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in absent-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            DirInfo dir = myCurrentReportInfo.myDir; 
            openDir(dir);
            myEditor.absentDir(SVNPathUtil.append(dir.myPath, name));
        } else if ((myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) && 
                element == ABSENT_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in absent-file element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            DirInfo dir = myCurrentReportInfo.myDir; 
            openDir(dir);
            myEditor.absentFile(SVNPathUtil.append(dir.myPath, name));
        } else if (myCurrentState == State.OPEN_DIR || myCurrentState == State.ADD_DIR) {
            ReportInfo info = null;
            if (element == DAVElement.CHECKED_IN) {
                info = pushState(State.IGNORE_PROP_NAME);
                info.myPropertyNameSpace = element.getNamespace();
                info.myPropertyName = element.getName();
            } else if (element == SET_PROP || element == REMOVE_PROP) {
                info = pushState(State.PROP);
                String propName = attrs.getValue(NAME_ATTR);
                if (propName == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing name attr in {0} element", element.getName());
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                
                int colonIndex = propName.indexOf(':');
                
                String propNameSpace = null;
                if (colonIndex != -1) {
                    propNameSpace = propName.substring(0, colonIndex);
                    propName = propName.substring(colonIndex + 1);
                }
                
                info.myPropertyNameSpace = propNameSpace;
                info.myPropertyName = propName;
                info.myEncoding = attrs.getValue(ENCODING_ATTR); 
            } else if (element == DAVElement.PROP) {
                pushState(State.NEED_PROP_NAME);
            } else if (element == FETCH_PROPS) {
                myCurrentReportInfo.myDir.myIsFetchProps = true;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "aborted in SerfEditorHandler: unexpected element met");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
        } else if (myCurrentState == State.OPEN_FILE || myCurrentState == State.ADD_FILE) {
            ReportInfo info = null;
            if (element == DAVElement.CHECKED_IN) {
                info = pushState(State.IGNORE_PROP_NAME);
                info.myPropertyName = element.getName();
                info.myPropertyNameSpace = element.getNamespace();
            } else if (element == DAVElement.PROP) {
                pushState(State.NEED_PROP_NAME);
            } else if (element == FETCH_PROPS) {
                myCurrentReportInfo.myIsFetchFile = true;
            } else if (element == SET_PROP || element == REMOVE_PROP) {
                info = pushState(State.PROP);
                String propName = attrs.getValue(NAME_ATTR);
                if (propName == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing name attr in {0} element", element.getName());
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                
                int colonIndex = propName.indexOf(':');
                
                String propNameSpace = null;
                if (colonIndex != -1) {
                    propNameSpace = propName.substring(0, colonIndex);
                    propName = propName.substring(colonIndex + 1);
                }
                
                info.myPropertyNameSpace = propNameSpace;
                info.myPropertyName = propName;
                info.myEncoding = attrs.getValue(ENCODING_ATTR); 
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "aborted in SerfEditorHandler: unexpected element met");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
                
            }
        } else if (myCurrentState == State.IGNORE_PROP_NAME) {
            pushState(State.PROP);
        } else if (myCurrentState == State.NEED_PROP_NAME) {
            ReportInfo info = pushState(State.PROP);
            info.myPropertyNameSpace = element.getNamespace();
            info.myPropertyName = element.getName();
        } 
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (myCurrentState == State.NONE) {
            return;
        }
        
        if ((myCurrentState == State.OPEN_DIR && element == OPEN_DIRECTORY) || 
                (myCurrentState == State.ADD_DIR && element == ADD_DIRECTORY)) {
            myCurrentReportInfo.myDir.myIsTagClosed = true;
            DAVProperties props = myCurrentReportInfo.myDir.myProperties;
            SVNPropertyValue checkedInURL = props.getPropertyValue(DAVElement.CHECKED_IN); 
            if (checkedInURL == null && (!SVNRevision.isValidRevisionNumber(myCurrentReportInfo.myDir.myBaseRevision) || 
                    myCurrentReportInfo.myDir.myIsFetchProps)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_OPTIONS_REQ_FAILED, 
                        "The OPTIONS response did not include the requested checked-in value");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            myCurrentReportInfo.myDir.myVSNURL = checkedInURL.getString();
            if (!SVNRevision.isValidRevisionNumber(myCurrentReportInfo.myDir.myBaseRevision) || 
                    myCurrentReportInfo.myDir.myIsFetchProps) {
                myCurrentReportInfo.myDir.myIsFetchProps = true;
                String label = SVNRevision.isValidRevisionNumber(myTargetRevision) ? Long.toString(myTargetRevision) : null;
                myCurrentReportInfo.myDir.myProperties = DAVUtil.getResourceProperties(myConnection, 
                        myCurrentReportInfo.myDir.myVSNURL, label, null);
                myActivePropFinds++;
            } else {
                
            }
            myStates.pop();
            myCurrentState = (State) myStates.peek();
        } else if (myCurrentState == State.OPEN_FILE && element == OPEN_FILE) {
            if (myCurrentReportInfo.myPath == null) {
                myCurrentReportInfo.myPath = SVNPathUtil.append(myCurrentReportInfo.myDir.myPath, 
                        myCurrentReportInfo.myBaseName);
            }
            
            myCurrentReportInfo.myLockToken = (String) myLockTokens.get(myCurrentReportInfo.myPath);
            if (myCurrentReportInfo.myLockToken != null && !myCurrentReportInfo.myIsFetchProps) {
                myCurrentReportInfo.myIsFetchProps = true;
            }
            
            SVNPropertyValue checkedInProp = myCurrentReportInfo.myProperties.getPropertyValue(DAVElement.CHECKED_IN); 
            String path = checkedInProp.getString();
            path = path.substring(0, path.length() - myCurrentReportInfo.myPath.length());
            String reposRoot = myOwner.getRepositoryRoot(true).toDecodedString();
            if (myDstPath != null && !myDstPath.equals(reposRoot)) {
                path = path.substring(path.length() - myDstPath.length() + reposRoot.length());
            }
            
            path = SVNPathUtil.removeTail(path);
            //path = SVNPathUtil.append(path, s)
        }
    }
    
    private void openDir(DirInfo dir) throws SVNException {
        if ("".equals(dir.myBaseName)) {
            myEditor.openRoot(dir.myBaseRevision);
        } else {
            openDir(dir.myParentDir);
            if (SVNRevision.isValidRevisionNumber(dir.myBaseRevision)) {
                myEditor.openDir(dir.myPath, dir.myBaseRevision);
            } else {
                myEditor.addDir(dir.myPath, null, SVNRepository.INVALID_REVISION);
            }
        }
    }
    
    private ReportInfo pushState(State state) {
        myCurrentState = state;
        myStates.push(myCurrentState);
        
        ReportInfo newInfo = null;
        
        if (state == State.OPEN_DIR || state == State.ADD_DIR) {
            newInfo = new ReportInfo();
            DirInfo dir = new DirInfo(); 
            newInfo.myDir = dir;
            dir.myProperties = new DAVProperties();
            newInfo.myProperties = dir.myProperties;
            dir.myRemovedProperties = new DAVProperties();
            if (myCurrentReportInfo != null) {
                myCurrentReportInfo.myDir.myRefCount++;
                newInfo.myDir.myParentDir = myCurrentReportInfo.myDir;
            } else {
                myRootDir = newInfo.myDir; 
            }
            myCurrentReportInfo = newInfo;
        } else if (state == State.OPEN_FILE || state == State.ADD_FILE) {
            newInfo = new ReportInfo();
            newInfo.myDir = myCurrentReportInfo.myDir;
            myCurrentReportInfo.myDir.myRefCount++;
            newInfo.myProperties = new DAVProperties();
            myCurrentReportInfo = newInfo;
        }
        return newInfo; 
    }
    
    private class ReportInfo {
        private String myBaseName;
        private String myPath;
        private String myVSNURL;
        private String myLockToken;
        private String myPropertyNameSpace;
        private String myPropertyName;
        private String myEncoding;
        private long myBaseRevision;
        private String myCopyFromPath;
        private long myCopyFromRevision;
        private boolean myIsFetchProps;
        private boolean myIsFetchFile;
        private DAVProperties myProperties;
        private DirInfo myDir; 
    }
    
    private class DirInfo {
        private boolean myIsFetchProps;
        private boolean myIsTagClosed;
        private Map myChildren;
        private String myVSNURL;
        private long myBaseRevision;
        private long myCopyFromRevision;
        private String myCopyFromPath;
        private String myBaseName;
        private String myPath;
        private int myRefCount;
        private DAVProperties myProperties;
        private DAVProperties myRemovedProperties;
        
        private DirInfo myParentDir;
        
    }

    private static class State {
        private static final State NONE = new State(0);
        private static final State OPEN_DIR = new State(1);
        private static final State ADD_DIR = new State(2);
        private static final State OPEN_FILE = new State(3);
        private static final State ADD_FILE = new State(4);
        private static final State PROP = new State(5);
        private static final State IGNORE_PROP_NAME = new State(6);
        private static final State NEED_PROP_NAME = new State(7);
        
        private int myID;
        
        public State(int id) {
            myID = id;
        }
    }
}
