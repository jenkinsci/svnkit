/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminUtil {
    
    private static final byte[] FORMAT_TEXT;
    private static final byte[] README_TEXT;
    private static final boolean SKIP_README;
    
    static {
        String eol = System.getProperty("line.separator");
        FORMAT_TEXT = new byte[] {'4', '\n'};
        README_TEXT = ("This is a Subversion working copy administrative directory." + eol
            + "Visit http://subversion.tigris.org/ for more information." + eol).getBytes();
        SKIP_README = Boolean.getBoolean("javasvn.skipReadme");
    }
    
    public static void createReadmeFile(File adminDir) throws SVNException {
        if (SKIP_README) {
            return;
        }
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(new File(adminDir, "README.txt"));
            os.write(README_TEXT);            
        } catch (IOException e) {
            SVNErrorManager.error("svn: Can not write README.txt file: '" + e.getMessage() + "'");
        } finally {
            SVNFileUtil.closeFile(os);
        }
        
    }

    public static void createFormatFile(File adminDir) throws SVNException {
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(new File(adminDir, "format"));
            os.write(FORMAT_TEXT);            
        } catch (IOException e) {
            SVNErrorManager.error("svn: Can not write format file: '" + e.getMessage() + "'");
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }


}
