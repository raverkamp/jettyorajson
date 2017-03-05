"use strict";

function replacer(k, o) {
    if (o instanceof Date) {
        return "date";
    }
    return o;
}

function doJsonPostSync(url, obj) {
    var req = new XMLHttpRequest();
    var str = JSON.stringify(obj, replacer);
    req.open("POST", url, false);
    req.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    req.send(str);
    if (req.status !== 200) {
        alert("JSON ERROR");
        console.log(req.responseText);
        throw "JSON ERROR";
    }
    var txt = req.responseText;
    var res = JSON.parse(txt);
    return res;
}

function orajsoncallersync(baseurl) {
    return function(proc,args) {
        var res = doJsonPostSync(baseurl, {procedure: proc, arguments: args});
        return res;
    };
}

function doJsonPostAsync(url, obj,success, dbexception, totalfailure) {
    //console.log("async", obj);
    var req = new XMLHttpRequest();
    req.onload = function(e) {
        if (req.readyState === 4) {
            if (req.status === 200) {
                var txt = req.responseText;
                var res = JSON.parse(txt);
                if (res.result) {
                    success(res.result);
                } else {
                    console.log("orajson error",res);
                    if (dbexception) {
                        dbexception(res.error);
                    } else {
                        throw res.error;
                    }
                }
            } else {
                console.log("orajson error",req.status, txt);
                if (totalfailure) {
                    totalfailure(req);
                } else {
                    throw "" + (req);
                }
            }
        }
    };
    var str = JSON.stringify(obj, replacer);
    req.open("POST", url, true);
    req.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    req.send(str);
}

  function orajsoncallerasync(baseurl) {
    return function(proc,args,success, dbfailure,totalfailure) {
        console.log("args", args);
        doJsonPostAsync(baseurl, {procedure: proc, "arguments": args}, success,dbfailure,totalfailure);
    };
}

function deepcopy(o) {
    return JSON.parse(JSON.stringify(o));
}