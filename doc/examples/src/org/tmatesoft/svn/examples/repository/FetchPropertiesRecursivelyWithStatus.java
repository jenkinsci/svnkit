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
package org.tmatesoft.svn.examples.repository;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class FetchPropertiesRecursivelyWithStatus {

    /**
     * Demonstration of recursive fetching properties from a repository tree using only low-level 
     * SVNKit API. This example fetches properties from the SVNKit's project repository which is publicly 
     * readable. So, you may copy-paste this example and run it as is.
     */
    public static void main(String[] args) {
        //initialize the DAV protocol
        DAVRepositoryFactory.setup();
        
        try {
            //create a repository access object
            SVNRepository repos = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("https://svn.svnkit.com/repos/svnkit/trunk"));

            //get the current HEAD revision
            final long rev = repos.getLatestRevision(); 
            
            //with this reporter we just say to the repository server - please, send us the entire tree, 
            //we do not have any local data
            ISVNReporterBaton reporter = new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                 
                    reporter.setPath("", null, rev, SVNDepth.INFINITY, 
                            true/*we are empty, take us all like in checkout*/);
                 
                    reporter.finishReport();
                
                }
            }; 
            
            //our editor only stores properties of files and directories
            PropFetchingEditor editor = new PropFetchingEditor();
            
            //run an update-like request which never receives any real file deltas
            repos.status(rev, null, SVNDepth.INFINITY, reporter, editor);

            //now iterate over file and directory properties and print them out to the console
            Map dirProps = editor.getDirsToProps();
            for (Iterator dirPathsIter = dirProps.keySet().iterator(); dirPathsIter.hasNext();) {
                String path = (String) dirPathsIter.next();
                Map props = (Map) dirProps.get(path);
                System.out.println("Directory '" + path + "' has the following properties:");
                for (Iterator propNamesIter = props.keySet().iterator(); propNamesIter.hasNext();) {
                    String propName = (String) propNamesIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) props.get(propName);
                    System.out.println("  '" + propName + "' = '" + SVNPropertyValue.getPropertyAsString(propValue) + "'");
                }
                System.out.println();
            }

            Map fileProps = editor.getFilesToProps();
            for (Iterator filePathsIter = fileProps.keySet().iterator(); filePathsIter.hasNext();) {
                String path = (String) filePathsIter.next();
                Map props = (Map) fileProps.get(path);
                System.out.println("File '" + path + "' has the following properties:");
                for (Iterator propNamesIter = props.keySet().iterator(); propNamesIter.hasNext();) {
                    String propName = (String) propNamesIter.next();
                    SVNPropertyValue propValue = (SVNPropertyValue) props.get(propName);
                    System.out.println("  '" + propName + "' = '" + SVNPropertyValue.getPropertyAsString(propValue) + "'");
                }
                System.out.println();
            }
            
        } catch (SVNException svne) {
            System.out.println(svne.getErrorMessage().getFullMessage());
            System.exit(1);
        }
        
    }
    
    private static class PropFetchingEditor implements ISVNEditor {
        private Stack myDirectoriesStack = new Stack();
        private Map myDirProps = new HashMap();
        private Map myFileProps = new HashMap();
        
        public void abortEdit() throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void closeFile(String path, String textChecksum) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            return null;
        }

        public void textDeltaEnd(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
            String absouluteDirPath = "/" + path;
            myDirectoriesStack.push(absouluteDirPath);
        }

        public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
            //filter out svn:entry and svn:wc properties since we are interested in regular properties only
            if (!SVNProperty.isRegularProperty(name)) {
                return;
            }

            String currentDirPath = (String) myDirectoriesStack.peek();
            Map props = (Map) myDirProps.get(currentDirPath);
            if (props == null) {
                props = new HashMap();
                myDirProps.put(currentDirPath, props);
            }
            props.put(name, value);
        }

        public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
            //filter out svn:entry and svn:wc properties since we are interested in regular properties only
            if (!SVNProperty.isRegularProperty(propertyName)) {
                return;
            }

            String absolutePath = "/" + path;
            Map props = (Map) myFileProps.get(absolutePath);
            if (props == null) {
                props = new HashMap();
                myFileProps.put(absolutePath, props);
            }
            props.put(propertyName, propertyValue);
        }

        public void closeDir() throws SVNException {
            myDirectoriesStack.pop();
        }

        public void openDir(String path, long revision) throws SVNException {
            String absoluteDirPath = "/" + path;
            myDirectoriesStack.push(absoluteDirPath);
        }

        public void openRoot(long revision) throws SVNException {
            String absoluteDirPath = "/";  
            myDirectoriesStack.push(absoluteDirPath);
        }

        public Map getDirsToProps() {
            return myDirProps;
        }
        
        public Map getFilesToProps() {
            return myFileProps;
        }
    }
}
