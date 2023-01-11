package bot1;

import battlecode.common.*;

import java.util.Random;

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
        } else {
            MapLocation targetLocation = Communication.getClosestEnemy(rc);
            if (targetLocation != null) {
                rc.setIndicatorString(String.format("target loc: (%d,%d)", targetLocation.x, targetLocation.y));
                Pathing.walkTowards(rc, targetLocation);
            }

        }

        // Also try to move randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
