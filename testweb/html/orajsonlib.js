
function replacer(k,o) {
    if (o instanceof Date) {
        return "date";
    }
    return o;
}

function doJsonPost(url,obj) {
  var req = new XMLHttpRequest();
  var str = JSON.stringify(obj,replacer);
  console.log(str);
  req.open("POST",url,false);
  req.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
  req.send("data="+escape(str));
	if (req.status != 200) {
		alert("JSON ERROR");
		console.log(req.responseText);
		throw "JSON ERROR";
	}
  var txt = req.responseText;
  var res = JSON.parse(txt);
	console.log(req);
  return res;
}
function deepcopy(o) {
  return JSON.parse(JSON.stringify(o));
}