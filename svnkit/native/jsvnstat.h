/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
#include <jni.h>
/* Header for class org_tmatesoft_svn_core_internal_wc_SVNStatHelper */

#ifndef _Included_org_tmatesoft_svn_core_internal_wc_SVNStatHelper
#define _Included_org_tmatesoft_svn_core_internal_wc_SVNStatHelper
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jintArray JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getAttributes
  (JNIEnv *, jclass, jstring, jboolean);

JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_changeMode
  (JNIEnv *, jclass, jstring, jboolean, jboolean, jboolean, jboolean);

JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_link
  (JNIEnv *, jclass, jstring, jstring);

JNIEXPORT jstring JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getLinkTargetPath
  (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif
