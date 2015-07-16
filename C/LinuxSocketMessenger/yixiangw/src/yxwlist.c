/*
 *@ author : yixiang wu
 *@ socklist.cpp
 *@ based on the reference below
 *@ reference: http://zh.wikipedia.org/wiki/链表
 */

//#include "yxwlist.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct node_sc{  
    void *datap;                
    struct node_sc *next, *prev;
};

struct yxwlist_sc{      
    struct node_sc head;
    int elmsize;
    int elmnr;
};

struct yxw{
    int name;
    int  c;
};

typedef void YXWLIST_T;


YXWLIST_T* yxwlist_new(int elmsize){
    struct yxwlist_sc *newlist;
    newlist = malloc(sizeof (struct yxwlist_sc));
    
    if (newlist == NULL){
        return NULL;
    }

    newlist->head.datap = NULL;
    newlist->head.next  = &newlist->head;
    newlist->head.prev  = &newlist->head; 
    newlist->elmsize = elmsize;

    return (void*)newlist;
}

int yxwlist_delete(YXWLIST_T *ptr){
    struct yxwlist_sc *me =ptr;
    struct node_sc *curr, *save;

    for(curr=me->head.next;curr!=&me->head;curr=save){
        save = curr ->next;
        free(curr->datap);
        free(curr);
    }
    free(me);
    return 0;   
}


int yxwlist_add(YXWLIST_T *ptr, const void *datap){
    struct yxwlist_sc *me =ptr;
    struct node_sc *newnodep;

    newnodep = malloc(sizeof (struct node_sc));
    if(newnodep == NULL){
        return -1;
    }

    newnodep->datap = malloc(me->elmsize);
    if(newnodep->datap == NULL){
        free(newnodep);
        return -1;
    }
    
    memcpy(newnodep->datap,datap,me->elmsize);
   
    me->head.prev->next =newnodep;
    newnodep->prev = me ->head.prev;
    me->head.prev = newnodep;
    newnodep->next = &me->head;
    return 0;
}

YXWLIST_T *a;
/*
int main(){
    char ni[] = "nimabi";
    //YXWLIST_T *a;
    a = yxwlist_new(sizeof(struct yxw));
    struct yxw *b;
    b = (struct yxw*)malloc(sizeof(struct yxw));
    struct yxw *d;
    d = (struct yxw*)malloc(sizeof(struct yxw));
    b->name = 100;
    b->c = 101;
    d->name =99;
    d->c = 98;
    yxwlist_add(a,b);
    yxwlist_add(a,d);
    struct yxwlist_sc *me =a;
    struct node_sc *curr;
    for(curr=me->head.next;curr!=&me->head;curr=curr->next){ 
        struct yxw *e;
        e = curr->datap;
        printf("%d\n",e->name);
        printf("%d\n",e->c);
    } 
}
*/

