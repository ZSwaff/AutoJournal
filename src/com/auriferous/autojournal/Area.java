package com.auriferous.autojournal;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.location.Location;

public class Area {
    private static double marginOfError = 5; // in feet
    private static double radiusEarth = 3959 * 5280; // in feet

    String name;
    Location center = null;
    double radius = 0; // in feet
    AreaType type = null;

    public Area(String initName, Location initCenter, double initRadius) {
        name = initName;
        center = initCenter;
        radius = initRadius;
    }

    public Area(String initName, ArrayList<Location> locs) {
    	name = initName;
    	
    	double maxDist = 0;
    	Location maxLoc1 = null, maxLoc2 = null;
    	for(Location loc1 : locs){
    		for(Location loc2 : locs){
        		if(loc1 == loc2) continue;
        		double currDist = calculateDistanceBetween(loc1, loc2);
        		if(currDist > maxDist){
        			maxDist = currDist;
        			maxLoc1 = loc1;
        			maxLoc2 = loc2;
        		}
        	}
    	}
    	
    	center = calculateMidpoint(maxLoc1, maxLoc2);
    	
    	for(Location loc1 : locs){
    		double potRadius = calculateDistanceBetween(loc1, center);
    		if(potRadius > radius){
    			radius = potRadius;
    		}
    	}
    }
    
    //old plan
    // calculates minimum covering circle
    public void Area2(String initName, ArrayList<Location> locs) {
        name = initName;
        if (locs.size() == 0)
            return;

        if (locs.size() == 1) {
            center = locs.get(0);
            return;
        }

        // http://www.cs.mcgill.ca/~cs507/projects/1998/jacob/problem.html

        // choose a point, make it the center
        Location currCenter = locs.get(0);

        // find the furthest point from there
        Location furthest = locs.get(1);
        double furthestDist = calculateDistanceBetween(currCenter, furthest);
        for (int i = 2; i < locs.size(); i++) {
            double potDist = calculateDistanceBetween(currCenter, locs.get(i));
            if (potDist > furthestDist) {
                furthestDist = potDist;
                furthest = locs.get(i);
            }
        }

        // start a bsearch moving the center toward the far point
        Location outerBound = currCenter;
        Location innerBound = furthest;
        double currRadius = calculateDistanceBetween(currCenter, furthest);
        Area currArea = new Area("", currCenter, currRadius);
        while (true) {
            currRadius = calculateDistanceBetween(currCenter, furthest);
            currArea = new Area("", currCenter, currRadius);
            boolean pointsOutside = false;
            int pointOnEdgeCount = 0;

            for (Location locToCheck : locs) {
                if (!currArea.doesContain(locToCheck)) {
                    pointsOutside = true;
                    break;
                }
                if (currArea.isOnBorder(locToCheck))
                    pointOnEdgeCount++;
            }
            if (!pointsOutside && (pointOnEdgeCount > 1))
                break;

            if (pointsOutside) {
                innerBound = currCenter;
                currCenter = calculateMidpoint(currCenter, outerBound);
            } else {
                outerBound = currCenter;
                currCenter = calculateMidpoint(currCenter, innerBound);
            }
        }

        while (true) {
            // store the other point(s) it hits
            ArrayList<Location> pointsOnEdge = new ArrayList<Location>();
            for (Location locToCheck : locs) {
                if (currArea.isOnBorder(locToCheck))
                    pointsOnEdge.add(locToCheck);
            }

            // if there is no arc > ~185 degrees, break
            Collections.sort(pointsOnEdge, new LocComparator(currArea));
            Location pivot1 = pointsOnEdge.get(0);
            Location pivot2 = pointsOnEdge.get(1);
            for (int i = 2; i <= pointsOnEdge.size(); i++) {
                if ((currArea.calculateAngleWith(pivot2) - currArea.calculateAngleWith(pivot1)) > PI * 1.03d) {
                    break;
                }
                pivot1 = pivot2;
                pivot2 = pointsOnEdge.get(i % pointsOnEdge.size());
            }
            if ((currArea.calculateAngleWith(pivot2) - currArea.calculateAngleWith(pivot1)) <= PI * 1.03d)
                break;

            // otherwise, bsearch the center along the bisector of the two ends of the arc, shrinking the radius, until a new edge point is found
            outerBound = currCenter;
            innerBound = calculateMidpoint(pivot1, pivot2);
            while (true) {
                currRadius = calculateDistanceBetween(currCenter, pivot1);
                currArea = new Area("", currCenter, currRadius);
                boolean pointsOutside = false;
                int pointOnEdgeCount = 0;

                for (Location locToCheck : locs) {
                    if (!currArea.doesContain(locToCheck)) {
                        pointsOutside = true;
                        break;
                    }
                    if (currArea.isOnBorder(locToCheck))
                        pointOnEdgeCount++;
                }
                if (!pointsOutside && (pointOnEdgeCount > 2))
                    break;

                if (pointsOutside) {
                    innerBound = currCenter;
                    currCenter = calculateMidpoint(currCenter, outerBound);
                } else {
                    outerBound = currCenter;
                    currCenter = calculateMidpoint(currCenter, innerBound);
                }
            }
        }
        center = currArea.center;
        radius = currArea.radius;
    }

