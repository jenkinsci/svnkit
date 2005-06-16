package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 17.06.2005
 * Time: 0:33:01
 * To change this template use File | Settings | File Templates.
 */
public class SVNImportMediator implements ISVNWorkspaceMediator {

    private File myRoot;
    private Map myLocations;

    public SVNImportMediator(File root) {
        myRoot = root;
        myLocations = new HashMap();
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        return null;
    }
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
    }

    public OutputStream createTemporaryLocation(String path, Object id) throws IOException {
        File tmpFile = SVNFileUtil.createUniqueFile(myRoot, PathUtil.tail(path), ".tmp");
        OutputStream os;
        try {
            os = SVNFileUtil.openFileForWriting(tmpFile);
        } catch (SVNException e) {
            throw new IOException(e.getMessage());
        }
        myLocations.put(id, tmpFile);
        return os;
    }

    public InputStream getTemporaryLocation(Object id) throws IOException {
        File file = (File) myLocations.get(id);
        if (file != null) {
            try {
                return SVNFileUtil.openFileForReading(file);
            } catch (SVNException e) {
                throw new IOException(e.getMessage());
            }
        }
        return null;
    }

    public long getLength(Object id) throws IOException {
        File file = (File) myLocations.get(id);
        if (file != null) {
            return file.length();
        }
        return 0;
    }

    public void deleteTemporaryLocation(Object id) {
        File file = (File) myLocations.remove(id);
        if (file != null) {
            file.delete();
        }
    }

    public void deleteAdminFiles(String path) {
    }
}
