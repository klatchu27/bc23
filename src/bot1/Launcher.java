package bot1;

import battlecode.common.*;

import java.util.Random;
import java.util.ArrayList;

public class Launcher {

    static final Random rng = new Random(6147);
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static MapLocation ownIslandLoc = null, enemyIslandLoc = null;

    /**
     * Run a single turn for a Launcher.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runLauncher(RobotController rc) throws GameActionException {

        // Try to attack someone
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length > 0) {
            MapLocation toAttack = enemies[0].getLocation();
            // MapLocation toAttack = rc.getLocation().add(Direction.EAST);

            if (rc.canAttack(toAttack)) {
                rc.setIndicatorString("Attacking");
                rc.attack(toAttack);
            }
        }

        // go toward owned islands
        if (ownIslandLoc == null) {
            ArrayList<MapLocation> ownIslandLocs = Communication.getIslandLocs(rc, 1);
            if (ownIslandLocs.size() > 0)
                ownIslandLoc = ownIslandLocs.get(rng.nextInt(ownIslandLocs.size()));
        }

        if (ownIslandLoc != null) {
            rc.setIndicatorString("protect target ownIslandLoc:" + ownIslandLoc);
            Pathing.walkTowards(rc, ownIslandLoc);
            if (rc.getLocation().distanceSquaredTo(ownIslandLoc) < 5)
                ownIslandLoc = null;
            return;
        }

        MapLocation targetLocation = Communication.getClosestEnemy(rc);
        if (targetLocation != null) {
            // System.out.println(String.format("Got CLOSEST ENEMY from comms @ " +
            // targetLocation));
            rc.setIndicatorString(
                    String.format("closest enemy comms loc: (%d,%d)", targetLocation.x, targetLocation.y));
            Pathing.walkTowards(rc, targetLocation);
        }

        // Also try to move randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
