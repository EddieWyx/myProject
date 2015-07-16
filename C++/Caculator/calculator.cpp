#include<iostream>
#include<vector>
#include<string>
#include<stack>
#include<sstream>
#include<string>
#include<cstring>
using namespace std;
int evaluate(string expr);
string convertCTS(char c);
int convertSTI(string c);
void reverse(string& in);
string inFix_postFix(string in, bool insertspace);
int priority(char c);
bool isOperant(char c);

int main(){
    string a;
    cout<<"enter expression: "<<endl;
    cin >> a;
    string b = inFix_postFix(a,true);
    int res = evaluate(b);
    cout<<b<<endl;
    cout<<res<<endl;

}

int evaluate(string expr){
    stack<int> val;
    int res;
    for(int i =0; i<expr.size();i++){
        if(isdigit(expr[i])){
            string p="";
            while(isdigit(expr[i])){
                p+=expr[i++];
            }
            int ttt = convertSTI(p);
            val.push(ttt);
        }   
        if(isOperant(expr[i])){
            int a = val.top();
            val.pop();
            int b = val.top();
            val.pop();
            switch(expr[i]){
                case '+':
                    res = a+b;
                    break;
                case '-':
                    res = b-a;
                    break;
                case '*':
                    res = a*b;
                    break;
                case '/':
                    res = b/a;
                    break;
                case '%':
                    res = b%a;
                    break;
            }
            val.push(res);
        }
    }
    res = val.top();
    return res;
}

string convertCTS(char c){
  stringstream s;
  string res;
  s << c;
  s >> res;
  return res; 
}

void reverse(string& in){
    for(int i =0; i <(in.size()/2); i++){
        swap(in[i],in[in.size()-i-1]);
    }
}

int convertSTI(string c){
    int res;
    res = atoi(c.c_str());
    return res;
}

/*
 * check if this is operant
 * 
 */
bool isOperant(char c){
    if(c == '+' || c == '-'|| c == '*' || c == '/' || c=='%'){
        return true;
    }
    else{
        return false;
    }
}

/**
 *check the priority
 *
 **/

int priority(char c){
    int pri =0;
    if(c == '*' ||c == '/' || c== '%'){
        pri = 2;
    }
    else if(c == '+' || c=='-'){
        pri = 1;
    }
    return pri;
}

string inFix_postFix(string in, bool insertspace){
    int i =0;
    string p="";
    char t;
    stack<char> save;
    while(i < in.size()){
        // while(in[i] == ' ' || in[i] == '\t'){
        //    i++;
        // }
        // if we find the digit
        if(isdigit(in[i])){
            while(isdigit(in[i])){
                p += in[i];
                i++;
            }
            // if there is a space;
            if(insertspace){
                p+=' ';
            }
        }
        // if we find the operator
        save.
        if(isOperant(in[i])){
            if(save.empty()){
                save.push(in[i]);
                i++;
            }else{
                while(!save.empty () &&((priority(save.top()) >= priority(in[i])))){
                    p+=save.top();
                    if(insertspace){
                        p+=' ';
                    }
                    save.pop();
                }
                save.push(in[i]);
                i++;
            }
        }
    }
    // if the stack is not empty
    while(!save.empty()){
        t = save.top();
        save.pop();

        p += t;
        if(insertspace){
             p+=' ';
        }
    }
    return p;
}

