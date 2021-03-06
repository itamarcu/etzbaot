package org.ajwerner.voronoi;

import java.util.*;

/**
 * Created by ajwerner on 12/23/13.
 * <p>
 * Edited by itamarcu on 2017-12-11.
 */
public class Voronoi
{
    private double sweepLoc;
    private final ArrayList<Site> sites;
    public final ArrayList<VoronoiEdge> edgeList;
    private HashSet<BreakPoint> breakPoints;
    private TreeMap<ArcKey, CircleEvent> arcs;
    private TreeSet<Event> events;
    
    public double getSweepLoc()
    {
        return sweepLoc;
    }
    
    public Voronoi(ArrayList<Site> sites, double MIN_X, double MIN_Y, double MAX_X, double MAX_Y)
    {
        // initialize data structures;
        this.sites = sites;
        edgeList = new ArrayList<VoronoiEdge>(sites.size());
        events = new TreeSet<Event>();
        breakPoints = new HashSet<BreakPoint>();
        arcs = new TreeMap<ArcKey, CircleEvent>();
        
        for (Point site : sites)
        {
            if ((site.x > MAX_X || site.x < MIN_X) || (site.y > MAX_Y || site.y < MIN_Y))
                throw new RuntimeException("Invalid site in input, sites must be between min and max x and y");
            events.add(new Event(site));
        }
        sweepLoc = MAX_Y;
        do
        {
            Event cur = events.pollFirst();
            sweepLoc = cur.p.y;
            if (cur.getClass() == Event.class)
            {
                handleSiteEvent(cur);
            }
            else
            {
                CircleEvent ce = (CircleEvent) cur;
                handleCircleEvent(ce);
            }
        }
        while ((events.size() > 0));
        
        for (BreakPoint bp : breakPoints)
        {
            bp.finish(MIN_Y, MAX_Y);
        }
        for (VoronoiEdge edge : edgeList)
        {
            if (edge.p1.y == Double.POSITIVE_INFINITY)
                edge.p1 = new Point(edge.p1.x, MAX_Y);
        }
    }
    
    private void handleSiteEvent(Event cur)
    {
        // Deal with first point case
        if (arcs.size() == 0)
        {
            arcs.put(new Arc(cur.p, this), null);
            return;
        }
        
        // Find the arc above the site
        Map.Entry<ArcKey, CircleEvent> arcEntryAbove = arcs.floorEntry(new ArcQuery(cur.p));
        Arc arcAbove = (Arc) arcEntryAbove.getKey();
        
        // Deal with the degenerate case where the first two points are at the same y value
        if (arcs.size() == 0 && arcAbove.site.y == cur.p.y)
        {
            VoronoiEdge newEdge = new VoronoiEdge(arcAbove.site, cur.p);
            newEdge.p1 = new Point((cur.p.x + arcAbove.site.x) / 2, Double.POSITIVE_INFINITY);
            BreakPoint newBreak = new BreakPoint(arcAbove.site, cur.p, newEdge, false, this);
            breakPoints.add(newBreak);
            this.edgeList.add(newEdge);
            Arc arcLeft = new Arc(null, newBreak, this);
            Arc arcRight = new Arc(newBreak, null, this);
            arcs.remove(arcAbove);
            arcs.put(arcLeft, null);
            arcs.put(arcRight, null);
            return;
        }
        
        // Remove the circle event associated with this arc if there is one
        CircleEvent falseCE = arcEntryAbove.getValue();
        if (falseCE != null)
        {
            events.remove(falseCE);
        }
        
        BreakPoint breakL = arcAbove.left;
        BreakPoint breakR = arcAbove.right;
        VoronoiEdge newEdge = new VoronoiEdge(arcAbove.site, cur.p);
        edgeList.add(newEdge);
        BreakPoint newBreakL = new BreakPoint(arcAbove.site, cur.p, newEdge, true, this);
        BreakPoint newBreakR = new BreakPoint(cur.p, arcAbove.site, newEdge, false, this);
        breakPoints.add(newBreakL);
        breakPoints.add(newBreakR);
        
        Arc arcLeft = new Arc(breakL, newBreakL, this);
        Arc center = new Arc(newBreakL, newBreakR, this);
        Arc arcRight = new Arc(newBreakR, breakR, this);
        
        arcs.remove(arcAbove);
        arcs.put(arcLeft, null);
        arcs.put(center, null);
        arcs.put(arcRight, null);
        
        checkForCircleEvent(arcLeft);
        checkForCircleEvent(arcRight);
    }
    
    private void handleCircleEvent(CircleEvent ce)
    {
        Arc arcRight = (Arc) arcs.higherKey(ce.arc);
        Arc arcLeft = (Arc) arcs.lowerKey(ce.arc);
        if (arcRight != null)
        {
            CircleEvent falseCe = arcs.get(arcRight);
            if (falseCe != null)
                events.remove(falseCe);
            arcs.put(arcRight, null);
        }
        if (arcLeft != null)
        {
            CircleEvent falseCe = arcs.get(arcLeft);
            if (falseCe != null)
                events.remove(falseCe);
            arcs.put(arcLeft, null);
        }
        arcs.remove(ce.arc);
        
        ce.arc.left.finish(ce.vert);
        ce.arc.right.finish(ce.vert);
        
        breakPoints.remove(ce.arc.left);
        breakPoints.remove(ce.arc.right);
        
        VoronoiEdge e = new VoronoiEdge(ce.arc.left.s1, ce.arc.right.s2);
        edgeList.add(e);
        
        // Here we're trying to figure out if the org.ajwerner.voronoi.Voronoi vertex we've found is the left
        // or right point of the new edge.
        // If the edges being traces out by these two arcs take a right turn then we know
        // that the vertex is going to be above the current point
        boolean turnsLeft = Point.ccw(arcLeft.right.edgeBegin, ce.p, arcRight.left.edgeBegin) == 1;
        // So if it turns left, we know the next vertex will be below this vertex
        // so if it's below and the slow is negative then this vertex is the left point
        boolean isLeftPoint = (turnsLeft) ? (e.slope < 0) : (e.slope > 0);
        if (isLeftPoint)
        {
            e.p1 = ce.vert;
        }
        else
        {
            e.p2 = ce.vert;
        }
        BreakPoint newBP = new BreakPoint(ce.arc.left.s1, ce.arc.right.s2, e, !isLeftPoint, this);
        breakPoints.add(newBP);
        
        arcRight.left = newBP;
        arcLeft.right = newBP;
        
        checkForCircleEvent(arcLeft);
        checkForCircleEvent(arcRight);
    }
    
    private void checkForCircleEvent(Arc a)
    {
        Point circleCenter = a.checkCircle();
        if (circleCenter != null)
        {
            double radius = a.site.distanceTo(circleCenter);
            Point circleEventPoint = new Point(circleCenter.x, circleCenter.y - radius);
            CircleEvent ce = new CircleEvent(a, circleEventPoint, circleCenter);
            arcs.put(a, ce);
            events.add(ce);
        }
    }
}

