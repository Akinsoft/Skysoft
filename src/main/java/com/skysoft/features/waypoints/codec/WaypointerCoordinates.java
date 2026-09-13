package com.skysoft.features.waypoints.codec;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class WaypointerCoordinates {
    private WaypointerCoordinates() {}

    static int[][] read(WaypointBytes input, int count, int mode, boolean trained) {
        WaypointBytes.require(count >= 0 && count <= 20000, "Too many Waypointer points.");
        if (mode == 7) {
            WaypointBytes section = input.section(2_097_152);
            int[][] values = WaypointerEntropy.read(section, false);
            section.end();
            WaypointBytes.require(values.length == count, "Waypointer coordinate count does not match the group.");
            return values;
        }
        int[][] points = new int[count][3];
        if (mode == 0 || mode == 1 || mode == 4) {
            if (mode == 4) {
                if (count > 0) for (int axis = 0; axis < 3; axis++) points[0][axis] = coordinate(input.signed(32));
                for (int axis = 0; axis < 3; axis++) for (int i = 1; i < count; i++) {
                    points[i][axis] = coordinate(points[i - 1][axis] + input.signed(32));
                }
            } else for (int i = 0; i < count; i++) for (int axis = 0; axis < 3; axis++) {
                points[i][axis] = coordinate(input.signed(32) + (mode == 0 && i > 0 ? points[i - 1][axis] : 0));
            }
        } else if (mode == 2) {
            WaypointBytes.Bits bits = new WaypointBytes.Bits(input, false);
            for (int[] point : points) {
                point[0] = coordinate(unzigzag(bits.read(12)));
                point[1] = (int) bits.read(9) - 64;
                point[2] = coordinate(unzigzag(bits.read(12)));
            }
            bits.end();
        } else if (mode == 3) {
            int[] origin = new int[3];
            for (int axis = 0; axis < 3; axis++) origin[axis] = coordinate(input.signed(32));
            int[] widths = widths(input);
            WaypointBytes.Bits bits = new WaypointBytes.Bits(input, false);
            for (int[] point : points) for (int axis = 0; axis < 3; axis++) {
                point[axis] = coordinate(origin[axis] + bits.read(widths[axis]));
            }
            bits.end();
        } else {
            WaypointBytes.require(mode == 5 || mode == 6, "Unsupported Waypointer coordinate mode.");
            if (count > 0) for (int axis = 0; axis < 3; axis++) points[0][axis] = coordinate(input.signed(32));
            int[] widths = widths(input);
            if (mode == 6) range(input, points, widths, trained);
            else {
                WaypointBytes.Bits bits = new WaypointBytes.Bits(input, false);
                for (int axis = 0; axis < 3; axis++) for (int i = 1; i < count; i++) {
                    points[i][axis] = coordinate(points[i - 1][axis] + unzigzag(bits.read(widths[axis])));
                }
                bits.end();
            }
        }
        return points;
    }

    private static int[] widths(WaypointBytes input) {
        int word = input.u16();
        WaypointBytes.require((word & 0x8000) == 0, "Reserved Waypointer width bit is set.");
        return new int[]{(word >>> 10) & 31, (word >>> 5) & 31, word & 31};
    }

    private static void range(WaypointBytes input, int[][] points, int[] widths, boolean trained) {
        int limit = Math.max(16, (points.length - 1) * Arrays.stream(widths).sum() + 16);
        byte[] bytes = input.bytes(input.count(Math.min(2_097_152, limit)));
        Range decoder = new Range(bytes, trained);
        for (int axis = 0; axis < 3; axis++) for (int i = 1; i < points.length; i++) {
            long value = 0;
            for (int bit = widths[axis] - 1; bit >= 0; bit--) value = (value << 1) | decoder.bit(axis * 31 + bit);
            points[i][axis] = coordinate(points[i - 1][axis] + unzigzag(value));
        }
        if (trained) {
            RangeEncoder encoder = new RangeEncoder();
            for (int axis = 0; axis < 3; axis++) {
                long maximum = 0;
                for (int i = 1; i < points.length; i++) {
                    long delta = (long) points[i][axis] - points[i - 1][axis];
                    long value = delta >= 0 ? delta * 2 : -delta * 2 - 1;
                    maximum = Math.max(maximum, value);
                    for (int bit = widths[axis] - 1; bit >= 0; bit--) encoder.bit(axis * 31 + bit, (int) ((value >>> bit) & 1));
                }
                WaypointBytes.require(widths[axis] == 64 - Long.numberOfLeadingZeros(maximum), "Noncanonical Waypointer coordinate width.");
            }
            WaypointBytes.require(Arrays.equals(bytes, encoder.finish()), "Noncanonical or truncated Waypointer range data.");
        }
    }

    static long unzigzag(long value) { return (value >>> 1) ^ -(value & 1); }

    static int coordinate(long value) {
        WaypointBytes.require(value >= -134217728 && value <= 134217727, "Waypointer coordinate is outside its range.");
        return (int) value;
    }

    private static int[] model(boolean trained) {
        int[] probabilities = new int[93];
        Arrays.fill(probabilities, 2048);
        if (trained) {
            int[][] priors = {
                {2145, 2212, 2034, 2177, 3087, 3683, 3946, 3983, 4058, 4053, 4001, 3810},
                {2336, 2341, 2312, 2554, 3471, 3885, 4015, 3734},
                {2163, 2131, 1986, 2119, 3053, 3701, 3949, 3992, 4057, 4087}
            };
            for (int axis = 0; axis < 3; axis++) System.arraycopy(priors[axis], 0, probabilities, axis * 31, priors[axis].length);
        }
        return probabilities;
    }

    private static final class Range {
        private final byte[] bytes;
        private final int[] probabilities;
        private int index;
        private long low;
        private long range = 0xffffffffL;
        private long code;

        Range(byte[] bytes, boolean trained) {
            this.bytes = bytes;
            probabilities = model(trained);
            for (int i = 0; i < 4; i++) code = (code << 8) | next();
        }

        int bit(int context) {
            long bound = (range >>> 12) * probabilities[context];
            int bit = ((code - low) & 0xffffffffL) < bound ? 0 : 1;
            if (bit == 0) {
                range = bound;
                probabilities[context] += (4096 - probabilities[context]) >>> 4;
            } else {
                low = (low + bound) & 0xffffffffL;
                range = (range - bound) & 0xffffffffL;
                probabilities[context] -= probabilities[context] >>> 4;
            }
            while ((low ^ (low + range)) < (1L << 24) || range < (1L << 16)) {
                if ((low ^ (low + range)) >= (1L << 24)) range = (-low) & 65535;
                low = (low << 8) & 0xffffffffL;
                range = (range << 8) & 0xffffffffL;
                code = ((code << 8) | next()) & 0xffffffffL;
            }
            return bit;
        }

        private int next() { return index < bytes.length ? bytes[index++] & 255 : 0; }
    }

    private static final class RangeEncoder {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int[] probabilities = model(true);
        private long low;
        private long range = 0xffffffffL;

        void bit(int context, int bit) {
            long bound = (range >>> 12) * probabilities[context];
            if (bit == 0) {
                range = bound;
                probabilities[context] += (4096 - probabilities[context]) >>> 4;
            } else {
                low = (low + bound) & 0xffffffffL;
                range = (range - bound) & 0xffffffffL;
                probabilities[context] -= probabilities[context] >>> 4;
            }
            while ((low ^ (low + range)) < (1L << 24) || range < (1L << 16)) {
                if ((low ^ (low + range)) >= (1L << 24)) range = (-low) & 65535;
                output.write((int) (low >>> 24) & 255);
                low = (low << 8) & 0xffffffffL;
                range = (range << 8) & 0xffffffffL;
            }
        }

        byte[] finish() {
            for (int count = 0; count <= 4; count++) {
                long unit = 1L << (8 * (4 - count));
                long candidate = ((low + unit - 1) / unit) * unit;
                if (candidate > 0xffffffffL || ((candidate - low) & 0xffffffffL) >= range) continue;
                for (int i = 0; i < count; i++) output.write((int) (candidate >>> (24 - i * 8)) & 255);
                return output.toByteArray();
            }
            throw new IllegalArgumentException("Invalid Waypointer range interval.");
        }
    }
}
