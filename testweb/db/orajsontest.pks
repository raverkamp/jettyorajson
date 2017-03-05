create or replace package orajsontest as 
procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date);

type header_rec is record  (name varchar2(32767 char),value varchar2(32767 char));
type header_array is table of header_rec;

procedure get_headers(headers out header_array);

procedure some_error(msg varchar2);

procedure user_objects(c out sys_refcursor);

end orajsontest;