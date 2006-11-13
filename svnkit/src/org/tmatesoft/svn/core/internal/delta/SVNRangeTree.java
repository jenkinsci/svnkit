/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.delta;



/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRangeTree {
    
    private SVNRangeTreeNode myRoot = null;
    
    private SVNRangeTreeNode myFreeTreeNodes;
    private SVNRangeTreeNode myAllocatedTreeNodes;
    private SVNRangeListNode myFreeListNodes;
    
    public static class SVNRangeTreeNode {
        
        public SVNRangeTreeNode(int offset, int limit, int target) {
            this.offset = offset;
            this.limit = limit;
            this.targetOffset = target;
        }
        
        public String toString() {
            String str = offset + ":" + limit + ":" + targetOffset;
            return str;
        }
        
        public int offset;
        public int limit;
        public int targetOffset;
        
        public SVNRangeTreeNode left;
        public SVNRangeTreeNode right;
        public SVNRangeTreeNode prev;
        public SVNRangeTreeNode next;
        
        public SVNRangeTreeNode nextFree;
    }
    
    private SVNRangeTreeNode allocateTreeNode(int offset, int limit, int target) {
        if (myFreeTreeNodes == null) {
            SVNRangeTreeNode node = new SVNRangeTreeNode(offset, limit, target);
            node.nextFree = myAllocatedTreeNodes;
            myAllocatedTreeNodes = node;
            return node;
        }
        SVNRangeTreeNode node = myFreeTreeNodes;
        myFreeTreeNodes = node.nextFree;
        node.left = node.right = node.next = node.prev = null;
        node.offset = offset;
        node.limit = limit;
        node.targetOffset = target;
        
        // make it head of the allocated list.
        node.nextFree = myAllocatedTreeNodes;
        myAllocatedTreeNodes = node;
        return node;
    }

    private void freeTreeNode(SVNRangeTreeNode node) {
        if (node.next != null) {
            node.next.prev = node.prev;
            node.next = null;
        }
        if (node.prev != null) {
            node.prev.next = node.next;
            node.prev = null;
        }
        // remove if from the allocated list, it has to be there.
        if (myAllocatedTreeNodes == node) {
            myAllocatedTreeNodes = myAllocatedTreeNodes.nextFree;
        } else {
            SVNRangeTreeNode allocated = myAllocatedTreeNodes;
            while(allocated.nextFree != node) {
                allocated = allocated.nextFree;
            }
            allocated.nextFree = node.nextFree;
        }
        // make it head of the free nodes list.
        node.nextFree = myFreeTreeNodes;
        myFreeTreeNodes = node;
    }

    private SVNRangeListNode allocateListNode(int kind, int offset, int limit, int target) {
        if (myFreeListNodes == null) {
            return new SVNRangeListNode(kind, offset, limit, target);
        }
        SVNRangeListNode node = myFreeListNodes;
        myFreeListNodes = node.next;
        node.offset = offset;
        node.limit = limit;
        node.targetOffset = target;
        node.kind = kind;
        node.prev = node.next = null;
        node.head = node;
        return node;
    }
    
    public void disposeList(SVNRangeListNode head) {
        SVNRangeListNode n = head;
        while(head.next != null) {
            head = head.next;
        }
        head.next = myFreeListNodes;
        myFreeListNodes = n;
    }
    
    public void dispose() {
        SVNRangeTreeNode node = myFreeTreeNodes;
        if (node == null) {
            myFreeTreeNodes = myAllocatedTreeNodes;
        } else {
            while(node.nextFree != null) {
                node = node.nextFree;
            }
            node.nextFree = myAllocatedTreeNodes;
        }
        myAllocatedTreeNodes = null;
        myRoot = null;
    }

    public static class SVNRangeListNode {
        
        public static int FROM_SOURCE = 0;
        public static int FROM_TARGET = 1;
        
        public SVNRangeListNode(int kind, int offset, int limit, int target) {
            this.kind = kind;
            this.offset = offset;
            this.limit = limit;
            this.targetOffset = target;
            this.head = this;
        }
        
        public SVNRangeListNode append(SVNRangeListNode node) {
            this.next = node;
            node.prev = this;
            node.head = this.head;
            return node;
        }

        public int kind;
        public int offset;
        public int limit;
        public int targetOffset;
        
        public SVNRangeListNode prev;
        public SVNRangeListNode next;
        public SVNRangeListNode head;
    }
    
    public SVNRangeListNode buildRangeList(int offset, int limit) {
        SVNRangeListNode tail = null;
        SVNRangeTreeNode node = myRoot;
        
        while(offset < limit) {
            if (node == null) {
                return appendToRangeList(SVNRangeListNode.FROM_SOURCE, offset, limit, 0, tail);
            }

            if (offset < node.offset) {
                if (limit <= node.offset) {
                    return appendToRangeList(SVNRangeListNode.FROM_SOURCE, offset, limit, 0, tail);
                }
                tail = appendToRangeList(SVNRangeListNode.FROM_SOURCE, offset, node.offset, 0, tail);
                offset = node.offset;
            } else {
                if (offset >= node.limit) {
                    node = node.next;
                } else {
                    int targetOffset = offset - node.offset + node.targetOffset;
                    if (limit <= node.limit) {
                        return appendToRangeList(SVNRangeListNode.FROM_TARGET, offset, limit, targetOffset, tail);
                    }
                    tail = appendToRangeList(SVNRangeListNode.FROM_TARGET, offset, node.limit, targetOffset, tail);
                    offset = node.limit;
                    node = node.next;
                }
            }
        }
        SVNDeltaCombiner.assertCondition(false, "assert #6");
        return tail;
    }

    private SVNRangeListNode appendToRangeList(int kind, int offset, int limit, int tOffset, SVNRangeListNode tail) {
        if (tail == null) {
            return allocateListNode(kind, offset, limit, tOffset);
        }
        return tail.append(allocateListNode(kind, offset, limit, tOffset));
    }
    
    private SVNRangeTreeNode myScratchNode = new SVNRangeTreeNode(0,0,0); 
    
    public void splay(int offset) {
        if (myRoot == null) {
            return;
        }
        SVNRangeTreeNode root = myRoot;
        SVNRangeTreeNode scratch = myScratchNode;
        scratch.left = scratch.right = null;
        SVNRangeTreeNode left = scratch;
        SVNRangeTreeNode right = scratch;

        while(true) {
            if (offset < root.offset) {
                if (root.left == null) {
                    break;
                }
                if (offset < root.left.offset) {
                    SVNRangeTreeNode node = root.left;
                    root.left = node.right;
                    node.right = root;
                    root = node;
                    if (root.left == null) {
                        break;
                    }
                }
                right.left = root;
                right = root;
                root = root.left;
            } else if (offset > root.offset) {
                if (root.right == null) {
                    break;
                }
                if (offset > root.right.offset) {
                    SVNRangeTreeNode node = root.right;
                    root.right = node.left;
                    node.left = root;
                    root = node;
                    if (root.right == null) {
                        break;
                    }
                }
                left.right = root;
                left = root;
                root = root.right;
            } else {
                break;
            }
        }
        left.right = root.left;
        right.left = root.right;
        root.left = scratch.right;
        root.right = scratch.left;
        
        if (offset < root.offset && root.left != null) {
            if (root.left.right == null) {
                SVNRangeTreeNode node = root.left;
                root.left = node.right;
                SVNDeltaCombiner.assertCondition(root.left == null, "not null I");
                node.right = root;
                root = node;
            } else {
                SVNRangeTreeNode node = root.left;
                SVNRangeTreeNode prevNode = node;
                while(node.right != null) {
                    prevNode = node;
                    node = node.right;
                }
                // node should now become root.
                right = root;
                left = root.left;
                root = node;
                //node = root.left;
                prevNode.right = null;
                right.left = null;
                root.left = left;
                root.right = right;
            }
        }
        myRoot = root;
        SVNDeltaCombiner.assertCondition((offset >= root.offset) || (root.left == null && root.prev == null), "assert #4");
    }
    
    public void insert(int offset, int limit, int targetOffset) {
        if (myRoot == null) {
            myRoot = allocateTreeNode(offset, limit, targetOffset);
            return;
        }
        if (offset == myRoot.offset && limit > myRoot.limit) {
            myRoot.limit = limit;
            myRoot.targetOffset = targetOffset;
            cleanTree(limit);
        } else if (offset > myRoot.offset && limit > myRoot.limit) {
            boolean haveToInsertRange = myRoot.next == null ||
                    myRoot.limit < myRoot.next.offset ||
                    limit > myRoot.next.limit;
            if (haveToInsertRange) {
                if (myRoot.prev != null && myRoot.prev.limit > offset) {
                    myRoot.offset = offset;
                    myRoot.limit = limit;
                    myRoot.targetOffset = targetOffset;
                } else {
                    SVNRangeTreeNode node = allocateTreeNode(offset, limit, targetOffset);
                    node.next = myRoot.next;
                    if (node.next != null) {
                        node.next.prev = node;
                    }
                    myRoot.next = node;
                    node.prev = myRoot;

                    node.right = myRoot.right;
                    myRoot.right = null;
                    node.left = myRoot;
                    myRoot = node;
                }
                cleanTree(limit);
            }   
        } else if (offset < myRoot.offset) {
            SVNDeltaCombiner.assertCondition(myRoot.left == null, "assert #5");
            SVNRangeTreeNode node = allocateTreeNode(offset, limit, targetOffset);
            
            node.left = node.prev = null;
            node.right = node.next = myRoot;
            myRoot = node.next.prev = node;
            cleanTree(limit);
        }
    }
    
    private void cleanTree(int limit) {
        int topOffset = limit + 1;
        SVNRangeTreeNode parent = myRoot;
        SVNRangeTreeNode node = myRoot.right;
        while(node != null) {
            int offset = node.right != null && node.right.offset < topOffset ? node.right.offset : topOffset;
            if (node.limit <= limit || (node.offset < limit && offset < limit)) {
                parent.right = null;
                parent = node;
                deleteSubtree(node);
                node = null;
            } else {
                topOffset = node.offset;
                node = node.left;
            }
        }
    }
    
    private void deleteSubtree(SVNRangeTreeNode node) {
        if (node != null) {
            deleteSubtree(node.left);
            deleteSubtree(node.right);
            freeTreeNode(node);
        }
    }
}
