create or replace package body p1 as

procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date) is
begin
  xo := xi+1;
  yo := yi||yi;
  zo := zi+1;
end;

procedure p2(a r1,b out r1) is
begin
b.x := a.x+1;
b.y := a.y||a.y;
b.z := a.z+1;
end;

procedure p3(a t1, b out t1) is
x r1;
res t1 := new t1();
begin
  for i in a.first .. a.last loop
    x := a(i);
    x.x := x.x+1;
    x.y := x.y||x.y;
    x.z := x.z + 1;
    res.extend();
    res(res.last) := x;
  end loop;
  b:= res;
end;

procedure p4(a in out t2) is
begin
  null;
end;

procedure p5(a in t3,b out t3) is
begin
  b:= a;
end;

procedure p6 is
begin
  null;
end;

function f7(a integer,b varchar2,c date) return r1 is
res r1;
begin
 res.x := a;
 res.y := b;
 res.z := c;
 return res;
end;

procedure p7(x dual%rowtype) is
begin
  null;
end;


procedure p8(x boolean,y out boolean) is
begin
  y:= not x;
end;

procedure p9(x1 pls_integer,y1 out pls_integer,x2 natural,y2 out natural) is
begin
  y1:= -x1;
  y2 := x2 + 10;
end;

procedure raise_error(errnum integer,txt varchar2) is
begin
  raise_application_error(errnum,txt);
end;

procedure no_args is
begin
 null;
end;

procedure pcursor1 (n number,v varchar2,d date,c out sys_refcursor) is
begin
  open c for select 'a' as a,1 as b, date '2001-1-5' as c from dual
                  union all select v,n,d from dual
                  order by 1;
end;

procedure pcursor2(n number,v varchar2,d date,c out return_cur) is

begin
  open c for select 1 as n,'a' as v, date '2001-1-5' as d from dual
                  union all select n,v,d from dual
                  order by 1;
end;

procedure pcursor3(c out return_cur2) is
begin
  open c for select * from all_tables where rownum <100;
end;


procedure pindex_tab(ai tabv,bi tabi,ao out tabv,bo out tabi) is
xa tabv;
xb tabi;
v varchar2(200);
i binary_integer;
begin
  v := ai.first;
  loop
    exit when v is null;
    xa('x'||v) := ai(v)||'y';
    v := ai.next(v);
  end loop;
  i := bi.first;
  loop
    exit when i is null;
    xb(i*2) := bi(i)||'y';
    i := bi.next(i);
  end loop;
  ao := xa;
  bo := xb;  
end;

procedure pindex_tab2(x in out tabvv) is
begin
  null;
end;

procedure praw(x in raw,y out raw) is
begin
  y:= x;
end;

end;
/