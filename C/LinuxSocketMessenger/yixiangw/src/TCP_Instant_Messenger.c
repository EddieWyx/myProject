

/*
 * 
 * @author  yixiang wu <yixiangw@buffalo.edu>
 * @version 1.0
 * 
 *
 * @section LICENSE
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details at
 * http://www.gnu.org/copyleft/gpl.html
 *
 * @section DESCRIPTION
 * This is a Simple messenger which uses C soket and is able to run in
 * Linux/ Mac OS base environment
 * 
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/wait.h>
#include <signal.h>
#include <sys/select.h>

#include "../include/global.h"
#include "../src/yxwlist.h"

#define STDIN 0 

/**
 * main function
 *
 * @param  argc Number of arguments
 * @param  argv The argument list
 * @return 0 EXIT_SUCCESS
 */


// struct of the wrapper 
// contains sock, name, ip and port number
struct yxwsock{
    int sock;
    char *name;
     char *ip;
    char *port;
};

// for create new connection
int tcp_client(char *port);

// for show my local ip
int create_udpsocket();

// show the author's info
void creator();

// help infomation 
void help(char* check);

//addition yxwlist function 
char* findhname(YXWLIST_T *a,int des);
int duplicateip(YXWLIST_T *a, char *e);
int findsockbyid(YXWLIST_T *a, int i);
void LIST(YXWLIST_T *a);
void recevbysock(YXWLIST_T *a, int des ,char* c);
void deletbysock(YXWLIST_T *a, int des);
int PORTNULL(YXWLIST_T *a,int des,char *p); 
void servupdlist(YXWLIST_T *a);
int findid(YXWLIST_T *a,int des);
//free the mem
void free_mem(char** lis, size_t s){
    size_t i;
    for (i = 0; i<s; i++){free(lis[i]);}
    free(lis);
};

// get sockaddr, IPv4 or IPv6:
// reference from Beej's Guide to Network Programming
void *get_in_addr(struct sockaddr *sa){
    if (sa->sa_family == AF_INET) {
        return &(((struct sockaddr_in*)sa)->sin_addr);
    }

    return &(((struct sockaddr_in6*)sa)->sin6_addr);
}


/**
 * * main function
 *
 * @param  argc Number of arguments
 * @param  argv The argument list
 * @return 0 EXIT_SUCCESS
 **/

char* buf;
char ipstr[INET6_ADDRSTRLEN];

