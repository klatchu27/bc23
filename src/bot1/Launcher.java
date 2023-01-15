package bot1;

import battlecode.common.*;

import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;

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

    static MapLocation ownIslandLoc = null, prevOwnIslandLoc = null;
    static MapLocation reinforcementLoc = null;

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
            if (rc.canAttack(toAttack)) {
                rc.setIndicatorString("Attacking");
                rc.attack(toAttack);
            }
        }

        // go toward reinforcement Location
        if (reinforcementLoc == null)
            reinforcementLoc = Communication.getClosestReinforcementLoc(rc);

        if (reinforcementLoc != null) {
            rc.setIndicatorString("reinforcing:" + reinforcementLoc);
            Pathing.walkTowards(rc, reinforcementLoc);
            if (rc.getLocation().distanceSquaredTo(reinforcementLoc) < 5 && (rc.getRoundNum() % 2 == 1)) {
                rc.setIndicatorString("tring to clear reinf" + reinforcementLoc);
                if (Communication.clearObsoleteReinforcementLoc(rc, reinforcementLoc))
                    reinforcementLoc = null;
            }
            return;
        }

        // go toward owned islands
        if (ownIslandLoc == null) {
            ArrayList<MapLocation> ownIslandLocs = Communication.getIslandLocs(rc, 1);
            Collections.shuffle(ownIslandLocs);
            for (int i = ownIslandLocs.size(); --i >= 0;) {
                if (prevOwnIslandLoc == null || ownIslandLocs.get(i).equals(prevOwnIslandLoc) == false) {
                    ownIslandLoc = ownIslandLocs.get(i);
                    break;
                }
            }
        }

        if (ownIslandLoc != null) {
            rc.setIndicatorString("protect target ownIslandLoc:" + ownIslandLoc);
            Pathing.walkTowards(rc, ownIslandLoc);
            if (rc.getLocation().distanceSquaredTo(ownIslandLoc) < 5) {

                RobotInfo[] ownTroops = rc.senseNearbyRobots(ownIslandLoc, -1, rc.getTeam());
                RobotInfo[] enemyTroops = rc.senseNearbyRobots(ownIslandLoc, -1, rc.getTeam().opponent());
                int ownCount = 0, enemyCount = 0;
                for (RobotInfo r : ownTroops)
                    if (r.getType() == RobotType.LAUNCHER)
                        ownCount++;
                for (RobotInfo r : enemyTroops)
                    if (r.getType() == RobotType.LAUNCHER)
                        enemyCount++;
                if ((ownCount >= 4 && enemyCount == 0) || (ownCount - enemyCount) >= 2) {
                    System.out.printf("ISLAND SECURE:%d,%d \n", ownIslandLoc.x, ownIslandLoc.y);
                    prevOwnIslandLoc = ownIslandLoc;
                    ownIslandLoc = null;
                }
            }
            return;
        }

        MapLocation targetLocation = Communication.getClosestEnemy(rc);
        if (targetLocation != null) {
            // System.out.println(String.format("Got CLOSEST ENEMY from comms @ " +
            // targetLocation));
            rc.setIndicatorString(
                    String.format("closest enemy comms loc: (%d,%d)", targetLocation.x, targetLocation.y));
            Pathing.walkTowards(rc, targetLocation);
            return;
        }

        // Also try to move randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
