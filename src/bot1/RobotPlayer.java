package bot1;

import battlecode.common.*;

import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Map;
import java.util.Random;
// import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what
 * we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    /**
     * We will use this variable to count the number of turns this robot has been
     * alive.
     * You can use static variables like this to save any information you want. Keep
     * in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between
     * your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided
     * by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant
     * number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very
     * useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
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
    static ArrayList<Integer> stashedislandLocs = null;

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world.
     * It is like the main function for your robot. If this method returns, the
     * robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this
     *           robot, and to get
     *           information on its current status. Essentially your portal to
     *           interacting with the world.
     **/
    // @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you
        // run a match!

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        Communication.initialiseComms(rc);

        while (true) {

            try {
                if (rc.canWriteSharedArray(0, 0)) {
                    Communication.reportAlive(rc); // report that we are alive!

                    if (rc.getRoundNum() % 2 == 0) {
                        WellInfo[] nearbyWells = rc.senseNearbyWells();
                        for (WellInfo r : nearbyWells)
                            Communication.reportWell(rc, r.getMapLocation());

                        Communication.clearObsoleteEnemies(rc); // remove outdated enemy locations
                        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                        for (RobotInfo r : nearbyEnemies)
                            if (r.getType() != RobotType.HEADQUARTERS)
                                Communication.reportEnemy(rc, r.getLocation());
                            else
                                Communication.reportEnemyHQ(rc, r.getLocation(), r.getID() % 16);

                        Communication.reportIsland(rc);

                    } else {

                        int stashSize = Communication.stashedislandLocs.size();
                        if (stashSize > 0) {
                            stashedislandLocs = new ArrayList<Integer>(Communication.stashedislandLocs);
                            for (int i = stashSize; --i >= 0;) {
                                Integer approxInt = stashedislandLocs.get(i);
                                if (Communication.reportIsland(rc, approxInt.intValue())) {
                                    System.out.printf("Successfully reported stashed aprroxLoc %d \n", approxInt);
                                    Communication.stashedislandLocs.remove(approxInt);
                                }
                            }
                        }
                        Communication.updateIslandType(rc);

                    }
                }

                switch (rc.getType()) {
                    case HEADQUARTERS:
                        HeadQuarters.runHeadquarters(rc);
                        break;
                    case CARRIER:
                        Carrier.runCarrier(rc);
                        break;
                    case LAUNCHER:
                        Launcher.runLauncher(rc);
                        break;
                    case BOOSTER:
                        break;// Examplefuncsplayer doesn't use any of these robot types below.
                    case DESTABILIZER:
                        break;// You might want to give them a try!
                    case AMPLIFIER:
                        Amplifier.runAmplifier(rc);
                        break;
                }

            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You
                // should
                // handle GameActionExceptions judiciously, in case unexpected events occur in
                // the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop
                // again.
                try {
                    Communication.copySharedArray(rc);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for
            // another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction
        // imminent...
    }

}

// gradlew.bat run -Pmaps=DefaultMap -PteamA=bot1 -PteamB=examplefuncsplayer >
// myLogs.txt

// run client/"Battlecode Client.exe" first and then the following command
// gradlew.bat run -x unpackClient -PwaitForClient=true -PteamA=bot1
// -PteamB=examplefuncsplayer -Pmaps=DefaultMap -PenableProfiler=false >
// myLogs.txt