package nl.ashlyn.core.arrival;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created by Ashlyn on 07/11/2026
 *
 * Finds a walkable path through the world, around trees and walls, for the arrival trail.
 * A path only uses loaded chunks and searches a limited number of blocks, so when the target is too far away
 * it returns the path to the spot closest to the target it could reach. The trail asks again as the player walks.
 * Only touch this from the main thread.
 */
final class ArrivalPath {

    private static final int MAX_NODES = 4000;
    private static final double GREED = 1.6;
    private static final int MAX_DROP = 3;
    private static final double CLIMB_COST = 0.5;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final Set<Material> UNSAFE = Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.LAVA);

    private record Node(int x, int y, int z, double cost, double score, Node parent) {
    }

    private ArrivalPath() {
    }

    /**
     * Find a path from one place to another
     * @return The feet positions to walk along, from the start to the target or as close to it as could be reached
     */
    static List<Vector> find(World world, Location from, Location to) {
        int targetX = to.getBlockX();
        int targetY = to.getBlockY();
        int targetZ = to.getBlockZ();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Long, Double> costs = new HashMap<>();
        Node start = new Node(from.getBlockX(), from.getBlockY(), from.getBlockZ(), 0, 0, null);
        open.add(start);
        costs.put(key(start.x, start.y, start.z), 0.0);

        Node closest = start;
        double closestLeft = left(start.x, start.y, start.z, targetX, targetY, targetZ);
        int searched = 0;

        while (!open.isEmpty() && searched < MAX_NODES) {
            Node node = open.poll();
            if (node.cost > costs.getOrDefault(key(node.x, node.y, node.z), Double.MAX_VALUE)) continue;
            searched++;

            double nodeLeft = left(node.x, node.y, node.z, targetX, targetY, targetZ);
            if (nodeLeft < closestLeft) {
                closest = node;
                closestLeft = nodeLeft;
            }
            if (Math.abs(node.x - targetX) <= 1 && Math.abs(node.z - targetZ) <= 1 && Math.abs(node.y - targetY) <= 2) {
                closest = node;
                break;
            }

            for (int[] direction : DIRECTIONS) {
                int x = node.x + direction[0];
                int z = node.z + direction[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal && (!open(world, node.x + direction[0], node.y, node.z) || !open(world, node.x, node.y, node.z + direction[1]))) {
                    continue;
                }

                int y = step(world, node, x, z);
                if (y == Integer.MIN_VALUE) continue;
                double cost = node.cost + (diagonal ? Math.sqrt(2) : 1) + (y > node.y ? CLIMB_COST : 0);
                long key = key(x, y, z);
                if (cost >= costs.getOrDefault(key, Double.MAX_VALUE)) continue;
                costs.put(key, cost);
                open.add(new Node(x, y, z, cost, cost + GREED * left(x, y, z, targetX, targetY, targetZ), node));
            }
        }

        List<Vector> path = new ArrayList<>();
        for (Node node = closest; node != null; node = node.parent) path.add(new Vector(node.x + 0.5, node.y, node.z + 0.5));
        Collections.reverse(path);
        return path;
    }

    /**
     * Find the height a player ends up at when they walk from a node to the block next to it
     * @return The feet height, or Integer.MIN_VALUE if they can't go there
     */
    private static int step(World world, Node node, int x, int z) {
        if (standable(world, x, node.y, z)) return node.y;
        if (open(world, node.x, node.y + 2, node.z) && standable(world, x, node.y + 1, z)) return node.y + 1;
        if (!open(world, x, node.y, z)) return Integer.MIN_VALUE;
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            if (standable(world, x, node.y - drop, z)) return node.y - drop;
            if (!open(world, x, node.y - drop, z)) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean standable(World world, int x, int y, int z) {
        if (!open(world, x, y, z) || !open(world, x, y + 1, z)) return false;
        Block ground = world.getBlockAt(x, y - 1, z);
        Material type = ground.getType();
        return type.isSolid() && !ground.isLiquid() && !UNSAFE.contains(type)
                && !Tag.FENCES.isTagged(type) && !Tag.WALLS.isTagged(type) && !Tag.FENCE_GATES.isTagged(type);
    }

    private static boolean open(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        return block.isPassable() && !block.isLiquid() && !UNSAFE.contains(block.getType());
    }

    private static double left(int x, int y, int z, int targetX, int targetY, int targetZ) {
        double dx = x - targetX;
        double dz = z - targetZ;
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(y - targetY) * 0.5;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }
}
