JettyOraJson

This system is an interface to an Oracle database via Json and HTTP.
The servlet is exposed at some url. The servlet is called with a HTTP post method,
there is one parameter "data". The value for this parameter is a json encoded 
Javascript object. 
The deserialized Javascript object has the follwoing fields:
procedure
   this determines the procedure to call, there is a mapping form these names
   to the real name of the oracle procedures, i.e. each procedure to be called
   has to be explicitly published. It is not possible to call functions.
   Reasoning: functions are often made public for the only reason to be used 
   in SQL statements.

arguments
  the arguments an object keyword arguments

The return from the post method is a Json object with the fields:
result: if everything was succesfull the out paramters in an object
error: if there was an exception some error message

What happens on error? Do we want to expose the inner workings of the system?

Or define one specific error code (maybe -20017, whatever) that will be 
translated into an error in the result json object?
Everything else gives an error 500? It would be nice to get some information 
about the error, but we do not want to tell it everyone. 
A restriction based on the IP address of the client?

Dates !!!!!
javascript serializes dates to JSON as iso 8601.
I gues Strings are internally without a timezone.
GMT+0200 is added on output.

In the Chrome Browser conosle I got this:
> new Date("2015-06-09T23:45:13Z")
  ==> Wed Jun 10 2015 01:45:13 GMT+0200 (CEST)

What are the options? Go with your own Datatype for dates without a timezone?

Or just use strings? Formatted as "yyyy-mm-dd" or "yyyy-mm-ddThh:mi:ss"?
OK: decision dates are formatted as strings:
"2015-12-04 00:00:00" or "2015-12-04". Both are accepted as input and the first 
is returned to caller.




