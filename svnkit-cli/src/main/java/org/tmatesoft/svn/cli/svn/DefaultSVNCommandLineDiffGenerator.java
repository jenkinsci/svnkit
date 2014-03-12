/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultSVNCommandLineDiffGenerator extends DefaultSVNDiffGenerator {
    
    private File myFile1;
    private File myFile2;
    
    public DefaultSVNCommandLineDiffGenerator(File file1, File file2) {
        myFile1 = file1;
        myFile2 = file2;
    }
    
    protected void displayHeaderFields(OutputStream os, String label1, String label2) throws IOException {
        Date time1 = new Date(SVNFileUtil.getFileLastModified(myFile1));
        Date time2 = new Date(SVNFileUtil.getFileLastModified(myFile2));
        String timestamp1 = SVNDate.formatConsoleDiffDate(time1);
        String timestamp2 = SVNDate.formatConsoleDiffDate(time2);
        String file1 = myFile1.getAbsolutePath();
        String file2 = myFile2.getAbsolutePath();
        
        os.write("--- ".getBytes(getEncoding()));
        os.write(file1.getBytes(getEncoding()));
        os.write("\t".getBytes(getEncoding()));
        os.write(timestamp1.getBytes(getEncoding()));
        os.write(getEOL());
        os.write("+++ ".getBytes(getEncoding()));
        os.write(file2.getBytes(getEncoding()));
        os.write("\t".getBytes(getEncoding()));
        os.write(timestamp2.getBytes(getEncoding()));
        os.write(getEOL());
    }
    
    protected boolean displayHeader(OutputStream os, String path, boolean deleted) throws IOException {
        return false;
    }

    protected boolean isHeaderForced(File file1, File file2) {
        return false;
    }

}