int main(int argc, char **argv)
{
    int max;
    max =4; // check the maximum connection.
    int backlog;//default size
    backlog =4;
    char * c_s; // indicate wether it is server or client
    char * myPort; // save my owen port number
    YXWLIST_T *socklist; // creat a global list to add the connections which the
                         // process part of
    socklist = yxwlist_new(sizeof(struct yxwsock));
    YXWLIST_T *sersocklist; // server list for update

    /*
     *TODO: determine the command
     */

    if(argc < 3){
    	printf("too few arguments\n");
    }else{
        c_s = argv[1];// determine if this is server or client
        myPort = argv[2];// my listening port number
    }

    /*
    * TCP_Server:
    * Reference: Adjusted the code from Beej's Guide to Network Programing
    * (6.1: simple stream server)
    */
    int tcp_server;
    struct addrinfo hints, *servinfo, *p;
    int check_getaddrinfo;
    size_t combytes =100;
    int yes=1;
    buf = (char *)malloc(1024*(sizeof(char)));

    memset(&hints,0,sizeof hints); // free the the necessary info
    hints.ai_family = AF_INET; // IPv4
    hints.ai_socktype = SOCK_STREAM; // TCP
    hints.ai_flags = AI_PASSIVE; // fill in my own IP for me

    if((check_getaddrinfo =
                getaddrinfo(NULL,myPort,&hints,&servinfo)) !=0){
            fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(check_getaddrinfo));
            return 1;
    }
    
    // create the socket
    for(p=servinfo; p!=NULL; p=p->ai_next){
     // create the server socket
     if((tcp_server = socket(p->ai_family,
         p->ai_socktype,p->ai_protocol)) == -1){
      perror("server: socket");
      continue;
     }

     // reuse the portnumber
     if(setsockopt(tcp_server,
       SOL_SOCKET,SO_REUSEADDR, &yes, sizeof(int))== -1){
      perror("setsockopt");
      exit(1);
     }

    // bind the socket
    if(bind(tcp_server,p->ai_addr,p->ai_addrlen) == -1){
        close(tcp_server);
        perror("server: bind");
        continue;
    }
     break;
   }
   if (p == NULL){
      fprintf(stderr, "server: failed to bind\n");
      return 2;
   }else{
      printf("stared your program.\n");
   }

   // lastly free the addrinfo
   freeaddrinfo(servinfo);
   
   //listening
   //if its client then if can only listen 3 member
   if(!(strcmp(c_s,"c"))){
     backlog=backlog-1;
     max = max-1;
   }
    
   if(listen(tcp_server,backlog)){
        perror("listen");
        exit(1);
   }

   fd_set master_list; // master file descriptor list
   fd_set watch_list;  // temp file descriptor list for select ()
   int fdmax;          // maximum file descriptor number;
   int selc,index;
   FD_ZERO(&master_list); //clear the master and temp sets
   FD_ZERO(&watch_list);

   // add the tcp_server to the master list
   FD_SET(tcp_server,&master_list);
   FD_SET(STDIN,&master_list);
   
   //keep check the biggest file descritor
   fdmax =tcp_server;
   
   //multiple command check set up
   char *cmd;
   cmd = (char *)malloc(100*(sizeof(char)));
   char *arg;

   // main loop
   while(1){
    watch_list = master_list;
    selc = select(fdmax + 1, &watch_list, NULL, NULL,NULL);
    if( selc < 0 ){
     perror("select");
     exit(4);
    }
    else{
     /*Loop through to check keyboard, */
     for(index = 0; index <=fdmax; index+=1){
      if(FD_ISSET(index, &watch_list)){
      /* if from keybord*/
      if(index == STDIN){
       int check = getline(&cmd,&combytes,stdin);
       if(check==-1){
         puts ("ERROR!");
       }
       else{
         int j,N;
         j = 0;
         N = 0;
         // determine how many space mean we put
         // how many arguments
         while(j<strlen(cmd)){
          if(cmd[j] == 32){
           N++;
          }
          j++;
         }
         char **k; // 2D char to save the arguments
         k = (char**)malloc((N+1)*sizeof(char*));
         
         //assign memory for that 2D char
         for(j=0;j<(N+1);j++){
          k[j]=(char*)malloc(100*(sizeof(char)));
         }
         arg = strtok(cmd," ");
         j=0;
         // save all the tokenize user inputs
         while(arg){
          strcpy(k[j],arg);
          j += 1;
          arg = strtok(NULL," ");
         }
         k[N][strlen(k[N])-1] = 0;
         // check those commands are only avaiable for
         // client
         if(!(strcasecmp(c_s,"c"))){
          /* compare command */
          if(!(strcasecmp(k[0],"CREATOR"))){
            creator();
          }
          else if(!(strcasecmp(k[0],"CONNECT"))){
            // if the space is less than 2
            if(N!=2){
             printf("Too few arguments\n");
            }else{
             // check if self connect    
             create_udpsocket();
             char hname[1024];
             gethostname(hname,1024);
             // check if attemp to connect self
             if((!(strcmp(k[1],ipstr)))||(!(strcmp(k[1],hname)))){
              printf("Error came from below.\n");
              printf("----------------------\n");
              printf("you connect yourself!\n");
             }else{
              // check if you registered
              if(sersocklist != NULL){
               // check if you try to connect
               // someone not in the
               // serverlist
               if(duplicateip(sersocklist,k[1])){
                // check if you reach the
                // maximum connection
                // ability
                if(max!=0){
                 //check if you try to
                 //do duplicate
                 //connection
                 if((!(duplicateip(socklist,k[1])))){
                  int outgo_client;
                  //new connection
                  outgo_client =create_tcpclient(k[1],k[2]);
                  if(outgo_client != -1){
                    printf("successfully connected to %s\n", k[1]);
                    FD_SET(outgo_client,&master_list);
                    if(outgo_client>fdmax){
                        fdmax = outgo_client;
                    }
                    // save sock,host name, ip, and port to 
                    // a linked list
                    struct yxwsock *yxw;
                    yxw = (struct yxwsock*)malloc(sizeof(struct yxwsock));
                    yxw->sock = outgo_client;
                    socklen_t len;
                    
                    struct sockaddr_storage addr;
                    len = sizeof addr;
                    int port;
                    if(getpeername(outgo_client,
                        (struct sockaddr*)&addr,
                        &len) == -1){
                        perror("getpeername: ");
                    }
                    bzero(ipstr,sizeof(ipstr));
                    
                    // get the connections ip
                    inet_ntop(addr.ss_family,
                                get_in_addr((struct sockaddr*)&addr),
                                ipstr, sizeof (ipstr));
                    yxw->ip = (char *)malloc(101*(sizeof(char)));
                    strcpy((yxw->ip),ipstr);
                    
                    // get the connection hostname
                    char host[1024];
                    char service[256];
                    getnameinfo((struct sockaddr *)&addr,len,host,
                        sizeof host, service,
                        sizeof service, 0 );
                    yxw->name = (char *)malloc(101*(sizeof(char)));
                    strcpy((yxw->name),host);
                    
                    // add the port number
                    yxw->port = (char *)malloc(101*(sizeof(char)));
                    strcpy((yxw->port),k[2]);
                    
                    // add to my list
                    yxwlist_add(socklist,yxw);
                    send(outgo_client,myPort,strlen(myPort),0);
                    free(yxw);
                    // decraese the
                    // connection
                    // cabaility
                    max--;
                 }else{
                                                                
                  }
                }else{
                    printf("Error came from one of below\n");
                    printf("--------------------------------------\n");
                    printf("you try to connect existing connection.\n");
                    printf("Or you try to connect the server.\n");
                 }
               }else{
                 printf("Error came from one of below\n");
                 printf("Or you reach the maximux connection cabability.\n");
                }
            }else{
              printf("Error came from one of below\n");
              printf("The Ip/hostname is not in server_update_list\n");
            }
           }else{
             printf("Error came from one of below\n");
             printf("You haven't registerd to the server yet\n");
           }
          }
         }
        }
        else if(!(strcasecmp(k[0],"REGISTER"))){
            if(N!=2){
                printf("Too few arguments. CHECK MUNE\n");
            }else{
            // check if register itself or
            // deplicate register
            if((!(duplicateip(socklist,k[1])))&& (max!=0)){
                int outgo_client;
                outgo_client = create_tcpclient(k[1],k[2]);
                FD_SET(outgo_client,&master_list);
                if(outgo_client>fdmax){
                    fdmax= outgo_client;
                }
                // save ip, hostname port and file
                // descriptor to my sock 
                struct yxwsock *yxw;
                yxw = (struct yxwsock*)malloc(sizeof(struct yxwsock));
                yxw->sock = outgo_client;
                socklen_t len;
                struct sockaddr_storage addr;
                len = sizeof addr;
                if(getpeername(outgo_client,
                        (struct sockaddr*)&addr,
                            &len) == -1){
                    perror("getpeername: ");
                }
                bzero(ipstr,sizeof(ipstr));
                inet_ntop(addr.ss_family,
                        get_in_addr((struct sockaddr*)&addr),
                        ipstr, sizeof (ipstr));
                yxw->ip = (char *)malloc(101*(sizeof(char)));
                strcpy((yxw->ip),ipstr);
                char host[1024];
                char service[256];
                getnameinfo((struct sockaddr *)&addr,len,host,
                        sizeof host, service,
                        sizeof service, 0 );
                yxw->name = (char *)malloc(101*(sizeof(char)));
                strcpy((yxw->name),host);
                yxw->port = (char *)malloc(101*(sizeof(char)));
                strcpy((yxw->port),k[2]);
                // save to my list
                yxwlist_add(socklist,yxw);
                send(outgo_client,myPort,strlen(myPort),0);
                free(yxw);
                max--;
                }else{
                    printf("Error came from one of the below\n");
                    printf("--------------------------------\n");
                    printf("You are already registered\n");
                    printf("You attemp to register a client\n");
                }
                }
            }
            else if(!(strcasecmp(k[0],"SEND"))){
                if(N<2){
                    printf("Too few arguments, check menu.\n");
                }else{
                    // commbine all the message to one
                    
                    char *tempbuf;
                    tempbuf =(char *)malloc(1000*sizeof(char));
                    char space[] = " ";
                    int ck;
                    ck=2;
                    if(N>2){
                        for(ck;ck<=N;ck++){
                            strcat(tempbuf,k[ck]);
                            strcat(tempbuf,space);
                        }
                    }else{
                    strcat(tempbuf,k[2]);
                    }
                    int msgSock;
                    msgSock =0;
                    int id;
                    id = 0;
                    id = atoi(k[1]);
                    // send the msg by the id
                    if(id!=1){
                    if((msgSock = findsockbyid(socklist,id))!=0){
                        if(send(msgSock,tempbuf,strlen(tempbuf),0) == -1){
                        printf("Error: failed to send\n");
                        }else{
                        printf("Message sent to ID:  %d\n",id);
                        }
                    }else{
                    printf("Check list to see if you guys connected\n");
                    }
                    free(tempbuf);
                    }else{
                    printf("you are not supposed to send message to server.\n");
                    
                    }
                }
                }
                else if(!(strcasecmp(k[0],"TERMINATE"))){
                        if(N!=1){
                        printf("Too few arguments, check menu.\n");
                        }else{
                        // terminate the desire id
                        int id;
                        id = atoi(k[1]);
                        // you can not terminate the server
                        if(id != 1){
                        int closeSock;
                        closeSock = findsockbyid(socklist,id);
                        if(closeSock!=0){
                            deletbysock(socklist, closeSock);
                            if(close(closeSock)!= -1){
                                FD_CLR(closeSock,&master_list);
                                printf("Sucessfully Terminate %d\n",id);
                                max++;
                            }else{
                                printf("Error: failed to terminate\n");
                            }
                        }else{
                            printf("Check list to see if you guys connected\n");
                        }
                        }else{
                            printf("You can not terminate server.\n");     
                        }
                        }
                    }
                    else if(!(strcasecmp(k[0],"LIST"))){
                        printf("\nclient connection list\n");
                        LIST(socklist);
                    }
                    else if(!(strcasecmp(k[0],"MYIP"))){
                        create_udpsocket();
                        printf("\nMY IP is: %s\n",ipstr);
                    }
                    else if(!(strcasecmp(k[0],"MYPORT"))){
                        printf("\nMY PORT NUMBER is %s\n",myPort);
                    }
                    else if(!(strcasecmp(k[0],"HELP"))){
                        help(c_s);
                    }
                    else if(!(strcasecmp(k[0],"EXIT"))){
                        // free all the memory
                        
                        free_mem(k,N+1);
                        free(cmd);
                        free(buf);
                        yxwlist_delete(socklist);
                        close(tcp_server);
                        FD_CLR(tcp_server,&master_list);
                        exit(1);
                    }else{
                                    printf("TYPE HELP FOR USER MENU.\n");
                                }
                            }else{
                                
                                if(!(strcasecmp(k[0],"EXIT"))){
                                    // free memory
                                    free_mem(k,N+1);
                                    free(cmd);
                                    free(buf);
                                    yxwlist_delete(socklist);
                                    close(tcp_server);
                                    FD_CLR(tcp_server,&master_list);
                                    exit(1);
                                }
                                else if(!(strcasecmp(k[0],"LIST"))){
                                    printf("\nconnection list\n");  
                                    LIST(socklist); 
                                }
                                else if(!(strcasecmp(k[0],"MYIP"))){
                                    create_udpsocket();
                                    printf("\nMY IP is: %s\n",ipstr);       
                                }
                                else if(!(strcasecmp(k[0],"MYPORT"))){
                                    printf("\nMY PORT NUMBER is %s\n",myPort);
                                }
                                else if(!(strcasecmp(k[0],"CREATOR"))){
                                    creator();
                                }
                                else if(!(strcasecmp(k[0],"HELP"))){
                                    help(c_s);
                                }else{
                                    printf("TYPE HELP FOR USER MENU.\n"); 
                                }

                                

                            }
                            /*
                                * free memory
                            */
                            free_mem(k,N+1);
                            }
                    }

                    /* if there is incomming connection*/
                    else if(index == tcp_server){
                        // create new socket for incoming connection
                        int incom_connect;
                        socklen_t addrlen_size;
                        struct sockaddr_storage their_addr;
                        addrlen_size = sizeof their_addr;
                        incom_connect = accept(tcp_server,
                                (struct sockaddr *)&their_addr,
                                &addrlen_size);
                        if(incom_connect == -1){
                            perror("accept");
                        }else{
                            FD_SET(incom_connect, &master_list);
                            if(incom_connect> fdmax){
                                fdmax = incom_connect;
                            }
                            //save the hostname sock, ip and port
                            //into the linked list
                            struct yxwsock *yxw;
                            yxw = (struct yxwsock*)malloc(
                                    sizeof(struct yxwsock));
                            yxw->sock =incom_connect;
                            socklen_t len;
                            struct sockaddr_storage addr;
                            len = sizeof addr;
                            if(getpeername(incom_connect,
                                        (struct sockaddr*)&addr,&len) == -1){
                                perror("getpeername: ");
                            }
                            bzero(ipstr,sizeof(ipstr));
                            inet_ntop(addr.ss_family,
                                    get_in_addr((struct sockaddr*)&addr),
                                    ipstr, sizeof (ipstr));
                            // get the ip and  save the ip
                            yxw->ip = (char *)malloc(101*(sizeof(char)));
                            strcpy((yxw->ip),ipstr);
                            char host[1024];
                            char service[256];
                            getnameinfo((struct sockaddr *)&addr,len,host,
                                        sizeof host, service,
                                        sizeof service, 0 );
                            // get the hostname
                            yxw->name = (char *)malloc(101*(sizeof(char)));
                            strcpy((yxw->name),host);
                            yxw->port =  NULL;
                            yxwlist_add(socklist,yxw);
                            free(yxw);
                        }
                    }
                        /* if data from a client*/
                    else{
                        // handle data from a client
                        bzero(buf,1024);
                        int n = recv(index,buf,1023,0);
                        if(n <= 0){
                         if(n == 0){
                            // connection close
                            if(!(strcasecmp(c_s,"c"))){
                            int iidd;
                            iidd =findid(socklist,index);
                            char *hhname;
                            hhname =findhname(socklist,index); 
                            printf("ID: %d hostname: %s  close the connection with you.\n",iidd,hhname);
                            max++;
                            }
                         }else{
                            perror("recv");
                         }
                         deletbysock(socklist,index);
                         close(index);  
                         FD_CLR(index,&master_list);  
                         if(!(strcasecmp(c_s,"s"))){
                            servupdlist(socklist);
                         }
                        }else{
                         //first hand shake which mean the client need to
                         //send the port number to server
                         //means port is null which return 0;
                         if(!PORTNULL(socklist,index,buf)){
                            // if thats server then update the server
                            // update list
                            if(!(strcasecmp(c_s,"s"))){
                                servupdlist(socklist);
                            }else{
                                // else show twho connect you
                                char * hhname;
                                hhname = findhname(socklist,index);
                                printf("%s connected you, check the list.\n",hhname); 
                            }
                         }else{
                            // server  update its list
                            if(index == (findsockbyid(socklist,1))){
                                // copy the server update list to the
                                // clients server update list
                                printf("\nserver_update_list\n");
                                YXWLIST_T *templist;
                                templist = yxwlist_new(sizeof(struct yxwsock));
                                char *argg;
                                argg = strtok(buf," ");
                                int iii;
                                iii = 0; 
                                struct yxwsock *tempsock;
                                tempsock = 
                                    (struct yxwsock*)malloc(sizeof(struct yxwsock));
                                while(argg){
                                    if(iii == 0){
                                     tempsock->name = 
                                         (char *)malloc(101*(sizeof(char)));
                                     strcpy((tempsock->name),argg);
                                    }
                                    else if(iii == 1){
                                     tempsock->ip =
                                         (char *)malloc(101*(sizeof(char)));
                                     strcpy((tempsock->ip),argg);
                                    }
                                    else if(iii = 2){
                                     tempsock->port = 
                                         (char *)malloc(101*(sizeof(char)));
                                     strcpy((tempsock->port),argg);
                                     yxwlist_add(templist,tempsock);
                                     iii =-1;  
                                    }
                                    argg = strtok(NULL," ");
                                    iii++;
                                }
                                sersocklist = templist;
                                //free(templist);
                                LIST(sersocklist);
                            // 
                            // if not then just print the message
                            }else{
                                if(!(strcasecmp(c_s,"c"))){
                                    recevbysock(socklist,index,buf);
                                }
                            }
                          }
                        }
                    }
                }
            }
        }
    }
    return 0;
}
// servupdlist: input is the socklist and 
// send the updlist message to every clients
void servupdlist(YXWLIST_T *a){
      struct yxwlist_sc *me = a;
      struct yxwlist_sc *inme =a;
      struct node_sc *curr;
      struct node_sc *incurr;  
      struct yxwsock *yxw;
      struct yxwsock *inyxw;
      int ss;
      char space[] = " ";
      char nl[] = "\n";
      char *finalmsg; 
      finalmsg =(char *)malloc(1000*(sizeof(char)));
      bzero(finalmsg,1000);
      for(incurr = inme->head.next; incurr!=&inme->head;incurr=incurr->next){
                inyxw = incurr->datap;
                strcat(finalmsg,(inyxw->name));
                strcat(finalmsg,space);
                strcat(finalmsg,(inyxw->ip)); 
                strcat(finalmsg,space); 
                strcat(finalmsg,(inyxw->port));
                strcat(finalmsg,space);
      }
      for(curr = me->head.next; curr!=&me->head;curr=curr->next){
                yxw = curr->datap;
                send(yxw->sock,finalmsg,strlen(finalmsg),0);
      }
      free(finalmsg);
}

