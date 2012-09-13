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
package de.jetsli.graph.http;

import com.google.inject.Inject;
import de.jetsli.graph.routing.AStar;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.util.StopWatch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

import static javax.servlet.http.HttpServletResponse.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServlet extends HttpServlet {

    private Logger logger = Logger.getLogger(getClass());
    @Inject
    private Graph graph;
    @Inject
    private Location2IDIndex index;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        try {
            String fromParam = getParam(req, "from");
            String[] fromStrs = fromParam.split(",");
            double fromLat = Double.parseDouble(fromStrs[0]);
            double fromLon = Double.parseDouble(fromStrs[1]);

            String toParam = getParam(req, "to");
            String[] toStrs = toParam.split(",");
            double toLat = Double.parseDouble(toStrs[0]);
            double toLon = Double.parseDouble(toStrs[1]);

            StopWatch sw = new StopWatch().start();
            int from = index.findID(fromLat, fromLon);
            int to = index.findID(toLat, toLon);
            float lookupTime = sw.stop().getSeconds();

            sw = new StopWatch().start();
            Path p = new AStar(graph).calcPath(from, to);
            int locs = p.locations();
            List<Double[]> points = new ArrayList<Double[]>(locs);
            for (int i = 0; i < locs; i++) {
                int loc = p.location(i);
                points.add(new Double[]{
                            graph.getLatitude(loc),
                            graph.getLongitude(loc)});
            }
            float routeTime = sw.stop().getSeconds();
            JSONBuilder json = new JSONBuilder().
                    startObject("info").
                    object("time", lookupTime + routeTime).
                    object("lookupTime", lookupTime).
                    object("routeTime", routeTime).
                    endObject().
                    startObject("route").
                    object("points", points).
                    object("distance", p.distance()).
                    endObject();

            writeResponse(res, json.build().toString(2));
        } catch (Exception ex) {
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
            res.getWriter().append(str);
        } catch (IOException ex) {
            logger.error("Cannot write message:" + str, ex);
        }
    }
}