    public boolean doesContain(Location loc) {
        return (calculateDistanceBetween(center, loc) - marginOfError < radius);
    }

    public boolean isOnBorder(Location loc) {
        return (abs(calculateDistanceBetween(center, loc) - radius) < marginOfError);
    }

    public double calculateAngleWith(Location loc) {
        return atan2(loc.getLongitude() - center.getLongitude(), loc.getLatitude() - center.getLatitude());
    }

    public boolean doesOverlap(Area otherArea) {
        return (calculateDistanceBetween(center, otherArea.center) < radius + otherArea.radius + marginOfError * 2);
    }

    @Override
    public String toString() {
        return name + "  Center: " + Converter.locToString(center) + "  Radius: " + ((int)radius);
    }

    public static Location calculateMidpoint(Location loc1, Location loc2) {
        double dLon = toRadians(loc2.getLongitude() - loc1.getLongitude());

        double lat1 = toRadians(loc1.getLatitude());
        double lat2 = toRadians(loc2.getLatitude());
        double lng1 = toRadians(loc1.getLongitude());

        double Bx = cos(lat2) * cos(dLon);
        double By = cos(lat2) * sin(dLon);
        double resultLat = atan2(sin(lat1) + sin(lat2), sqrt((cos(lat1) + Bx) * (cos(lat1) + Bx) + By * By));
        double resultLng = lng1 + atan2(By, cos(lat1) + Bx);

        return Converter.latAndLngToLoc(toDegrees(resultLat), toDegrees(resultLng));
    }

    public static double calculateDistanceBetween(Location loc1, Location loc2) {
        double ang1 = toRadians(loc1.getLatitude());
        double ang2 = toRadians(loc2.getLatitude());
        double dang = toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dlong = toRadians(loc2.getLongitude() - loc2.getLongitude());

        double a = sin(dang / 2) * sin(dang / 2) + cos(ang1) * cos(ang2) * sin(dlong / 2) * sin(dlong / 2);
        double c = 2 * atan2(sqrt(a), sqrt(1 - a));

        return radiusEarth * c;
    }

    public enum AreaType {
        LOCALE,
        CITY,
        SEARCH;
    }
}

class LocComparator implements Comparator<Location> {
    Area area;

    LocComparator(Area initArea) {
        area = initArea;
    }

    public int compare(Location loc1, Location loc2) {
        double angle1 = area.calculateAngleWith(loc1);
        double angle2 = area.calculateAngleWith(loc2);
        if (angle2 > angle1)
            return 1;
        if (angle2 < angle1)
            return -1;
        return 0;
    }
}