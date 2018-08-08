"use strict";

(function () {
    
   function addclicker(id,proc) {
    var obj = document.getElementById(id);
        obj.addEventListener("click", proc);
    }; 
    
    addclicker("test1_sync",callproc);
    
    addclicker("headers_sync", callHeaders);

   var scaller = orajsoncallersync("/orajson");
   var acaller = orajsoncallerasync("/orajson");
   var acaller2 = orajsoncallerasync2("/orajson");
    
    // p1.procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date) 
    function callproc() {
        var args = {xi: 23, yi: "asas", zi: "2015-12-04"};
        var res = scaller("p1_p",args);
        console.log(res);
    }
    
    function callHeaders() {
        var args = {};
        var res = scaller("headers",args);
        console.log(res);
    }
    
    addclicker("test1_async", test1_async);
    
    function test1_async() {
        function success(x) {
            console.log("success", x);
        }
        acaller("p1_p",  {xi: 23, yi: "asas", zi: "2015-12-04"} ,success);
    }
    
    addclicker("headers_async", headers_async);
    
    function headers_async() {
        function success(x) {
            console.log("success", x);
        }
        acaller("headers",  {} ,success);
    }
    
    
    function some_error_async() {
        acaller("some_error",{msg:"bla"},function(x) {alert("this should not happen");},
                function(e) {alert("expected error:" + e);});
    }
    addclicker("some_error_async", some_error_async);
    
    
    
    function user_objects_async() {
        acaller("user_objects",{}, 
            function(x) {
                for (var i=0;i<x.c.length;i++) {
                    console.log(x.c[i]);
                }
            }); 
    }
    
    addclicker("user_objects",user_objects_async);
    
    addclicker("user_objects2", function() {
        acaller2("user_objects",{}, 
            function(x) {
                for (var i=0;i<x.c.length;i++) {
                    console.log(x.c[i]);
                }
            });
    });

})();