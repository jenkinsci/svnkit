package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author Marc Strapetz
 */
public interface ISVNConnector {
    public void open(SVNRepositoryImpl repository) throws SVNException;

    public void close() throws SVNException;

    public OutputStream getOutputStream() throws IOException;

    public InputStream getInputStream() throws IOException;
}