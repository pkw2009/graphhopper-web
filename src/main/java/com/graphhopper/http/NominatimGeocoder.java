package com.graphhopper.http;

import com.graphhopper.search.Geocoding;
import com.graphhopper.util.shapes.GHInfoPoint;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Online request for (reverse) geocoding.
 *
 * @author Peter Karich
 */
public class NominatimGeocoder implements Geocoding {

    public static void main(String[] args) {
        System.out.println("search " + new NominatimGeocoder().search("bayreuth", "berlin"));

        System.out.println("reverse " + new NominatimGeocoder().reverse(new GHPoint(49.9027606, 11.577197),
                new GHPoint(52.5198535, 13.4385964)));
    }
    private String nominatimUrl;
    private String nominatimReverseUrl;
    private BBox bounds;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private int timeoutInMillis = 10000;
    private String userAgent = "GraphHopper Web Service";

    public NominatimGeocoder() {
        this("http://open.mapquestapi.com/nominatim/v1/search.php",
                "http://open.mapquestapi.com/nominatim/v1/reverse.php");
    }

    public NominatimGeocoder(String url, String reverseUrl) {
        this.nominatimUrl = url;
        this.nominatimReverseUrl = reverseUrl;
    }

    public NominatimGeocoder bounds(BBox bounds) {
        this.bounds = bounds;
        return this;
    }

    @Override public List<GHInfoPoint> search(String... places) {
        List<GHInfoPoint> resList = new ArrayList<GHInfoPoint>();
        for (String place : places) {
            // see https://trac.openstreetmap.org/ticket/4683 why limit=3 and not 1
            String url = nominatimUrl + "?format=json&q=" + WebHelper.encodeURL(place) + "&limit=3";
            if (bounds != null) {
                // minLon, minLat, maxLon, maxLat => left, top, right, bottom
                url += "&bounded=1&viewbox=" + bounds.minLon + "," + bounds.maxLat + "," + bounds.maxLon + "," + bounds.minLat;
            }

            try {
                HttpURLConnection hConn = openConnection(url);
                String str = WebHelper.readString(hConn.getInputStream());
                // System.out.println(str);
                // TODO sort returned objects by bounding box area size?
                JSONObject json = new JSONArray(str).getJSONObject(0);
                double lat = json.getDouble("lat");
                double lon = json.getDouble("lon");
                GHInfoPoint p = new GHInfoPoint(lat, lon);
                p.name(json.getString("display_name"));
                resList.add(p);
            } catch (Exception ex) {
                logger.error("problem while geocoding (search " + place + "): " + ex.getMessage());
            }
        }
        return resList;
    }

    @Override public List<GHInfoPoint> reverse(GHPoint... points) {
        List<GHInfoPoint> resList = new ArrayList<GHInfoPoint>();
        for (GHPoint point : points) {
            try {
                String url = nominatimReverseUrl + "?lat=" + point.lat + "&lon=" + point.lon
                        + "&format=json&zoom=16";
                HttpURLConnection hConn = openConnection(url);
                String str = WebHelper.readString(hConn.getInputStream());
                // System.out.println(str);
                JSONObject json = new JSONObject(str);
                double lat = json.getDouble("lat");
                double lon = json.getDouble("lon");

                JSONObject address = json.getJSONObject("address");
                String name = "";
                if (address.has("road"))
                    name += address.get("road") + ", ";
                if (address.has("postcode"))
                    name += address.get("postcode") + " ";
                if (address.has("city"))
                    name += address.get("city") + ", ";
                else if (address.has("county"))
                    name += address.get("county") + ", ";
                if (address.has("state"))
                    name += address.get("state") + ", ";
                if (address.has("country"))
                    name += address.get("country");
                resList.add(new GHInfoPoint(lat, lon).name(name));
            } catch (Exception ex) {
                logger.error("problem while geocoding (reverse " + point + "): " + ex.getMessage());
            }
        }
        return resList;
    }

    HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection hConn = (HttpURLConnection) new URL(url).openConnection();;
        hConn.setRequestProperty("User-Agent", userAgent);
        hConn.setRequestProperty("content-charset", "UTF-8");
        hConn.setConnectTimeout(timeoutInMillis);
        hConn.setReadTimeout(timeoutInMillis);
        hConn.connect();
        return hConn;
    }

    public NominatimGeocoder timeout(int timeout) {
        this.timeoutInMillis = timeout;
        return this;
    }
}