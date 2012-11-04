var routingLayer;
var map;
var browserTitle = "GraphHopper Web Demo";
var startPoint = null;
var errCallback = function(err) {
    alert("error:"+ err.statusText + ", " + err.responseText);
};
var jsonType = false;

$(document).ready(function(e) {
    var History = window.History;
    if (History.enabled) {
        History.Adapter.bind(window, 'statechange', function(){
            var state = History.getState();
            console.log(state);            
            initFromUrl(parseUrl(state.url));
        });
    }
        
    initMap(requestCenter());
    initFromUrl(parseUrlWithHisto());
});
            
function initFromUrl(paramMap) {    
    var from = {};      
    if(paramMap.from) {
        try {
            var index = paramMap.from.indexOf(",");
            from.lat = parseFloat(paramMap.from.substr(0, index));
            from.lng = parseFloat(paramMap.from.substr(index + 1));
        } catch(ex) {            
        }
    }
    
    if(!from.lat || !from.lng) {
        from.lat = 52.532932;
        from.lng = 13.4;
    }    
    if(paramMap.from && paramMap.to)
        route(paramMap.from, paramMap.to);
    else
        console.log("cannot find/parse from or to parameter?");
}

function initMap(center) {
    console.log("init map at " + center.lat + "," + center.lng);
    map = L.map('map', {
        center: [center.lat, center.lng],
        zoom: 10
    });
    L.tileLayer('http://{s}.tile.cloudmade.com/{key}/{styleId}/256/{z}/{x}/{y}.png', {
        key: '43b079df806c4e03b102055c4e1a8ba8',
        styleId: 997
    }).addTo(map);
    routingLayer = L.geoJson().addTo(map);
    // TODO limit area to underlying routing graph bounds!
    // max.setMaxBounds( <LatLngBounds> bounds ) 
    
    var popup = L.popup();    
    function onMapClick(e) {        
        if(!startPoint) {
            popup.setLatLng(e.latlng).setContent("Start").openOn(map);
            startPoint = e.latlng;
        } else {
            popup.setLatLng(e.latlng).setContent("End").openOn(map);
            var endPoint = e.latlng;
            route(startPoint.lat + "," + startPoint.lng, endPoint.lat + "," + endPoint.lng);
        }
    }

    map.on('click', onMapClick);
}

function route(start, end) {
    startPoint = null;
    routingLayer.clearLayers();
    doRequest(start, end, function (json) {
                
        // json.route.data needs to be in geoJson format => where a points is LON,LAT!
        // http://leaflet.cloudmade.com/examples/geojson.html
        // some more code for geoJson
        // https://github.com/CloudMade/Leaflet/issues/327
        // https://github.com/CloudMade/Leaflet/issues/822
        var myStyle = {
            "color": 'blue',
            "weight": 5,
            "opacity": 0.55
        };

        var geojsonFeature = {
            "type": "Feature",                   
            // "style": myStyle,                
            "geometry": json.route.data
        };
        routingLayer.addData(geojsonFeature);
        
        $("#info").empty();
        var distDiv = $("<div/>");
        distDiv.html("distance: " + Math.round(json.route.distance * 1000) / 1000
            + "km, time: " + Math.round(json.route.time) 
            + "min, took: " + Math.round(json.info.took * 1000) / 1000 + "s"); 
        $("#info").append(distDiv);
        var googleLink = $("<a>Google</a>");
        googleLink.attr("href", "http://maps.google.com/?q=from:" + start + "+to:" + end);
        $("#info").append(googleLink);
        var osrmLink = $("<br/><a>OSRM</a>");
        osrmLink.attr("href", "http://map.project-osrm.org/?loc=" + start + "&loc=" + end);
        $("#info").append(osrmLink);
        
    });
}

function doRequest(from, to, callback) {
    // http://localhost:8989/api?from=52.439688,13.276863&to=52.532932,13.479424
    var host = location.protocol + "//" + location.host;    
    var demoUrl = "?from=" + from + "&to=" + to;
    var url;
    if(jsonType) {
        url = host + "/api" + demoUrl + "&type=json"; // &debug=true
        $.ajax({
            "url" : url, 
            "success": callback,
            "error" : errCallback,
            "type" : "GET",
            "dataType" : "json"
        });
    } else {
        // we need a very efficient way to get the probably huge number of points
        url = host + "/api" + demoUrl + "&type=bin";             
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'arraybuffer';
        xhr.onload = function(e) {
            if (this.status == 200) {
                var dv = new DataView(this.response);
                var json = {
                    "info" : {
                        "took" : 0
                    },
                    "route": {
                        "time": 0, 
                        "distance": 0, 
                        "data" : {}
                    }
                };
                
                var i = 0;
                json.info.took = dv.getFloat32(i);                
                i += 4;
                json.route.distance = dv.getFloat32(i);
                i += 4;
                json.route.time = dv.getInt32(i);
                i += 4;
                var locations = dv.getInt32(i);
                var tmpArray = [];
                json.route.data = {
                    "type" : "LineString",
                    "coordinates": tmpArray
                };
                for(var index = 0; index < locations; index ++) {
                    i += 4;
                    var lat = dv.getFloat32(i);
                    i += 4;
                    var lng = dv.getFloat32(i);
                    tmpArray.push([lng, lat]);
                }            
                callback(json);
            } else
                errCallback(e);
        };
        xhr.send();
    }
    History.pushState({}, browserTitle, demoUrl);
    console.log(url);    
}

function requestCenter() {
    var host = location.protocol + "//" + location.host;    
    var url = host + "/api/bounds";
    console.log(url);
    var center = {
        lat : 0, 
        lng : 0
    };
    $.ajax({
        "url": url,
        "async": false,
        "success": function(json) {
            var bounds = json.bbox;
            var minLon = bounds[0];
            var minLat = bounds[1];
            var maxLon = bounds[2];
            var maxLat = bounds[3];
            center.lat = (minLat + maxLat) / 2;
            center.lng = (minLon + maxLon) / 2;
        },
        "error" : errCallback,
        "type" : "GET",
        "dataType" : "json"
    });
    
    return center;
}

function parseUrlWithHisto() {
    if(window.location.hash) 
        return parseUrl(window.location.hash);
    
    return parseUrl(window.location.search);
}
function parseUrlAndRequest() {
    return parseUrl(window.location.search);
}
function parseUrl(query) {
    var index = query.indexOf('?');
    if(index >= 0)
        query = query.substring(index + 1);
    var res = {};        
    var vars = query.split("&");
    for (var i=0;i < vars.length;i++) {
        var pair = vars[i].split("=");
        if(pair.length > 1 && pair[1] != null)
            pair[1] = decodeURIComponent(pair[1].replace(/\+/g,' '));
                        
        if (typeof res[pair[0]] === "undefined")
            res[pair[0]] = pair[1];
        else if (typeof res[pair[0]] === "string") {
            var arr = [ res[pair[0]], pair[1] ];
            res[pair[0]] = arr;
        } else
            res[pair[0]].push(pair[1]);                   
    } 
    return res;
}
