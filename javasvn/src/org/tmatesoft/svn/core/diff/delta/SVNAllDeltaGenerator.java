package org.tmatesoft.svn.core.diff.delta;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author Marc Strapetz
 */
public class SVNAllDeltaGenerator implements ISVNDeltaGenerator {

	// Accessing ==============================================================

	public void generateDiffWindow(ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
        long length = workFile.length();
		SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(length);
        DebugLog.log("NEW FILE LENGTH: " + length);
		OutputStream os = consumer.textDeltaChunk(window);
        OutputStream fos = null;
        if (length == 0) {
            try {
                os.close();
            } catch (IOException e1) {
            }
            consumer.textDeltaEnd();
            return;
        }
		InputStream is = null;
		try {
			is = new BufferedInputStream(workFile.read(0, workFile.length()));
			FSUtil.copy(is, os, null, null);
            if (DebugLog.isSafeMode()) {
                ourTmpFile = File.createTempFile("svn.", ".diff");
                fos = new BufferedOutputStream(new FileOutputStream(ourTmpFile)); 
                SVNDiffWindowBuilder.save(window, fos);
                is = new BufferedInputStream(workFile.read(0, workFile.length()));
                FSUtil.copy(is, fos, null, null);
            }
		}
		catch (IOException e) {
			throw new SVNException(e);
		}
		finally {
            try {
                os.close();
            }
            catch (IOException e) {
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            }
            catch (IOException e) {
            }
			if (is != null) {
				try {
					is.close();
				}
				catch (IOException e) {
				}
			}
		}
		consumer.textDeltaEnd();
	}
    
    private static File ourTmpFile;
    
    public static File lastTempFile() {
        return ourTmpFile;
    }
}