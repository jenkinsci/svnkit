/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.ws.fs;

import java.util.*;

import de.regnis.q.sequence.*;

/**
 * @author TMate Software Ltd.
 */
final class FSMergerBySequenceList {

    // Fields =================================================================

    private final List myBlocks;

    private int myPosition;

    // Setup ==================================================================

    public FSMergerBySequenceList(List blocks) {
        this.myBlocks = blocks;
        this.myPosition = 0;
    }

    // Accessing ==============================================================

    public boolean hasCurrent() {
        return myPosition < myBlocks.size();
    }

    public QSequenceDifferenceBlock current() {
        return (QSequenceDifferenceBlock) myBlocks.get(myPosition);
    }

    public boolean hasNext() {
        return myPosition + 1 < myBlocks.size();
    }

    public QSequenceDifferenceBlock peekNext() {
        return myPosition + 1 < myBlocks.size() ? (QSequenceDifferenceBlock) myBlocks
                .get(myPosition + 1)
                : null;
    }

    public void forward() {
        myPosition++;
    }
}