package com.skysoft.features.waypoints.codec;

import java.math.BigInteger;
import java.util.Arrays;

final class WaypointerEntropy {
    private WaypointerEntropy() {}

    static int[][] read(WaypointBytes input, boolean delta) {
        int count = input.count(20000);
        int[][] points = new int[count][3];
        if (count == 0) return points;
        for (int axis = 0; axis < 3; axis++) points[0][axis] = WaypointerCoordinates.coordinate(input.signed(32));
        if (count == 1) return points;
        if (delta) {
            for (int i = 1; i < count; i++) for (int axis = 0; axis < 3; axis++) {
                points[i][axis] = WaypointerCoordinates.coordinate(points[i - 1][axis] + input.signed(32));
            }
            return points;
        }
        WaypointBytes.Bits bits = new WaypointBytes.Bits(input, true);
        int token = (int) bits.read(2);
        int[][] tuples = {{4, 0, 4}, {4, 3, 4}, {3, 2, 3}};
        int[] parameters = new int[3];
        boolean quotient = false;
        if (token < 3) parameters = tuples[token];
        else {
            parameters[0] = parameter(bits, true);
            quotient = parameters[0] == -1;
            if (quotient) {
                WaypointBytes.require(count <= 1024, "Waypointer quotient route exceeds its point limit.");
                parameters[0] = parameter(bits, false);
            }
            parameters[1] = parameter(bits, false);
            parameters[2] = parameter(bits, false);
        }
        int[] budget = {90 * (count - 1) + (quotient ? 128 : 0)};
        for (int axis = 0; axis < 3; axis++) {
            int k = parameters[axis];
            long[] values;
            if (k == 31) values = new long[count - 1];
            else if (quotient) {
                WaypointBytes.require(k <= 28, "Invalid Waypointer quotient parameter.");
                values = quotient(bits, count - 1, k, budget);
            } else {
                values = new long[count - 1];
                for (int i = 0; i < values.length; i++) values[i] = ((long) unary(bits, budget) << k) | bits.read(k);
            }
            for (int i = 1; i < count; i++) {
                WaypointBytes.require(values[i - 1] <= 536870910L, "Waypointer delta exceeds its range.");
                points[i][axis] = WaypointerCoordinates.coordinate(points[i - 1][axis] + WaypointerCoordinates.unzigzag(values[i - 1]));
            }
            WaypointBytes.require(k == optimal(values, quotient), "Noncanonical Waypointer entropy parameter.");
        }
        if (!quotient && token == 3) for (int[] tuple : tuples) {
            WaypointBytes.require(!Arrays.equals(parameters, tuple), "Noncanonical Waypointer entropy descriptor.");
        }
        bits.end();
        return points;
    }

    private static int parameter(WaypointBytes.Bits bits, boolean marker) {
        int value = (int) bits.read(3);
        if (value != 7) return value;
        value = (int) bits.read(5);
        if (value == 1 && marker) return -1;
        WaypointBytes.require(value >= 7, "Reserved Waypointer entropy descriptor.");
        return value;
    }

    private static int unary(WaypointBytes.Bits bits, int[] budget) {
        int count = 0;
        while (bits.read(1) == 0) {
            WaypointBytes.require(--budget[0] >= 0, "Waypointer unary data exceeds its work limit.");
            count++;
        }
        return count;
    }

    private static long[] quotient(WaypointBytes.Bits bits, int count, int k, int[] budget) {
        boolean majority = bits.read(1) != 0;
        int zeros = 0;
        while (bits.read(1) == 0) WaypointBytes.require(++zeros <= 10, "Waypointer cardinality exceeds its limit.");
        int cardinality = 1;
        for (int i = 0; i < zeros; i++) cardinality = (cardinality << 1) | (int) bits.read(1);
        cardinality--;
        WaypointBytes.require(cardinality <= count / 2 && !(majority && cardinality * 2 == count), "Invalid Waypointer minority count.");
        BigInteger combinations = choose(count, cardinality);
        int width = combinations.subtract(BigInteger.ONE).bitLength();
        BigInteger rank = BigInteger.ZERO;
        for (int i = 0; i < width; i++) rank = rank.shiftLeft(1).or(BigInteger.valueOf(bits.read(1)));
        WaypointBytes.require(rank.compareTo(combinations) < 0, "Invalid Waypointer quotient rank.");
        boolean[] minority = unrank(rank, count, cardinality);
        long[] values = new long[count];
        for (int i = 0; i < count; i++) values[i] = bits.read(k);
        for (int i = 0; i < count; i++) if (majority != minority[i]) {
            values[i] |= ((long) unary(bits, budget) + 1) << k;
        }
        return values;
    }

    private static BigInteger choose(int n, int k) {
        if (k > n) return BigInteger.ZERO;
        k = Math.min(k, n - k);
        BigInteger value = BigInteger.ONE;
        for (int i = 1; i <= k; i++) value = value.multiply(BigInteger.valueOf(n - k + i)).divide(BigInteger.valueOf(i));
        return value;
    }

    private static int optimal(long[] values, boolean quotient) {
        if (Arrays.stream(values).allMatch(value -> value == 0)) return 31;
        int best = 0;
        long bestCost = Long.MAX_VALUE;
        for (int k = 0; k <= (quotient ? 28 : 30); k++) {
            long sum = 0;
            int present = 0;
            for (long value : values) {
                long q = value >>> k;
                sum += q;
                if (q != 0) present++;
            }
            long cost = sum + (long) values.length * k;
            if (quotient) {
                int minority = Math.min(present, values.length - present);
                int gammaWidth = 32 - Integer.numberOfLeadingZeros(minority + 1);
                cost += 1L + gammaWidth * 2L - 1 + choose(values.length, minority).subtract(BigInteger.ONE).bitLength();
            } else cost += values.length;
            if (cost < bestCost) { best = k; bestCost = cost; }
        }
        return best;
    }

    private static boolean[] unrank(BigInteger rank, int count, int cardinality) {
        boolean[] values = new boolean[count];
        int position = count - 1;
        BigInteger current = choose(position, cardinality);
        for (int k = cardinality; k > 0; k--) {
            while (current.compareTo(rank) > 0) {
                current = current.multiply(BigInteger.valueOf(position - k)).divide(BigInteger.valueOf(position));
                position--;
            }
            values[position] = true;
            rank = rank.subtract(current);
            current = position == 0 ? BigInteger.ZERO : current.multiply(BigInteger.valueOf(k)).divide(BigInteger.valueOf(position));
            position--;
        }
        return values;
    }
}
