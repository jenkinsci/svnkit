/*
 * ====================================================================
 * Copyright (c) 2004 Marc Strapetz, marc.strapetz@smartsvn.com. 
 * All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence;

import java.util.*;
import junit.framework.*;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLCSTest extends TestCase {

	// Accessing ==============================================================

	public void test() throws QSequenceException {
		test1("abcabba", "cbabac", "cbba");
		test1("cbabac", "abcabba", "cbba");

		test2("abccd", "abccd", "abccd");
		test2("ab*", "ab", "ab");
		test2("*abccd", "abccd", "abccd");
		test2("***abccd", "abccd", "abccd");
		test2("abc*d", "abc+d", "abcd");
		test2("abc*d", "abcd", "abcd");
		test2("abc*d", "abc++d", "abcd");
		test2("abc*d", "abc+++d", "abcd");
		test2("abc*d", "ab+c++++d", "abcd");
		test2("ab", "ccd", "");
		test2("ab", "bccd", "b");
		test2("******a*****b*****c*c***d", "abccd", "abccd");
		test2("******a*****b*****c*c***d", "+a+b+c+c+d+", "abccd");

		test2("a", "***a", "a");
		test2("a", "****a", "a");
		test2("a", "a****", "a");
		test2("", "", "");
		test2("a", "", "");
		test2("aaaaaaaaa", "", "");
		test2("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "a", "a");

		test2("This is a small test with some minor variations.", "Thies ist a smal text with some minbr variation.", "This is a smal tet with some minr variation.");
	}

	// Utils ==================================================================

	private void test2(String left, String right, String lcs) throws QSequenceException {
		test1(left, right, lcs);
		test1(right, left, lcs);
	}

	private void test1(String left, String right, String lcs) throws QSequenceException {
		final QSequenceTestMedia media = QSequenceTestMedia.createCharacterMedia(left, right);
		final List commands = new QSequenceSimpleLCS(media).getCommands();
		int lastLeft = -1;
		int lastRight = -1;

		final StringBuffer calculatedLcs = new StringBuffer();
		for (Iterator it = commands.iterator(); it.hasNext();) {
			final QSequenceSimpleLCSCommand command = (QSequenceSimpleLCSCommand)it.next();
			if (command.isLeft()) {
				assertTrue(lastLeft <= command.getFrom());
				for (int index = command.getFrom(); index <= command.getTo(); index++) {
					calculatedLcs.append(media.getLeftLine(index));
				}
				lastLeft = command.getTo();
			}
			else {
				assertTrue(lastRight <= command.getFrom());
				for (int index = command.getFrom(); index <= command.getTo(); index++) {
					calculatedLcs.append(media.getRightLine(index));
				}
				lastRight = command.getTo();
			}
		}

		assertEquals(lcs, calculatedLcs.toString());
	}
}