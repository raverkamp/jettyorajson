create or replace package p1 as
-- test package for javaplsql
procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date);

type r1 is record (x number, y varchar2(200), z date);


procedure p2(a r1, b out r1);

type t1 is table of r1;

procedure p3(a t1, b out t1);

type t2 is table of t1;

procedure p4(a in out t2);

type t3 is table of varchar2(32767);

procedure p5(a in t3,b out t3);

procedure p6;

function f7(a integer,b varchar2,c date) return r1;

procedure p7(x dual%rowtype);

procedure p8(x boolean,y out boolean);

procedure p9(x1 pls_integer,y1 out pls_integer,x2 natural,y2 out natural);

procedure raise_error(errnum integer,txt varchar2);

procedure no_args;

procedure pcursor1 (n number,v varchar2,d date,c out sys_refcursor);

type refcursor_rec is record (n number,v varchar2(200),d date);

TYPE return_cur IS REF CURSOR RETURN refcursor_rec;

procedure pcursor2(n number,v varchar2,d date,c out return_cur);

type return_cur2 is ref cursor return all_tables%rowtype;

procedure pcursor3(c out return_cur2);

type tabv is table of varchar2(200) index by varchar2(200);
type tabi is table of varchar2(200) index by binary_integer;

procedure pindex_tab(ai tabv,bi tabi,ao out tabv,bo out tabi);

type tabvv is table of tabv index by varchar2(200);

procedure pindex_tab2(x in out tabvv);

procedure praw(x in raw,y out raw);


end;
/