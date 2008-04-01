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

package de.regnis.q.sequence;

/**
 * @author Marc Strapetz
 */
public interface QSequenceCommonBlockFactory {

	Object createCommonBlock(int leftFrom, int leftTo, int rightFrom, int rightTo);

	Object createDistinctBlock(int leftFrom, int leftTo, int rightFrom, int rightTo);
}