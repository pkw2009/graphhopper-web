/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.http;

import com.google.inject.Inject;
import com.graphhopper.routing.AStar;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.util.StopWatch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class GraphHopperServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    private Graph graph;
    @Inject
    private Location2IDIndex index;
    @Inject
    private AlgorithmPreparation prepare;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String fromParam = getParam(req, "from");
        String[] fromStrs = fromParam.split(",");
        double fromLat = Double.parseDouble(fromStrs[0]);
        double fromLon = Double.parseDouble(fromStrs[1]);

        String toParam = getParam(req, "to");
        String[] toStrs = toParam.split(",");
        double toLat = Double.parseDouble(toStrs[0]);
        double toLon = Double.parseDouble(toStrs[1]);
        try {
            StopWatch sw = new StopWatch().start();
            int from = index.findID(fromLat, fromLon);
            int to = index.findID(toLat, toLon);
            float idLookupTime = sw.stop().getSeconds();

            sw = new StopWatch().start();
//            Path p = calcPath(from, to);
            Path p = calcPreparedGraphPath(from, to);
            double dist = 0;
            int locs = 0;
            List<Double[]> points;
            String infoStr;
            if (p != null) {
                infoStr = "path found";
                dist = p.distance();
                locs = p.nodes();
                points = new ArrayList<Double[]>(locs);
                for (int i = 0; i < locs; i++) {
                    int loc = p.node(i);
                    // geoJson is LON,LAT!
                    points.add(new Double[]{
                                graph.getLongitude(loc),
                                graph.getLatitude(loc)
                            });
                }
            } else {
                infoStr = "NO path found";
                points = Collections.EMPTY_LIST;
            }

            float routeLookupTime = sw.stop().getSeconds();
            JSONBuilder json = new JSONBuilder().
                    startObject("info").
                    object("time", idLookupTime + routeLookupTime).
                    object("lookupTime", idLookupTime).
                    object("routeTime", routeLookupTime).
                    endObject().
                    startObject("route").
                    object("distance", dist).
                    startObject("data").
                    object("type", "LineString").
                    object("coordinates", points).
                    endObject().
                    endObject();

            logger.info(infoStr + " " + fromLat + "," + fromLon + "->" + toLat + "," + toLon
                    + ", distance: " + dist + ", locations:" + locs
                    + ", routeLookupTime:" + routeLookupTime + ", idLookupTime:" + idLookupTime);
            writeResponse(res, json.build().toString(2));
        } catch (Exception ex) {
            logger.error("Error while query:" + fromLat + "," + fromLon + "->" + toLat + "," + toLon, ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    protected String getParam(HttpServletRequest req, String string) {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
            return l[0];
        return "";
    }

    public void writeError(HttpServletResponse res, int code, String str) {
        try {
            res.sendError(code, str);
        } catch (IOException ex) {
            logger.error("Cannot write error " + code + " message:" + str, ex);
        }
    }

    public void writeResponse(HttpServletResponse res, String str) {
        try {
            res.setStatus(SC_OK);
            res.getWriter().append(str);
        } catch (IOException ex) {
            logger.error("Cannot write message:" + str, ex);
        }
    }

    private Path calcPath(int from, int to) {
        // every request create a new independent algorithm instance (not thread safe!)
        AStar algo = new AStar(graph);
        return algo.setApproximation(false).setType(FastestCalc.DEFAULT).calcPath(from, to);
    }

    private Path calcPreparedGraphPath(int from, int to) {
        return prepare.createAlgo().calcPath(from, to);
    }
}