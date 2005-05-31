/*
 * Created on 31.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

public class SVNWCUtil {
    
    public static String getURL(File versionedFile) {
        SVNWCAccess wcAccess;
        try {
            wcAccess = SVNWCAccess.create(versionedFile);
            return wcAccess.getTargetEntryProperty(SVNProperty.URL);
        } catch (SVNException e) {
        }
        return null;
    }

    public static boolean isVersionedDirectory(File dir) {
        return SVNWCAccess.isVersionedDirectory(dir);
    }

    public static void getBaseFileContents(File versionedFile, OutputStream dst) throws SVNException {
        String name = versionedFile.getName();
        SVNWCAccess wcAccess = SVNWCAccess.create(versionedFile);
        File file = wcAccess.getAnchor().getFile(name, false);
        
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            int r;
            while((r = is.read()) >= 0) {
                dst.write(r);
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);            
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }        
    }

    public static void getWorkingFileContents(File versionedFile, OutputStream dst) throws SVNException {
        String name = versionedFile.getName();
        SVNWCAccess wcAccess = SVNWCAccess.create(versionedFile);
        File root = wcAccess.getAnchor().getFile(".svn/tmp/text-base", false);
        File tmpFile = null;
        try { 
            tmpFile = SVNFileUtil.createUniqueFile(root, name, ".tmp");
            String tmpPath = SVNFileUtil.getBasePath(tmpFile);
            SVNTranslator.translate(wcAccess.getAnchor(), name, name, tmpPath, false, false);
            
            InputStream is = null;
            try {
                is = new FileInputStream(tmpFile);
                int r;
                while((r = is.read()) >= 0) {
                    dst.write(r);
                }
            } catch (IOException e) {
                SVNErrorManager.error(0, e);            
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
    }

    public static boolean isBinaryMimetype(String mimetype) {
        return mimetype != null && !mimetype.startsWith("text/");
    }
}