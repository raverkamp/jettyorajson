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
        console.assert(a.result.xo === 2,"p1_p xo");
        console.assert(a.result.yo === "blabla", "p1_p yo");
        console.assert(a.result.zo === "2018-05-07 00:00:00","p1_p zo",a.result.zo);
    }
    
    function p1_p2(scaller) {
        var r1 = {x:12, y:"trump", z: "2018-5-3"};
        var r2 = scaller("p1.p2", {a: r1}).result.b;
        console.log(r2);
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
    
    function p1_p4(scaller) {
        var t1 = [[{x:12, y:"trump", z: "2018-09-30 00:00:00"}, 
                   {x:11000, y:"donald", z: "2012-12-25 00:00:00"}],
                  [{x:12, y:"trump", z: "2018-05-08 00:00:00"}, 
                   {x:11000, y:"donald", z: "2012-01-03 00:00:00"}]];
        var t2 = scaller("p1.p4", {a: t1}).result.a;
        console.assert(deepCompare(t1,t2),"deep");
        
        {
            let t = [];
            var res = scaller("p1.p4", {a: t}).result.a;
            console.assert(deepCompare(t,res),"deep");
        }
        {
            let t = [null];
            var res = scaller("p1.p4", {a: t}).result.a;
            console.assert(deepCompare(t,res),"deep");
        }
        {
            let t = null;
            var res = scaller("p1.p4", {a: t}).result.a;
            console.assert(deepCompare(t,res),"deep");
        }
    }
    
    function p1_p5(scaller) {
        var t = ["a","b","w",null,"3"];
        var res = scaller("p1.p5", {a: t}).result.b;
        console.assert(deepCompare(t,res),"p1_p5");
    }
    
    function p1_p6(scaller) {
        var res = scaller("p1.p6", {}).result;
        console.assert(deepCompare({},res),"p1_p5");
    }
    
    function p1_f7(scaller) {
        var x = {x: 123, y: "roland", z: "2012-01-03 00:00:00"};
        var res = scaller("p1.f7",{a:123, b: "roland", c: "2012-01-03 00:00:00"}).result;
        console.assert(deepCompare({RETURN: x},res),"p1.f7");
    }
    
    // arg is anonymous record type
    function p1_p7(scaller) {
        var res = scaller("p1.p7",{x:"heinz"});
        console.assert(deepCompare(res.result),undefined);
        console.assert(res.error.search("anonymous record types")>=0);
    }
    
    // p8 bool in bool out
    function p1_p8(scaller) {
        var res = scaller("p1.p8", {x:true});
        console.assert(deepCompare(res.result, {y: false}));
    }
    
    //procedure p9(x1 pls_integer,y1 out pls_integer,x2 natural,y2 out natural)
    function p1_p9(scaller) {
        let a = {x1:123132, x2:6567};
        var res = scaller("p1.p9",a).result;
        console.assert(deepCompare(res,{y1: (-a.x1), y2: a.x2 +10}));
    }
    
    //procedure raise_error(errnum integer,txt varchar2) 
    function p1_raise_error(scaller) {
        var res = scaller("p1.raise_error", {errnum: -20123, txt: "quatsch"});
        console.assert(res.error.search("20123")>=0);
        console.assert(res.error.search("quatsch")>=0);
    }
    
    //procedure pcursor1 (n number,v varchar2,d date,c out sys_refcursor) is
    function p1_pcursor1(scaller) {
        var res = scaller("p1.pcursor1", {n:234.125, v:"popel",d:"2012-01-03 12:43:09"}).result;
        console.assert(deepCompare(res,{c:[{a:"a", b:1,c:"2001-01-05 00:00:00"},
            {a:"popel", b:234.125, c:"2012-01-03 12:43:09"}]}));   
    }
    
    // TYPE return_cur IS REF CURSOR RETURN refcursor_rec;
    // procedure pcursor2(n number,v varchar2,d date,c out return_cur);
    function p1_pcursor2(scaller) {
        var res = scaller("p1.pcursor2", {n:234.125, v:"popel",d:"2012-01-03 12:43:09"}).result;
        console.assert(deepCompare(res,{c:[{v:"a", n:1, d:"2001-01-05 00:00:00"},
            {v:"popel", n:234.125, d:"2012-01-03 12:43:09"}]}));   
    }
    
    //procedure pcursor3(c out return_cur2)
    function p1_pcursor3(scaller) {
        var res = scaller("p1.pcursor3",{});
        console.assert(deepCompare(res.result),undefined);
        console.assert(res.error.search("anonymous record types")>=0);
    }
    
    // type tabv is table of varchar2(200) index by varchar2(200);
    // type tabi is table of varchar2(200) index by binary_integer;
    // procedure pindex_tab(ai tabv,bi tabi,ao out tabv,bo out tabi) 
    function p1_pindex_tab(scaller) {
       const ai = {"a" :"A1", "x": "X1", "8": "81"};
       const bi = { 0: "X1", 13: "81"};
       bi[-20]= "A1";
       const res = scaller("p1.pindex_tab",{ai:ai, bi:bi}).result;
       console.assert(deepCompare(res.ao,{"xa" :"A1y", "xx": "X1y", "x8": "81y"}));
       const l = {0: "X1y", 26: "81y"};
       l[-40] = "A1y";
       console.assert(deepCompare(res.bo,l));
    }
    
    // procedure pindex_tab2(x in out tabvv) 
    function p1_pindex_tab2(scaller) {
        const a = {"a": {"b": "c", "x":"y", "aas": null}, "1123":{"q":"78", "w": "fgh", "df":"roland"}};
        const res = scaller("p1.pindex_tab2", {"x":a}).result;
        console.assert(deepCompare(a,res.x));
    }
    
    function p1_tests() {
        var l = [p1_p, p1_p2, p1_p3, p1_p4, p1_p5, p1_p6, p1_f7, p1_p7, p1_p8,
        p1_p9, p1_raise_error, p1_pcursor1, p1_pcursor2, p1_pcursor3, p1_pindex_tab,
        p1_pindex_tab2];
        for(var i =0;i<l.length;i++) {
            let f = l[i];
            let fn = ""+l[i] +"\n";
            let p = fn.indexOf("\n");
            console.log(fn.substring(0,p));
            f(scaller2);
        }
    }
    
    
    addclicker("p1_tests", p1_tests);
    
})();