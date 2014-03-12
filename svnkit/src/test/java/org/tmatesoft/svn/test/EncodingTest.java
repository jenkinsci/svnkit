package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

public class EncodingTest {

    @Test
    public void testFuzzyEscapeNonASCIINonControlChars() throws Exception {
        String originalString = "\u770b\u5168\u90e8";
        String escapedString = SVNEncodingUtil.fuzzyEscape(originalString);

        Assert.assertEquals(originalString, escapedString);
    }
}
