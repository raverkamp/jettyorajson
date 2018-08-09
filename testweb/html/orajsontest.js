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
   var scaller2 = orajsoncallersync2("/orajson");
    
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
    
    
    
    function p1_p(scaller) {
        var a = scaller("p1.p", {xi:1, yi:"bla", zi: "2018-05-06"});
        console.assert(a.result.xo === 2);
        console.assert(a.result.yo === "blabla");
        console.assert(a.result.zo === "2018-05-07 00:00:00");
    }
    
    function p1_p2(scaller) {
        var r1 = {x:12, y:"trump", z: "2018-5-3"};
        var r2 = scaller("p1.p2", {a: r1}).result.b;
        console.assert(r2.x === r1.x+1,"x");
        console.assert(r2.y === r1.y+r1.y,"y");
        console.assert(r2.z === "2018-05-04 00:00:00","z");
    }
    
    function p1_p3(scaller) {
        var t1 = [{x:12, y:"trump", z: "2018-5-3"}, {x:11000, y:"donald", z: "2012-1-3"}];
        var t2 = scaller("p1.p3", {a: t1}).result.b;
        console.assert(t2[0].x === t1[0].x+1, "x");
        console.assert(t2[0].y === t1[0].y + t1[0].y,"y");
        console.assert(t2[0].z === "2018-05-04 00:00:00","z");
        
        console.assert(t2[1].x === t1[1].x+1, "x");
        console.assert(t2[1].y === t1[1].y + t1[1].y,"y");
        console.assert(t2[1].z === "2012-01-04 00:00:00","z");
    }
    
    function p1_tests() {
        p1_p(scaller2);
        p1_p2(scaller2);
        p1_p3(scaller2);
    }
    
    addclicker("p1_tests", p1_tests);
    
    

})();