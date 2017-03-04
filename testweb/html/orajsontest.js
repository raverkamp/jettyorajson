

(function () {

    function post() {
        var res = doJsonPost("/orajson", {procedure: "bla", arguments: {x: "roland"}});
        console.log(res);
    }

    var b1 = document.getElementById("b1");
    b1.addEventListener("click", function () {
        callproc();
    });
    
   var b2 = document.getElementById("b2");
    b2.addEventListener("click", function () {
        callHeaders();
    });

    var caller = orajsoncaller("/orajson");
    // p1.procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date) 
    function callproc() {
        var args = {xi: 23, yi: "asas", zi: "2015-12-04"};
        var res = caller("p1_p",args);
        console.log(res);
    }
    
    function callHeaders() {
        var args = {};
        var res = caller("p1_p",args);
        console.log(res);
    }


})();