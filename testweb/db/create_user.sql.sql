create user user_orajson identified by user_orajson;
grant resource to user_orajson;
grant connect to user_orajson;

--connect  user_orajson/user_orajson
create type number_array as table of number;
create type varchar2_array as table of varchar2(32767);
create type date_array as table of date;
create type raw_array as table of raw(32767);