package com.bbinxx.photoshere.stitching

import com.bbinxx.photoshere.core.model.*
import kotlinx.coroutines.ensureActive
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.acos
import kotlin.math.tan

/** Conservative local visual refinement, not global bundle adjustment.
 * ORB matches -> RANSAC homography in calibrated coordinates -> SVD projection
 * to SO(3). Only small, well-supported rotations close to sensor poses survive.
 * Bearing residuals reject many parallax/planar homographies that are not pure
 * camera rotations. Featureless or inconsistent views retain their sensor pose.
 */
class VisualPoseRefiner {
    data class Geometry(val width: Int, val height: Int, val horizontalFov: Float, val verticalFov: Float) {
        fun normalized(point: Point): Point = Point(
            (point.x - width * 0.5) / (width * 0.5 / tan(Math.toRadians(horizontalFov.toDouble()) * 0.5)),
            (point.y - height * 0.5) / (height * 0.5 / tan(Math.toRadians(verticalFov.toDouble()) * 0.5)),
        )
    }
    data class Rotation(val quaternion: Quaternion, val inliers: Int, val medianErrorDeg: Float)
    data class Result(val images: List<CapturedImageMeta>, val refinedCount: Int, val warnings: List<String>)
    private data class Features(val points: List<KeyPoint>, val descriptors: Mat, val geometry: Geometry)
    private val preprocessor = ImagePreprocessor()

