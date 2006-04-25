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
package org.tmatesoft.svn.examples.repository;

import java.io.ByteArrayInputStream;
import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/*
 * 
 * r1 by 'me' at Wed Apr 26 02:10:14 NOVST 2006
   r2 by 'me' at Wed Apr 26 02:10:14 NOVST 2006
   r3 by 'me' at Wed Apr 26 02:10:15 NOVST 2006
   r4 by 'me' at Wed Apr 26 02:10:15 NOVST 2006
   
   The latest source revision: 4
   
   'file:///G:/tgtRepository' repository tree:

   /dirA (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/dirB (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/dirB/fileB.txt (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/fileA.txt (author: 'me'; revision: 2; date: Wed Apr 26 02:10:14 NOVST 2006)
   
   Number of replicated revisions: 4
   
   'file:///G:/tgtRepository' repository tree:

   /dirA (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/dirB (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/dirB/fileB.txt (author: 'me'; revision: 4; date: Wed Apr 26 02:10:15 NOVST 2006)
   /dirA/fileA.txt (author: 'me'; revision: 2; date: Wed Apr 26 02:10:14 NOVST 2006)
 */
public class Replicate {

    /*
     * 
     */
    public static void main(String[] args) {
        /*
         * Default values:
         */
        String srcPath = "srcRepository";
        String tgtPath = "tgtRepository";
        
        /*
         * Initializes the library (it must be done before ever using the
         * library itself)
         */
        setupLibrary();

        if (args != null) {
            /*
             * a source repository url
             */
            srcPath = (args.length >= 1) ? args[0] : srcPath;
            /*
             * a target repository url
             */
            tgtPath = (args.length >= 2) ? args[1] : tgtPath;
        }

        
        SVNURL srcURL = null;
        SVNURL tgtURL = null;
        SVNRepository srcRepository = null;
        SVNRepository tgtRepository = null;
        
        try {
            srcURL = SVNRepositoryFactory.createLocalRepository(new File(srcPath), false, false);
            tgtURL = SVNRepositoryFactory.createLocalRepository(new File(tgtPath), true, false);
            
            srcRepository = SVNRepositoryFactory.create(srcURL);
            tgtRepository = SVNRepositoryFactory.create(tgtURL);
        } catch (SVNException svne) {
            /*
             * getFullMessage() will give us a full tree of SVNException 
             * errors.
             */
            System.err.println("Can not create a repository: " + svne.getErrorMessage().getFullMessage());
            System.exit(1);
        }

        srcRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        tgtRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        
        try{
            populateSourceRepository(srcRepository);
            System.out.println();
            long srcLatestRevision = srcRepository.getLatestRevision();
            System.out.println("The latest source revision: " + srcLatestRevision);
            System.out.println();
            System.out.println("'" + srcURL + "' repository tree:");
            System.out.println();
            DisplayRepositoryTree.listEntries(srcRepository, "");
        }catch(SVNException svne){
            System.err.println("An error occurred while accessing source repository: " + svne.getErrorMessage().getFullMessage());
            System.exit(1);
        }

        try{
            long replicatedRevisions = replicateRepository(srcRepository, tgtRepository);
            System.out.println();
            System.out.println("Number of replicated revisions: " + replicatedRevisions);
            System.out.println();
            System.out.println("'" + tgtURL + "' repository tree:");
            System.out.println();
            DisplayRepositoryTree.listEntries(tgtRepository, "");
        }catch(SVNException svne){
            System.err.println("An error occurred while accessing source repository: " + svne.getErrorMessage().getFullMessage());
            System.exit(1);
        }
        
    }

    private static void populateSourceRepository(SVNRepository srcRepository) throws SVNException {
        /*
         * Simple repository tree to create. Each entry will be 
         * added in its own revision.
         */
        String dirA = "dirA";
        String dirB = "dirA/dirB";
        String fileA = "dirA/fileA.txt";
        String fileB = "dirA/dirB/fileB.txt";
        byte[] fileAContents = "This is file fileA.txt".getBytes();
        byte[] fileBContents = "This is file fileB.txt".getBytes();
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        long revision = -1;
        SVNCommitInfo info = null;
        String checksum = null;
        
        /*
         * First commit "/dirA".
         */
        ISVNEditor editor = srcRepository.getCommitEditor("adding " + dirA, null);
        editor.openRoot(-1);
        editor.addDir(dirA, null, -1);
        editor.closeDir();
        editor.closeDir();
        info = editor.closeEdit();
        System.out.println(info);
        revision = info.getNewRevision();
        
        /*
         * Then commit "/dirA/fileA.txt".
         */
        editor = srcRepository.getCommitEditor("adding " + fileA, null);
        editor.openRoot(-1);
        editor.openDir(dirA, revision);
        editor.addFile(fileA, null, -1);
        editor.applyTextDelta(fileA, null);
        checksum = deltaGenerator.sendDelta(fileA, new ByteArrayInputStream(fileAContents), editor, true);
        editor.closeFile(fileA, checksum);
        editor.closeDir();
        editor.closeDir();
        info = editor.closeEdit();
        System.out.println(info);
        revision = info.getNewRevision();

        /*
         * Then commit "/dirA/dirB".
         */
        editor = srcRepository.getCommitEditor("adding " + dirB, null);
        editor.openRoot(-1);
        editor.openDir(dirA, revision);
        editor.addDir(dirB, null, -1);
        editor.closeDir();
        editor.closeDir();
        editor.closeDir();
        info = editor.closeEdit();
        System.out.println(info);
        revision = info.getNewRevision();
        
        /*
         * Then commit "/dirA/dirB/fileB.txt".
         */
        editor = srcRepository.getCommitEditor("adding " + fileB, null);
        editor.openRoot(-1);
        editor.openDir(dirA, revision);
        editor.openDir(dirB, revision);
        editor.addFile(fileB, null, -1);
        editor.applyTextDelta(fileB, null);
        checksum = deltaGenerator.sendDelta(fileB, new ByteArrayInputStream(fileBContents), editor, true);
        editor.closeFile(fileB, checksum);
        editor.closeDir();
        editor.closeDir();
        editor.closeDir();
        info = editor.closeEdit();
        System.out.println(info);
    }

    private static long replicateRepository(SVNRepository srcRepository, SVNRepository tgtRepository) throws SVNException {
        long latestRevision = srcRepository.getLatestRevision();
        SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
        return replicator.replicateRepository(srcRepository, tgtRepository, 1, latestRevision);
    }
    
    /*
     * Initializes the library to work with a repository via 
     * different protocols.
     */
    private static void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();
        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }

}
