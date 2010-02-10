/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.patch;

import java.io.File;
import java.io.OutputStream;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNPatchFileStream {
    
    public interface SVNPatchFileLineFilter {
        boolean lineFilter(String line);
    }

    public interface SVNPatchFileLineTransformer {
        String lineTransformer(String line);
    }

    public static SVNPatchFileStream openReadOnly(File patchPath) {
        return null;
    }

    public static SVNPatchFileStream openRangeReadOnly(File path, long start, long end) {
        return null;
    }

    public static SVNPatchFileStream openForWrite(File rejectPath) {
        return null;
    }
    
    public void close() {
    }

    public boolean isEOF() {
        return false;
    }

    public File getPath() {
        return null;
    }

    public long getSeekPosition() {
        return 0;
    }

    public void setSeekPosition(long pos) {
    }
    
    public void setLineFilter(SVNPatchFileLineFilter lineFilter) {
        
    }
    
    public void setLineTransformer(SVNPatchFileLineTransformer lineTransfomer) {
        
    }

    public void write(String string) {
    }

    /* Attempt to write LEN bytes of DATA to STREAM, the underlying file
     * of which is at ABSPATH. Fail if not all bytes could be written to
     * the stream. Do temporary allocations in POOL. */
    public void tryWrite(String string) {
    }

    public boolean readLineWithEol(StringBuffer line, StringBuffer eol) {
        return false;
    }

    public void tryWrite(StringBuffer line) {
    }

    public void write(StringBuffer hunkHeader) {
    }

    public boolean readLine(StringBuffer lineBuf) {
        return false;
    }

    public static SVNPatchFileStream wrapOutputStream(OutputStream outputStream) {
        return null;
    }

    public boolean readLine(StringBuffer lineRaw, String eolStr) {
        return false;
    }

    public void reset() {
    }

}
