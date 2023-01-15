package bot1;

import battlecode.common.*;

import java.util.Random;
// import java.util.Arrays;

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

    private static final int NUM_TYPES = 6;
    static int[] troopsAlive = new int[NUM_TYPES];
    static int[] troopsBuilt = new int[NUM_TYPES];
    static int anchorsBuilt = 0;

    static double[] minTroops = { 0, 4, 4, 2, 0, 0 };
    static int[] maxTroopsBuilt = { 0, 100, 100, 3, 0, 0 };
    static int maxAnchorsBuilt = 3;

    static int[][] ExplorationHeatMap = null;
    static int exploreX = 0, exploreY = 0, scaleX = 3, scaleY = 3;

    static int totalResources = 0, adamantium = 0, mana = 0, resourceTypeRequired = 0;
    static int standardAnchors = 0, MIN_STANDARD_ANCHOR = 2;
    static MapLocation curLoc = null;
    static boolean underAttack = false;

    /**
     * Run a single turn for a Headquarters.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runHeadquarters(RobotController rc) throws GameActionException {

        if (rc.getRoundNum() % 4 == 0)
            addExplorationLoc(rc);

        curLoc = rc.getLocation();
        adamantium = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        mana = rc.getResourceAmount(ResourceType.MANA);
        totalResources = adamantium + mana;
        standardAnchors = rc.getNumAnchors(Anchor.STANDARD);

        // calc the resource required an reporting to comms: 2->corresonponds to MANA
        resourceTypeRequired = 2;
        if (10 * mana > 13 * adamantium && underAttack == false)
            resourceTypeRequired = 1;// if Mn>1.3*Ad then set req to Ad
        if (standardAnchors > 0)
            resourceTypeRequired |= 4;
        rc.setIndicatorString(String.format("resourceTypeReq: %d", resourceTypeRequired));

        if (rc.getRoundNum() % 2 == 1) {
            checkEnemyActivity(rc);
        }

        if (rc.getRoundNum() % 2 == 0)
            Communication.reportOwnHQ(rc, curLoc, resourceTypeRequired);

        // get count of troops alive of each type from last round
        for (int i = NUM_TYPES; --i >= 0;)
            troopsAlive[i] = Communication.getAlive(rc, troopTypes[i]);

        // build order logic
        int round = rc.getRoundNum();
        if (round > 100 && 50 * anchorsBuilt < round * maxAnchorsBuilt && standardAnchors < MIN_STANDARD_ANCHOR) {
            if (rc.canBuildAnchor(Anchor.STANDARD) && rc.isActionReady()) {
                rc.buildAnchor(Anchor.STANDARD);
                anchorsBuilt++;
                rc.setIndicatorString("Building anchor! " + rc.getNumAnchors(Anchor.STANDARD));
            }
        }

        if (rc.getRobotCount() > 50 && standardAnchors < MIN_STANDARD_ANCHOR)
            return;

        if (troopsAlive[1] < minTroops[1] && 50 * troopsBuilt[1] < round * maxTroopsBuilt[1] && rc.isActionReady())
            build(rc, RobotType.CARRIER);
        if (troopsAlive[2] < minTroops[2] && 50 * troopsBuilt[2] < round * maxTroopsBuilt[2] && rc.isActionReady())
            build(rc, RobotType.LAUNCHER);
        if (troopsAlive[3] < minTroops[3] && 50 * troopsBuilt[3] < round * maxTroopsBuilt[3] && rc.isActionReady())
            build(rc, RobotType.AMPLIFIER);

        if (mana >= 60 && rc.isActionReady())
            build(rc, RobotType.LAUNCHER);
        if (adamantium >= 50 && rc.isActionReady())
            build(rc, RobotType.CARRIER);

    }

    static boolean build(RobotController rc, RobotType type) throws GameActionException {
        for (Direction d : directions) {
            final MapLocation newLoc = curLoc.add(d);
            if (rc.canBuildRobot(type, newLoc)) {
                rc.buildRobot(type, newLoc);
                troopsAlive[typeToIndex(type)]++;
                troopsBuilt[typeToIndex(type)]++;
                return true;
            }
        }
        return false;
    }

    static void addExplorationLoc(RobotController rc) {

        int width = rc.getMapWidth() / scaleX, height = rc.getMapHeight() / scaleY;
        if (ExplorationHeatMap == null)
            ExplorationHeatMap = new int[width][height];

        int slot = -1;
        for (; exploreX < width; exploreX++) {
            for (; exploreY < height; exploreY++) {
                if (ExplorationHeatMap[exploreX][exploreY] == 0) {

                    slot = Communication.reportExploreLoc(rc, new MapLocation(3 * exploreX, 3 * exploreY), true);
                    if (slot != -1)
                        ExplorationHeatMap[exploreX][exploreY] = 1;
                    return;
                }
            }
        }

    }

    public static void checkEnemyActivity(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        Team opponent = rc.getTeam().opponent();
        int[] nearbyEnemyTroops = new int[NUM_TYPES];
        int[] nearbyOwnTroops = new int[NUM_TYPES];
        int nearbyEnemyTroopsCount = 0;
        for (RobotInfo r : nearbyRobots) {
            if (r.getTeam() == opponent) {
                nearbyEnemyTroops[typeToIndex(r.getType())]++;
            } else {
                nearbyOwnTroops[typeToIndex(r.getType())]++;
            }
        }

        if (nearbyEnemyTroopsCount > 0) {
            if (nearbyEnemyTroopsCount >= nearbyOwnTroops[typeToIndex(
                    RobotType.LAUNCHER)]
                    && nearbyEnemyTroopsCount > 0) {

                underAttack = true;
                rc.setIndicatorString("HELP needed at HQ");
                System.out.printf("HELP needed at HQ \n");

                if (rc.getRoundNum() % 2 == 1)
                    Communication.reportReinforcementLoc(rc, curLoc,
                            nearbyEnemyTroopsCount - nearbyOwnTroops[2] / 2);
                build(rc, RobotType.LAUNCHER);
            } else
                underAttack = false;
        }
    }

    private static int typeToIndex(RobotType type) {
        switch (type) {
            case HEADQUARTERS:
                return 0;
            case CARRIER:
                return 1;
            case LAUNCHER:
                return 2;
            case AMPLIFIER:
                return 3;
            case BOOSTER:
                return 4;
            case DESTABILIZER:
                return 5;
            default:
                throw new RuntimeException("Unknown type: " + type);
        }
    }

}
