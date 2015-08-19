

(function () {

    function post() {
        var res = doJsonPost("/orajson", {procedure: "bla", arguments: {x: "roland"}});
        console.log(res);
    }

    var b1 = document.getElementById("b1");
    b1.addEventListener("click", function () {
        callproc();
    });

    // p1.procedure p(xi number,yi varchar2,zi date,xo out number,yo out varchar2,zo out date) 
    function callproc() {
        var args = {XI: 23, YI: "asas", ZI: "2015-12-04"};
        var res = doJsonPost("/orajson", {procedure: "p1_p", arguments: args});
        console.log(res);
    }


})();