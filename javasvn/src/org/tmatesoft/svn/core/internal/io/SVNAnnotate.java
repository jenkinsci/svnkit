/*
 * Created on Mar 3, 2005
 */
package org.tmatesoft.svn.core.internal.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceLine;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceLineReader;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceMedia;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.util.DebugLog;

import de.regnis.q.sequence.QSequenceDifferenceBlock;

public class SVNAnnotate implements ISVNFileRevisionHandler {
	
	private Map myDiffWindowsMap;
	private List myDiffWindowsList;

	private long myRevision;
	private String myAuthor;
	
	private SVNRAFileData myTempFile;
	private SVNRAFileData myBaseFile;
	private String myTempFilePath;
	private String myBaseFilePath;
	
	private List myLines;
	private ISVNAnnotateHandler myHandler;
	
	public SVNAnnotate() {
	}
	
	public void setAnnotateHandler(ISVNAnnotateHandler handler) {
		myHandler = handler;		
	}
	
	private void createBaseFile() {
		if (myBaseFilePath == null) {
			try {
				File baseFile = File.createTempFile("javasvn.", ".temp");
				myBaseFilePath = baseFile.getAbsolutePath();
				baseFile.deleteOnExit();
				myBaseFile = new SVNRAFileData(baseFile, false);		
			} catch (IOException e) {
				DebugLog.error(e);
			}
		}
	}

	public void hanldeFileRevision(SVNFileRevision fileRevision) {
		createBaseFile();
		if (myLines == null) {
			myLines = new ArrayList();
		}
		myDiffWindowsMap = null;
		myDiffWindowsList = null;
		myRevision = fileRevision.getRevision();
		myAuthor = fileRevision.getProperties().get("svn:author").toString();
		try {
			File tempFile = File.createTempFile("javasvn.", ".temp");
			myTempFilePath = tempFile.getAbsolutePath();
			tempFile.deleteOnExit();
			myTempFile = new SVNRAFileData(tempFile, false);
		} catch (IOException e) {
			DebugLog.error(e);
		}
	}
	
	public void dispose() {
		if (myBaseFilePath != null) {
			new File(myBaseFilePath).delete();
		}
		myBaseFilePath = null;
		myTempFilePath = null;
		if (myLines != null && myHandler != null) {
			for(int i = 0; i < myLines.size(); i++) {
				LineInfo info = (LineInfo) myLines.get(i);
				myHandler.handleLine(info.revision, info.author, info.line);
			}
		}
		myLines = null;
	}

	public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) {
		if (diffWindow == null) {
			return null;
		}
		File tmpFile = null;
		try {
			tmpFile = File.createTempFile("javasvn.", ".diff");
			tmpFile.deleteOnExit();
		} catch (IOException e) {
			DebugLog.error(e);
			return null;
		}
		if (myDiffWindowsMap == null) {
			myDiffWindowsMap = new HashMap();
			myDiffWindowsList = new LinkedList();
		}
		myDiffWindowsMap.put(diffWindow, tmpFile);
		myDiffWindowsList.add(diffWindow);
		try {
			return new FileOutputStream(tmpFile);
		} catch (FileNotFoundException e) {
			DebugLog.error(e);
		}
		return null;
	}

	public void hanldeDiffWindowClosed(String token) {
		for(int i = 0; i < myDiffWindowsList.size(); i++) {
			SVNDiffWindow diffWindow = (SVNDiffWindow) myDiffWindowsList.get(i);
			File dataFile = (File) myDiffWindowsMap.get(diffWindow);
			InputStream is = null;
			try {
				is = new FileInputStream(dataFile);
				diffWindow.apply(myBaseFile, myTempFile, is, myTempFile.length());
			} catch (SVNException e) {
				DebugLog.error(e);
			} catch (IOException e) {
				DebugLog.error(e);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						DebugLog.error(e);
					}
				}
			}
		}
		
		InputStream left = null;
		InputStream right = null;
		try {
			left = new FileInputStream(myBaseFilePath);
			right = new FileInputStream(myTempFilePath);
			
			SVNSequenceLineReader reader = new SVNSequenceLineReader(null);

	        SVNSequenceLine[] leftLines = reader.read(left);
	        SVNSequenceLine[] rightLines = reader.read(right);
			ArrayList newLines = new ArrayList();
			int lastStart = 0;
			
	        List blocksList = SVNSequenceMedia.createBlocks(leftLines, rightLines);
			for(int i = 0; i < blocksList.size(); i++) {
				QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) blocksList.get(i);
				// remove these lines from lines map...
				// copy from last start to start.
				
				int start = block.getLeftFrom();
				int end = block.getLeftTo();
				for(int j = lastStart; j < Math.min(myLines.size(), start); j++) {
					newLines.add(myLines.get(j));
					lastStart = j + 1;
				}
				// skip it.
				if (block.getLeftSize() > 0) {
					lastStart += block.getLeftSize();
				}
				// copy all from right.
				for (int j = block.getRightFrom(); j <= block.getRightTo(); j++) {
					LineInfo line = new LineInfo();
					line.revision = myRevision;
					line.author = myAuthor;
					line.line = new String(rightLines[j].getBytes());
					newLines.add(line);
				}
			}
			for(int j = lastStart; j < myLines.size(); j++) {
				newLines.add(myLines.get(j));
			}
			myLines = newLines;
		} catch (Throwable e) {
			
			e.printStackTrace(System.out);
			DebugLog.error(e);
		} finally {
			if (left != null) {
				try {
					left.close();
				} catch (IOException e) {}
			}
			if (right != null) {
				try {
					right.close();
				} catch (IOException e) {}
			}
		}

		FSUtil.copy(new File(myTempFilePath), new File(myBaseFilePath), null, null, null);
		new File(myTempFilePath).delete();
	}

	private void dumpLines() {
		System.out.println();
		System.out.println("LINES: (" + myLines.size() + ")");
		NumberFormat nf = NumberFormat.getIntegerInstance();
		nf.setMinimumIntegerDigits(6);
		int count = 0;
		for(int i = 0; i < myLines.size(); i++) {
			LineInfo info = (LineInfo) myLines.get(i);
			System.out.print(nf.format(info.revision) + "      " + info.author + " " + info.line);
		}
	}

	
	private static class LineInfo {
		public String line;
		public long revision;
		public String author; 
	}
}