//find which hostname of which connects you
char* findhname(YXWLIST_T *a,int des){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;
    struct yxwsock *yxw; 
    for(curr=me->head.next; curr!=&me->head;curr=curr->next){
        yxw = curr->datap;
        if((yxw->sock)==des){  
            return (yxw->name); 
        }
    }
    return NULL;
}


// findid:
// find out which id number by put in the socklist 
// the filedescriptor
int findid(YXWLIST_T *a,int des){
      struct yxwlist_sc *me = a;
      struct node_sc *curr;
      struct yxwsock *yxw;
      int i;
      i=1;
      for(curr=me->head.next; curr!=&me->head;curr=curr->next){
          yxw = curr->datap;
          if((yxw->sock)==des){
             return i;
          }
          i++;
        }
    return 0;
}
// deletbysock
// delete the specific node by the descriptor
void deletbysock(YXWLIST_T *a, int des){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;
    struct yxwsock *yxw;
    for(curr=me->head.next; curr!=&me->head;curr=curr->next){
        yxw = curr->datap;
        if((yxw->sock)==des){
            struct node_sc *_next,*_prev;
            _prev = curr->prev, _next = curr->next;
            _prev ->next = _next, _next->prev = _prev;
            free(curr->datap);
            free(curr);
            break;
        }
    }
}
//recebysock
//receive the message 
//display in a right way
void recevbysock(YXWLIST_T *a, int des ,char* c){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;
    struct yxwsock *yxw;
    int i;
    i=1;
    for(curr=me->head.next; curr!=&me->head;curr=curr->next){
        yxw = curr->datap;
        if((yxw->sock)==des){
                printf("Message received from: %s\n", yxw->name);
                printf("Sender's IP: %s\n", yxw->ip);
                printf("Sender's ID: %d\n", i);
                printf("Message: %s\n", c);
        }
        i++;
    }
}
// findsockbyid
// return the sepcific file descriptor
// by the id number
int findsockbyid(YXWLIST_T *a, int i){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;
    struct yxwsock *yxw;
    int j;
    j =i;
    for(curr=me->head.next;curr!=&me->head;curr=curr->next){
        yxw = curr->datap;
        if(j==1){
            return yxw->sock;
        }
        j--;
    }
    return 0;
}
//  duplicateip
//  check if the ip or hostanem you enter
//  already apear
int duplicateip(YXWLIST_T *a, char *e){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;  
    struct yxwsock *yxw;
    for(curr=me->head.next;curr!=&me->head;curr=curr->next){ 
        yxw = curr->datap;
        if((!(strcasecmp((yxw->ip),e)))||(!(strcasecmp((yxw->name),e)))){
            return 1;
        }
    }
    return 0;
}
// print out the list
// server update list
void LIST(YXWLIST_T *a){
    struct yxwlist_sc *me = a;
    struct node_sc *curr;
    struct yxwsock *yxw;
    int i;
    i = 0;
    int pp;

    printf("%-5s%-35s%-20s%-8s\n","ID","HOSTNAME","IP ADDRESS","PORT");
   // yxw = (struct yxwsock*)malloc(sizeof(struct yxwsock)); 
    for(curr=me->head.next; curr!=&me->head;curr=curr->next){
          yxw = curr->datap;
          i=i+1;
          if((yxw->port)!=NULL){
             pp =(int) strtol((yxw->port),NULL,10);
             printf("%-5d%-35s%-20s%-8d\n",i,yxw->name,yxw->ip,pp);
            }
    }

}
// PORTNULL
// check if the specific port associated with 
// file descriptor is null or not
int PORTNULL(YXWLIST_T *a, int des, char* p){
     struct yxwlist_sc *me = a;
     struct node_sc *curr;
     struct yxwsock *yxw;
     for(curr=me->head.next; curr!=&me->head;curr=curr->next){
         yxw = curr->datap;
         if((yxw->sock)==des){
             if((yxw->port) == NULL){
                 yxw->port = (char *)malloc(101*sizeof(char));
                 strcpy((yxw->port),p);
                 return 0;
             }else{
                return 1;
             }
         }
     }
     return 1;
}
// creator
// show who am I
void creator(){
    printf("NAME: YIXIANG WU\n");
    printf("UBIT NAME: yixianw\n");
    printf("UBIT EMAIL: yixiangw@buffalo.edu\n");
    printf("I have read and understood the course academic");
    printf("integrity policy located at");
    printf("http://www.cse.buffalo.edu/faculty/dimitrio/courses/");
    printf("cse4589_f14/index.html#integrity\n");
}
// help
// display the user menu
void help(char* check){

    printf("BELOW ARE THE COMMANDS YOU CAN USE\n");
    printf("HELP: user manu");
    printf("CREATOR show who created this program");
    printf("MYIP obtain my own ip address\n");
    printf("MYPORT obtain my own port\n");
    if(!(strcmp(check,"c"))){
        printf("REGISTER <SERVER_IP> <SERVER_PORT_NO> register to server\n");
        printf("CONNECT  <DESTINATION_IP> <DESTINATION_PORT_NO> connect to other. you can connect to 2 at the most\n");
        printf("SEND <CONNECTTION ID> <MESSAGE> send messege to other\n");
        printf("TERMINATE <CONNECTTION ID> disconnect from cretain connections\n");
    }
    printf("LIST show all the exiting connections you are part of\n");
    printf("EXIT close all the connection and exit the program\n");
}
/*
 *create the tcp_client
 *Reference: Beej's Guide to Network Program
 *@ simple stream client;
 */

