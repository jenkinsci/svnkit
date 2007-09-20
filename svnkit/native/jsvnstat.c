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
// jsvncrypt.c: Defines the entry point for the dll application.
//
#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include "jsvnstat.h"

/*
 * return values:
 *   1 - ordinar file
 *   2 - directory
 *   3 - symlink
 *   4 - unknown
 *  -1 - error occurres during system call 
 * 
 */
JNIEXPORT jint JNICALL Java_org_tmatesoft_svn_core_internal_wc_SVNStatHelper_getType 
    (JNIEnv *env, jclass clazz, jstring path, jboolean find_links) {
    
    struct stat info;
    int srv, ret_val;
    const char* p_path;
	mode_t mode;

    p_path = (char*)((*env)->GetStringUTFChars(env, path, 0));
    
    if (find_links)
        srv = lstat(p_path, &info);
    else
        srv = stat(p_path, &info);

    if (srv == 0) {
    	mode = info.st_mode;
    	
    	switch (mode & S_IFMT) {
        case S_IFREG:
            ret_val = 1;  
            break;
        case S_IFDIR:
            ret_val = 2;  
            break;
        case S_IFLNK:
            ret_val = 3;  
            break;
        default:
            ret_val = 4;  
            break;
        }
    } else {
    	ret_val = -1;
    }

    (*env)->ReleaseStringUTFChars(env, path, p_path);
    return ret_val;
}
  