    suspend fun refine(images: List<CapturedImageMeta>, onProgress: (Float) -> Unit = {}): Result {
        if (!runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)) {
            return Result(images, 0, listOf("Visual alignment unavailable; sensor poses were used"))
        }
        val cache = linkedMapOf<Int, Features>()
        fun features(index: Int): Features? {
            cache.remove(index)?.let { cache[index] = it; return it }
            val meta = images[index]
            val bitmap = preprocessor.load(File(meta.filePath), 1024) ?: return null
            val rgba = Mat()
            val gray = Mat()
            val keys = MatOfKeyPoint()
            val descriptors = Mat()
            val mask = Mat()
            val orb = ORB.create(1800)
            try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                orb.detectAndCompute(gray, mask, keys, descriptors)
                val h = meta.horizontalFovDeg.takeIf { it in 20f..150f } ?: 65f
                val v = meta.verticalFovDeg.takeIf { it in 20f..150f } ?: Math.toDegrees(
                    2 * kotlin.math.atan(tan(Math.toRadians(h.toDouble()) / 2) * bitmap.height / bitmap.width),
                ).toFloat()
                val result = Features(keys.toArray().toList(), descriptors.clone(), Geometry(bitmap.width, bitmap.height, h, v))
                if (cache.size >= 5) cache.remove(cache.keys.first())?.descriptors?.release()
                cache[index] = result
                return result
            } finally {
                bitmap.recycle(); rgba.release(); gray.release(); keys.release()
                descriptors.release(); mask.release(); orb.clear()
            }
        }
        val refined = images.toMutableList()
        var count = 0
        var failed = 0
        try {
            for (index in images.indices) {
                coroutineContext.ensureActive()
                if (index == 0) { onProgress(1f / images.size); continue }
                try {
                    val current = features(index)
                    if (current != null && !current.descriptors.empty()) {
                        val originalPose = pose(images[index])
                        val neighbours = (0 until index).sortedBy { forwardDistance(originalPose, pose(images[it])) }
                            .filter { forwardDistance(originalPose, pose(images[it])) < minOf(
                                current.geometry.horizontalFov, images[it].horizontalFovDeg.takeIf { f -> f > 0 } ?: 65f,
                            ) * 0.9f }.take(3)
                        var best: Pair<Quaternion, Float>? = null
                        for (previous in neighbours) {
                            coroutineContext.ensureActive()
                            val reference = features(previous) ?: continue
                            val rotation = match(reference, current) ?: continue
                            val candidate = (pose(refined[previous]) * rotation.quaternion.inverse()).normalized()
                            // Small local corrections only. Large disagreement needs
                            // recalibration/global optimisation, not a forced warp.
                            if (candidate.angularDistanceDegrees(originalPose) > 8f) continue
                            val confidence = rotation.inliers / (0.1f + rotation.medianErrorDeg)
                            if (best == null || confidence > best.second) best = candidate to confidence
                        }
                        best?.let {
                            refined[index] = images[index].copy(quaternion = it.first.toFloatArray().toList())
                            count++
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed++ }
                onProgress((index + 1f) / images.size)
            }
        } finally { cache.values.forEach { it.descriptors.release() } }
        val warnings = mutableListOf<String>()
        if (count < images.size - 1) warnings += "Visual refinement corrected $count of ${images.size - 1} views; remaining views use sensor poses"
        if (failed > 0) warnings += "$failed views could not be visually matched"
        return Result(refined, count, warnings)
    }

    private fun match(a: Features, b: Features): Rotation? {
        if (a.descriptors.empty() || b.descriptors.empty()) return null
        val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
        val pairs = mutableListOf<MatOfDMatch>()
        try {
            matcher.knnMatch(a.descriptors, b.descriptors, pairs, 2)
            val good = pairs.mapNotNull { pair -> pair.toArray().let {
                if (it.size == 2 && it[0].distance < it[1].distance * 0.7f) it[0] else null
            } }.sortedBy { it.distance }.distinctBy { it.trainIdx }
            return estimateRotation(good.map { a.points[it.queryIdx].pt }, good.map { b.points[it.trainIdx].pt }, a.geometry, b.geometry)
        } finally { pairs.forEach { it.release() }; matcher.clear() }
    }

    /** Camera convention is +X right, +Y up, forward -Z. Homography uses +Z. */
    fun estimateRotation(pointsA: List<Point>, pointsB: List<Point>, a: Geometry, b: Geometry): Rotation? {
        if (pointsA.size < 24 || pointsA.size != pointsB.size ||
            (pointsA + pointsB).any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val na = pointsA.map(a::normalized)
        val nb = pointsB.map(b::normalized)
        val source = MatOfPoint2f(*na.toTypedArray())
        val target = MatOfPoint2f(*nb.toTypedArray())
        val mask = Mat()
        val singular = Mat(); val u = Mat(); val vt = Mat(); val rotation = Mat(); val empty = Mat()
        var homography: Mat? = null
        try {
            homography = Calib3d.findHomography(source, target, Calib3d.RANSAC, 0.005, mask, 2000, 0.995)
            if (homography.empty()) return null
            val determinant = Core.determinant(homography)
            if (!determinant.isFinite() || determinant <= 0) return null
            val inlierIndices = na.indices.filter { (mask.get(it, 0)?.firstOrNull() ?: 0.0) != 0.0 }
            if (inlierIndices.size < 24 || inlierIndices.size < na.size * 0.45) return null
            fun spread(points: List<Point>, geometry: Geometry): Boolean {
                val selected = inlierIndices.map { points[it] }
                return selected.maxOf { it.x } - selected.minOf { it.x } > geometry.width * 0.15 &&
                    selected.maxOf { it.y } - selected.minOf { it.y } > geometry.height * 0.15
            }
            if (!spread(pointsA, a) || !spread(pointsB, b)) return null
            Core.SVDecomp(homography, singular, u, vt)
            Core.gemm(u, vt, 1.0, empty, 0.0, rotation)
            val rotationDeterminant = Core.determinant(rotation)
            if (!rotationDeterminant.isFinite() || rotationDeterminant !in 0.99..1.01) return null
            val sign = floatArrayOf(1f, -1f, -1f)
            fun column(c: Int) = Vec3(
                rotation.get(0, c)[0].toFloat() * sign[0] * sign[c],
                rotation.get(1, c)[0].toFloat() * sign[1] * sign[c],
                rotation.get(2, c)[0].toFloat() * sign[2] * sign[c],
            )
            val quaternion = Quaternion.fromBasis(column(0), column(1), column(2))
            val errors = inlierIndices.map { i ->
                val first = Vec3(na[i].x.toFloat(), -na[i].y.toFloat(), -1f).normalized()
                val second = Vec3(nb[i].x.toFloat(), -nb[i].y.toFloat(), -1f).normalized()
                Math.toDegrees(acos(quaternion.rotate(first).dot(second).coerceIn(-1f, 1f).toDouble())).toFloat()
            }.sorted()
            val median = errors[errors.size / 2]
            if (!median.isFinite() || median > 0.75f || errors[(errors.size * 0.9).toInt().coerceAtMost(errors.lastIndex)] > 1.5f) return null
            return Rotation(quaternion, inlierIndices.size, median)
        } finally {
            source.release(); target.release(); mask.release(); homography?.release()
            singular.release(); u.release(); vt.release(); rotation.release(); empty.release()
        }
    }

    private fun pose(image: CapturedImageMeta) = Quaternion.fromFloatArray(image.quaternion.toFloatArray())
    private fun forwardDistance(a: Quaternion, b: Quaternion): Float = Math.toDegrees(acos(
        a.rotate(Vec3.FORWARD).dot(b.rotate(Vec3.FORWARD)).coerceIn(-1f, 1f).toDouble(),
    )).toFloat()
}