// for new connection
// create the new soket and connect to the desitination
int create_tcpclient(char* ip,char *port){
    int tcp_client;
    struct addrinfo hints, *servinfo, *p;
    int check_addrinfo;

    memset(&hints,0,sizeof hints);
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;


    if((check_addrinfo = getaddrinfo(ip,port,&hints,&servinfo))!=0){
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(check_addrinfo));
        return 1;
    }

    // creat the sock
    for(p = servinfo; p != NULL; p->ai_next){
        if((tcp_client = socket(p->ai_family,
                                p->ai_socktype,
                                p->ai_protocol)) == -1){
            perror("client: socket");
            continue;
        }

        if(connect(tcp_client,p->ai_addr,p->ai_addrlen) == -1){
            close(tcp_client);
            perror("client: connect");
            break;
           // continue;
        }
        break;
    }
    if(p==NULL){
        fprintf(stderr, "failed to connect, check your ip or port\n");
        tcp_client = -1;
        return tcp_client;
    }
  //  printf("connected to the destination\n");
    freeaddrinfo(servinfo); // free the addrinfo;
    return tcp_client;
}

/*
 *create udp socket to get its own IP
 *Reference: Beej's Guide to Network Programming
 *@ DATAGRAM client
 */
int create_udpsocket(){
    int sockfd;
    struct addrinfo hints, *servinfo, *p;
    int rv;

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_DGRAM;

    if ((rv = getaddrinfo("8.8.8.8", NULL, &hints, &servinfo)) != 0) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rv));
        return 1;
    }

    for(p = servinfo; p != NULL; p = p->ai_next) {
        if ((sockfd = socket(p->ai_family,
                             p->ai_socktype,
                             p->ai_protocol)) == -1) {
              perror("UDP: socket");
              continue;
        }
        break;
    }
    if (p == NULL) {
        fprintf(stderr, "UDP: failed to create socket\n");
        return 2;
    }
    if(connect(sockfd,p->ai_addr,p->ai_addrlen) == -1) {
        perror("UDP: connect");
        close(sockfd);
    }

    struct sockaddr_in ss;
    unsigned int len;
    len = sizeof ss;
    getsockname(sockfd,(struct sockaddr *)&ss,&len);
    bzero(ipstr,sizeof(ipstr));
    inet_ntop(AF_INET,&ss.sin_addr,ipstr,sizeof ipstr);
    freeaddrinfo(servinfo);
    close(sockfd);
}



















