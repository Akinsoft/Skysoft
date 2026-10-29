// SPDX-License-Identifier: LGPL-2.1-only
// Adapted from SkyHanni; see credits.md for attribution and source details.

package com.skysoft.utils.particle

import com.skysoft.utils.WorldVec
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class ParticlePathEstimator(
    private val minPoints: Int = DEFAULT_MIN_POINTS,
    private val maxPointGap: Double = DEFAULT_MAX_POINT_GAP,
) {
    private val points = mutableListOf<WorldVec>()

    init {
        require(minPoints >= COEFFICIENT_COUNT) { "Particle path estimation needs at least $COEFFICIENT_COUNT points." }
        require(maxPointGap > 0.0) { "Particle path point gap must be positive." }
    }

    fun reset() {
        points.clear()
    }

    fun count(): Int = points.size

    fun addIfUsable(location: WorldVec): ParticlePathPointResult {
        if (!location.isFinite()) return ParticlePathPointResult.IGNORED
        val last = points.lastOrNull()
        if (last != null) {
            val distance = last.distance(location)
            if (distance == 0.0) return ParticlePathPointResult.IGNORED
            if (distance > maxPointGap) {
                points.clear()
            }
        }
        points += location
        return ParticlePathPointResult.ADDED
    }

    fun estimate(): WorldVec? {
        if (points.size < minPoints) return null
        val curve = fitCubicCurve(points) ?: return null
        val derivative = curve.derivativeAtStart()
        val derivativeLength = derivative.length()
        if (derivativeLength <= MIN_DERIVATIVE_LENGTH) return null

        val t = PROJECTION_SCALE * pitchWeight(derivative) / derivativeLength
        val estimate = curve.at(t)
        if (!estimate.isFinite()) return null
        if (estimate.distance(points.first()) > MAX_ESTIMATE_DISTANCE) return null
        return estimate
    }

    private fun fitCubicCurve(points: List<WorldVec>): CubicCurve? {
        val x = fitDimension(points.map { it.x }) ?: return null
        val y = fitDimension(points.map { it.y }) ?: return null
        val z = fitDimension(points.map { it.z }) ?: return null
        return CubicCurve(x, y, z)
    }

    private fun fitDimension(values: List<Double>): DoubleArray? {
        val matrix = Array(COEFFICIENT_COUNT) { DoubleArray(COEFFICIENT_COUNT) }
        val vector = DoubleArray(COEFFICIENT_COUNT)

        values.forEachIndexed { index, value ->
            val t = index.toDouble()
            val powers = DoubleArray(COEFFICIENT_COUNT) { power -> t.pow(power) }
            for (row in 0 until COEFFICIENT_COUNT) {
                vector[row] += value * powers[row]
                for (column in 0 until COEFFICIENT_COUNT) {
                    matrix[row][column] += powers[row] * powers[column]
                }
            }
        }

        return solve(matrix, vector)
    }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray? {
        val size = vector.size
        val augmented = Array(size) { row -> matrix[row].copyOf(size + 1).also { it[size] = vector[row] } }

        for (pivotColumn in 0 until size) {
            val pivotRow = (pivotColumn until size).maxBy { row -> abs(augmented[row][pivotColumn]) }
            if (abs(augmented[pivotRow][pivotColumn]) < MIN_PIVOT) return null
            augmented.swapRows(pivotColumn, pivotRow)
            val pivot = augmented[pivotColumn][pivotColumn]

            for (column in pivotColumn until size + 1) {
                augmented[pivotColumn][column] /= pivot
            }
            for (row in 0 until size) {
                if (row == pivotColumn) continue
                val factor = augmented[row][pivotColumn]
                for (column in pivotColumn until size + 1) {
                    augmented[row][column] -= factor * augmented[pivotColumn][column]
                }
            }
        }

        return DoubleArray(size) { row -> augmented[row][size] }
    }

    private fun Array<DoubleArray>.swapRows(first: Int, second: Int) {
        if (first == second) return
        val temp = this[first]
        this[first] = this[second]
        this[second] = temp
    }

    private fun pitchWeight(derivative: WorldVec): Double {
        val horizontalLength = hypot(derivative.x, derivative.z)
        val observedPitch = -atan2(derivative.y, horizontalLength)
        val correctedPitch = observedPitch + asin(PITCH_OFFSET * cos(observedPitch))
        return sqrt(PITCH_WEIGHT_BASE - PITCH_WEIGHT_SIN_SCALE * sin(correctedPitch))
    }

    private class CubicCurve(
        private val x: DoubleArray,
        private val y: DoubleArray,
        private val z: DoubleArray,
    ) {
        fun derivativeAtStart(): WorldVec = WorldVec(x[1], y[1], z[1])

        fun at(t: Double): WorldVec = WorldVec(evaluate(x, t), evaluate(y, t), evaluate(z, t))

        private fun evaluate(coefficients: DoubleArray, t: Double): Double =
            coefficients[CONSTANT_TERM_INDEX] +
                coefficients[LINEAR_TERM_INDEX] * t +
                coefficients[QUADRATIC_TERM_INDEX] * t * t +
                coefficients[CUBIC_TERM_INDEX] * t * t * t
    }

    private companion object {
        const val DEFAULT_MIN_POINTS = 6
        const val DEFAULT_MAX_POINT_GAP = 3.0
        const val COEFFICIENT_COUNT = 4
        const val PROJECTION_SCALE = 3.0
        const val PITCH_OFFSET = 0.75
        const val PITCH_WEIGHT_SIN_SCALE = 24.0
        const val PITCH_WEIGHT_BASE = 25.0
        const val CONSTANT_TERM_INDEX = 0
        const val LINEAR_TERM_INDEX = 1
        const val QUADRATIC_TERM_INDEX = 2
        const val CUBIC_TERM_INDEX = 3
        const val MIN_PIVOT = 1.0E-9
        const val MIN_DERIVATIVE_LENGTH = 1.0E-6
        const val MAX_ESTIMATE_DISTANCE = 500.0
    }
}

internal enum class ParticlePathPointResult {
    ADDED,
    IGNORED,
}
