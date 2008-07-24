/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineRandomAccessFileFactory {

	// Static =================================================================

	private static final Map fileToRaFile = new HashMap();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				for (Iterator it = fileToRaFile.keySet().iterator(); it.hasNext();) {
					final File file = (File)it.next();
					final RandomAccessFile raFile = (RandomAccessFile)fileToRaFile.get(file);

					try {
						raFile.close();
						file.delete();
					}
					catch (IOException ex) {
					}
				}

				super.run();
			}
		});
	}

	public static RandomAccessFile createRandomAccessFile(File file, String mode) throws FileNotFoundException {
		final RandomAccessFile raFile = new RandomAccessFile(file, mode);
		fileToRaFile.put(file, raFile);
		return raFile;
	}
}
