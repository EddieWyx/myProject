/*
*
*@Author : yixiang wu
*@header file for own created linked list
*@adjustment from below reference
*@Reference : http://zh.wikipedia.org/wiki/链表
*
* yxwlist.h
**/

#ifndef  YXWLIST_H_
#define YXWLIST_H_

struct node_sc{  
    void *datap;                
    struct node_sc *next, *prev;
     };

struct yxwlist_sc{      
    struct node_sc head;
    int elmsize;
    int elmnr;
     };

typedef void node_proc_fun_t(void*);
typedef int node_comp_fun_t(const void*, const void*);

typedef void YXWLIST_T;

YXWLIST_T *yxwlist_new(int elmsize);
int yxwlist_delete(YXWLIST_T *ptr);

int yxwlist_add(YXWLIST_T *ptr, const void *datap);

int yxwlist_travel(YXWLIST_T *ptr, node_proc_fun_t *proc);

void yxwlist_node_delete(YXWLIST_T *ptr, node_comp_fun_t *comp,const void *key);
void *yxwlist_node_find(YXWLIST_T *ptr, node_comp_fun_t *comp,const void *key);

#endif

