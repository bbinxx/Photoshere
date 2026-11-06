package com.bbinxx.photoshere.stitching

import android.graphics.BitmapFactory
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import java.io.File

data class FeatureMatchResult(
    val goodMatches: Int,
    val inliers: Int,
    val inlierRatio: Float,
    val approved: Boolean,
)

/**
 * ORB feature verification for neighbouring frames.
 *
 * Sensor poses are the primary source of camera orientation. Feature matching
 * is used as an overlap/consistency check and as the seed for future pose
 * refinement when a session shows unexpected drift.
 */
class FeatureMatcher(
    private val maxFeatures: Int = 1600,
) {

    fun match(featureA: FeatureSet, featureB: FeatureSet): FeatureMatchResult {
        if (featureA.descriptors.empty() || featureB.descriptors.empty()) {
            return FeatureMatchResult(0, 0, 0f, false)
        }
        val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
        val matches = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(featureA.descriptors, featureB.descriptors, matches, 2)

        val good = mutableListOf<org.opencv.core.DMatch>()
        matches.forEach { pair ->
            val array = pair.toArray()
            if (array.size >= 2) {
                val first = array[0]
                val second = array[1]
                if (first.distance < RATIO * second.distance) good.add(first)
            }
        }
        if (good.isEmpty()) {
            return FeatureMatchResult(0, 0, 0f, false)
        }

        val pointsA = org.opencv.core.MatOfPoint2f()
        val pointsB = org.opencv.core.MatOfPoint2f()
        pointsA.fromList(good.map { featureA.keypoints[it.queryIdx].pt })
        pointsB.fromList(good.map { featureB.keypoints[it.trainIdx].pt })
        var inliers = good.size
        if (good.size >= 8) {
            val mask = Mat()
            val homography = org.opencv.calib3d.Calib3d.findHomography(pointsA, pointsB, org.opencv.calib3d.Calib3d.RANSAC, 3.0, mask)
            try {
                if (!homography.empty()) {
                    var count = 0
                    for (i in 0 until mask.rows()) {
                        if (mask.get(i, 0)?.get(0)?.toInt() == 1) count++
                    }
                    inliers = count
                }
            } finally {
                homography.release()
                mask.release()
            }
        }
        pointsA.release()
        pointsB.release()
        val ratio = inliers.toFloat() / good.size.coerceAtLeast(1)
        return FeatureMatchResult(
            goodMatches = good.size,
            inliers = inliers,
            inlierRatio = ratio,
            approved = inliers >= 12 && ratio >= 0.35f,
        )
    }

    fun extract(file: File): FeatureSet? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val mat = Mat()
        val gray = Mat()
        val keypoints = MatOfKeyPoint()
        val descriptors = Mat()
        return try {
            Utils.bitmapToMat(bitmap, mat)
            org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
            val orb = ORB.create(maxFeatures)
            orb.detectAndCompute(gray, Mat(), keypoints, descriptors)
            FeatureSet(keypoints.toArray().toList(), descriptors.clone())
        } finally {
            bitmap.recycle()
            mat.release()
            gray.release()
            keypoints.release()
        }
    }

    fun matchFiles(first: File, second: File): FeatureMatchResult {
        val a = extract(first) ?: return FeatureMatchResult(0, 0, 0f, false)
        val b = extract(second) ?: return FeatureMatchResult(0, 0, 0f, false)
        return try {
            match(a, b)
        } finally {
            a.descriptors.release()
            b.descriptors.release()
        }
    }

    data class FeatureSet(
        val keypoints: List<org.opencv.core.KeyPoint>,
        val descriptors: Mat,
    )

    private companion object {
        const val RATIO = 0.75f
    }
}
