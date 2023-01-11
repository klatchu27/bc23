package bot1;

import battlecode.common.*;

import java.util.Random;

public strictfp class HeadQuarters {

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
    static final RobotType[] troopTypes = {
            RobotType.HEADQUARTERS,
            RobotType.CARRIER,
            RobotType.LAUNCHER,
            RobotType.AMPLIFIER,
            RobotType.BOOSTER,
            RobotType.DESTABILIZER,
    };

    public enum Index {
        HEADQUARTERS(0), CARRIER(1), LAUNCHER(2), AMPLIFIER(3), BOOSTER(4), DESTABILIZER(5);

        private final int index;

        private Index(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }

    private static final int NUM_TYPES = 6;
    static int[] troopsAlive = new int[NUM_TYPES];
    static double[] minTroops = { 0.0, 4.0, 1.0, 2.0, 0.0, 0.0 };

    // this order is executed in reverse to save bytecode
    static final int[] indexOfTroopsBuildOrder = {
            Index.DESTABILIZER.getIndex(),
            Index.BOOSTER.getIndex(),
            Index.AMPLIFIER.getIndex(),
            Index.LAUNCHER.getIndex(),
            Index.CARRIER.getIndex(),
    };
    static int[][] ExplorationHeatMap = null;
    static boolean stillExploring = true;
    static int totalResources = 0, adamantium = 0, mana = 0, resourceTypeRequired = 0;
    static int standardAnchors = 0, MIN_STANDARD_ANCHOR = 1;

    /**
     * Run a single turn for a Headquarters.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runHeadquarters(RobotController rc) throws GameActionException {

        addExplorationLoc(rc);

        MapLocation curLoc = rc.getLocation();
        adamantium = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        mana = rc.getResourceAmount(ResourceType.MANA);
        totalResources = adamantium + mana;
        standardAnchors = rc.getNumAnchors(Anchor.STANDARD);

        // calc the resource required an reporting to comms
        resourceTypeRequired = 2;
        if (2 * mana > 3 * adamantium)
            resourceTypeRequired = 1;// if Mn>1.5*Ad then set req to Ad
        if (standardAnchors > 0)
            resourceTypeRequired |= 4;
        rc.setIndicatorString(String.format("resourceTypeReq: %d", resourceTypeRequired));
        Communication.reportOwnHQ(rc, curLoc, resourceTypeRequired);

        // get count of troops alive of each type from last round
        for (int i = NUM_TYPES - 1; --i >= 0;) {
            troopsAlive[i] = Communication.getAlive(rc, troopTypes[i]);
        }

        double thresholdIndex = totalResources / 200.0;
        for (int i = indexOfTroopsBuildOrder.length; --i > 0;)
            if (troopsAlive[indexOfTroopsBuildOrder[i]] < Math.max(1,
                    Math.floor(minTroops[indexOfTroopsBuildOrder[i]] * thresholdIndex)))
                if (build(rc, troopTypes[indexOfTroopsBuildOrder[i]]))
                    break;

        if (standardAnchors < MIN_STANDARD_ANCHOR)
            if (rc.canBuildAnchor(Anchor.STANDARD)) {
                // If we can build an anchor do it!
                // System.out.println("SUCCESSFULLY built ANCHOR!!");
                rc.buildAnchor(Anchor.STANDARD);
                rc.setIndicatorString("Building anchor! " + rc.getAnchor());
            }
    }

    static boolean build(RobotController rc, RobotType type) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        for (Direction d : directions) {
            MapLocation newLoc = curLoc.add(d);
            if (rc.canBuildRobot(type, newLoc)) {
                rc.buildRobot(type, newLoc);
                switch (type) {
                    case CARRIER:
                        troopsAlive[Index.CARRIER.getIndex()]++;
                        return true;
                    case LAUNCHER:
                        troopsAlive[Index.LAUNCHER.getIndex()]++;
                        return true;
                    case AMPLIFIER:
                        troopsAlive[Index.AMPLIFIER.getIndex()]++;
                        return true;
                    default:
                        return true;
                }
            }
        }
        return false;
    }

    static void addExplorationLoc(RobotController rc) {

        int width = rc.getMapWidth(), height = rc.getMapHeight();
        if (ExplorationHeatMap == null) {
            System.out.println(String.format("Byte code left b4 = %d", Clock.getBytecodesLeft()));
            ExplorationHeatMap = new int[width][height];
            System.out.println(String.format("Byte code left after = %d", Clock.getBytecodesLeft()));
        }

        int slot = -1;
        outer: while (stillExploring) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (ExplorationHeatMap[x][y] == 0) {

                        slot = Communication.reportExploreLoc(rc, new MapLocation(x, y), true);

                        // updating heat map with 4*4 unit**2(square distance)
                        if (slot != -1) {
                            for (int dx = -4; dx <= 4; dx++) {
                                for (int dy = -4; dy <= 4; dy++) {
                                    if (x + dx >= 0 && x + dx < width && y + dy >= 0 && y + dy < height)
                                        ExplorationHeatMap[x + dx][y + dy] = 1;
                                }
                            }
                        } else
                            break outer;
                    }
                }
            }
            stillExploring = false;
        }

    }

}
