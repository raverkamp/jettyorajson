create or replace package body orajsontest as

 procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date) is
begin
  xo := xi+1;
  yo := yi||yi;
  zo := zi;
end;

 procedure get_headers(headers out header_array) is
  res header_array;
  r header_rec;
begin
 res := new header_array();
 for i in 1..owa.num_cgi_vars loop
    res.extend();
    r.name := owa.cgi_var_name(i);
    r.value := owa.cgi_var_val(i);
    res(res.last) := r;
  end loop;
  headers := res;
end;

end orajsontest;