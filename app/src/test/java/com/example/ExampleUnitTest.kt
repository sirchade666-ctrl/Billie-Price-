package com.example

import com.example.data.model.EmfReading
import com.example.data.model.PowerSourceFilterMode
import com.example.engine.EmfSensorEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Local unit tests for Geoport EMF signal processing and power-source EMF rejection.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun emfPowerSourceFilter_rejectsSteadyWiringAndAcRipple_passesTransientSpikes() {
    val engine = EmfSensorEngine(context = null)
    var t = 1_700_000_000_000L

    // 1. Calibrate Earth baseline at 45.0 μT (15 samples)
    repeat(15) {
      engine.processMagneticSample(45.0f, 0f, 0f, t)
      t += 100L
    }

    // 2. Approach steady wall wiring / appliance field (+16 μT constant = 61.0 μT) for 3.0s
    var steadyReading = EmfReading()
    repeat(30) {
      steadyReading = engine.processMagneticSample(61.0f, 0f, 0f, t)
      t += 100L
    }
    assertTrue("Expected steady wiring to be flagged as filtered", steadyReading.isPowerSourceFiltered)
    assertEquals("Wiring / Appliance", steadyReading.powerSourceLabel)
    assertTrue(
      "Expected powerSourceOffsetUt > 12 μT, got ${steadyReading.powerSourceOffsetUt}",
      steadyReading.powerSourceOffsetUt > 12f
    )
    assertTrue(
      "Expected steady wiring delta filtered below 3.0 μT, got ${steadyReading.delta}",
      abs(steadyReading.delta) < 3.0f
    )

    // 3. Walk away from power source back to 45.0 μT — verify no false negative rebound alert
    var walkAwayReading = EmfReading()
    repeat(8) {
      walkAwayReading = engine.processMagneticSample(45.0f, 0f, 0f, t)
      assertTrue(
        "Walking away from power source should not create a false rebound alert, got ${walkAwayReading.delta}",
        abs(walkAwayReading.delta) < 3.5f
      )
      t += 100L
    }
    assertEquals(0f, walkAwayReading.powerSourceOffsetUt, 0.25f)

    // 4. Sudden transient +18 μT anomaly spike (63.0 μT for 3 samples) should pass through
    var spikeReading = EmfReading()
    repeat(3) {
      spikeReading = engine.processMagneticSample(63.0f, 0f, 0f, t)
      t += 100L
    }
    assertTrue(
      "Expected sudden transient spike > 12 μT to pass through, got ${spikeReading.delta}",
      spikeReading.delta > 12.0f
    )

    // Return briefly to baseline
    repeat(5) {
      engine.processMagneticSample(45.0f, 0f, 0f, t)
      t += 100L
    }

    // 5. Periodic alternating AC mains ripple (45 ± 8 μT) should be detected and suppressed
    var rippleReading = EmfReading()
    for (i in 0 until 16) {
      val mag = if (i % 2 == 0) 53.0f else 37.0f
      rippleReading = engine.processMagneticSample(mag, 0f, 0f, t)
      t += 60L
    }
    assertTrue("Expected AC mains ripple to be filtered", rippleReading.isPowerSourceFiltered)
    assertEquals("AC Mains Ripple", rippleReading.powerSourceLabel)
    assertTrue(
      "Expected AC mains ripple delta suppressed below 3.0 μT, got ${rippleReading.delta}",
      abs(rippleReading.delta) < 3.0f
    )

    // 6. Strict mode rejects steady wiring even faster (< 1.8s)
    engine.calibrateBaseline()
    engine.setPowerSourceFilterMode(PowerSourceFilterMode.STRICT)
    repeat(15) {
      engine.processMagneticSample(45.0f, 0f, 0f, t)
      t += 100L
    }
    var strictReading = EmfReading()
    repeat(18) {
      strictReading = engine.processMagneticSample(61.0f, 0f, 0f, t)
      t += 100L
    }
    assertTrue("Expected Strict mode to filter steady wiring quickly", strictReading.isPowerSourceFiltered)
    assertTrue(
      "Expected Strict mode delta < 3.0 μT, got ${strictReading.delta}",
      abs(strictReading.delta) < 3.0f
    )

    // 7. OFF mode passes raw +16 μT reading untouched
    engine.setPowerSourceFilterMode(PowerSourceFilterMode.OFF)
    val rawReading = engine.processMagneticSample(61.0f, 0f, 0f, t)
    assertFalse("Expected OFF mode not to filter", rawReading.isPowerSourceFiltered)
    assertEquals(16.0f, rawReading.delta, 0.1f)
  }

  @Test
  fun appUpdateEngine_comparesSemver_resolvesGitHubUrls_andParsesReleaseJson() {
    // Semver checks
    assertTrue(com.example.engine.AppUpdateEngine.isNewerVersion("2.0", "v2.1"))
    assertTrue(com.example.engine.AppUpdateEngine.isNewerVersion("2.0", "2.0.1"))
    assertTrue(com.example.engine.AppUpdateEngine.isNewerVersion("2.9", "v3.0-beta"))
    assertFalse(com.example.engine.AppUpdateEngine.isNewerVersion("2.0", "2.0"))
    assertFalse(com.example.engine.AppUpdateEngine.isNewerVersion("2.1", "v2.0.9"))

    // GitHub slug & URL resolution
    assertEquals(
      "https://api.github.com/repos/Sirchade/geoport-emf/releases/latest",
      com.example.engine.AppUpdateEngine.resolveGitHubLatestReleaseApiUrl("Sirchade/geoport-emf")
    )
    assertEquals(
      "https://api.github.com/repos/Sirchade/geoport-emf/releases/latest",
      com.example.engine.AppUpdateEngine.resolveGitHubLatestReleaseApiUrl("https://github.com/Sirchade/geoport-emf")
    )
    assertEquals(
      "https://github.com/Sirchade/geoport-emf/releases/latest",
      com.example.engine.AppUpdateEngine.resolveExternalPageUrl("Sirchade/geoport-emf", "com.aistudio.geoportemf.xqrkmn")
    )
    assertEquals(
      "https://play.google.com/store/apps/details?id=com.aistudio.geoportemf.xqrkmn",
      com.example.engine.AppUpdateEngine.resolveExternalPageUrl("", "com.aistudio.geoportemf.xqrkmn")
    )

    // Release JSON parsing
    val sampleJson = """
      {
        "tag_name": "v2.2",
        "name": "Geoport EMF 2.2 - SLS Optics",
        "body": "Added SLS Camera mode and improved EMF power-source filtering.",
        "html_url": "https://github.com/Sirchade/geoport-emf/releases/tag/v2.2",
        "published_at": "2026-11-23T18:00:00Z"
      }
    """.trimIndent()
    val parsed = com.example.engine.AppUpdateEngine.parseReleaseJson(sampleJson, "https://example.com")
    assertNotNull(parsed)
    assertEquals("2.2", parsed!!.versionName)
    assertEquals("Geoport EMF 2.2 - SLS Optics", parsed.releaseTitle)
    assertEquals("2026-11-23", parsed.publishedAt)
    assertTrue(parsed.downloadPageUrl.contains("/releases/tag/v2.2"))
  }

  @Test
  fun slsPoseAnalyzer_normalizesMirrorsRotatesAndValidatesSkeletonTopology() {
    val (rx, ry) = com.example.engine.SlsPoseAnalyzer.normalizeLandmarkCoordinate(
      px = 250f,
      py = 500f,
      imageWidth = 1000,
      imageHeight = 1000,
      mirrorX = false
    )
    assertEquals(0.25f, rx, 0.001f)
    assertEquals(0.50f, ry, 0.001f)

    val (mx, my) = com.example.engine.SlsPoseAnalyzer.normalizeLandmarkCoordinate(
      px = 250f,
      py = 500f,
      imageWidth = 1000,
      imageHeight = 1000,
      mirrorX = true
    )
    assertEquals(0.75f, mx, 0.001f)
    assertEquals(0.50f, my, 0.001f)

    val (r90x, r90y) = com.example.engine.SlsPoseAnalyzer.rotateNormalizedPoint(0.2f, 0.3f, 90)
    assertEquals(0.7f, r90x, 0.001f)
    assertEquals(0.2f, r90y, 0.001f)

    // Verify FILL_CENTER viewport projection preserves aspect ratio & center-crops accurately
    // Camera buffer: 480x640 (3:4 portrait) inside a 400x400 square viewport -> scale = 400/480 = 0.8333, scaledH = 533.33, offsetY = -66.67
    val (vxCenter, vyCenter) = com.example.engine.SlsPoseAnalyzer.mapNormalizedToFillCenterViewport(
      normX = 0.5f,
      normY = 0.5f,
      sourceWidth = 480,
      sourceHeight = 640,
      viewWidth = 400f,
      viewHeight = 400f
    )
    assertEquals(200f, vxCenter, 0.1f)
    assertEquals(200f, vyCenter, 0.1f)

    val (vxTop, vyTop) = com.example.engine.SlsPoseAnalyzer.mapNormalizedToFillCenterViewport(
      normX = 0.5f,
      normY = 0.0f,
      sourceWidth = 480,
      sourceHeight = 640,
      viewWidth = 400f,
      viewHeight = 400f
    )
    assertEquals(200f, vxTop, 0.1f)
    assertTrue("Top edge should center-crop above y=0, got $vyTop", vyTop < -60f)

    assertEquals(24, com.example.data.model.SlsSkeletonTopology.boneConnections.size)

    val prevSkeleton = com.example.data.model.SlsSkeleton(
      joints = mapOf(
        com.example.data.model.SlsJointType.NOSE to com.example.data.model.SlsJoint(
          com.example.data.model.SlsJointType.NOSE, 0.40f, 0.20f, 0.90f
        ),
        com.example.data.model.SlsJointType.LEFT_SHOULDER to com.example.data.model.SlsJoint(
          com.example.data.model.SlsJointType.LEFT_SHOULDER, 0.30f, 0.35f, 0.85f
        ),
        com.example.data.model.SlsJointType.RIGHT_SHOULDER to com.example.data.model.SlsJoint(
          com.example.data.model.SlsJointType.RIGHT_SHOULDER, 0.50f, 0.35f, 0.85f
        ),
        com.example.data.model.SlsJointType.LEFT_HIP to com.example.data.model.SlsJoint(
          com.example.data.model.SlsJointType.LEFT_HIP, 0.33f, 0.65f, 0.80f
        )
      ),
      avgConfidence = 0.85f
    )
    val nextSkeleton = prevSkeleton.copy(
      joints = prevSkeleton.joints + (
        com.example.data.model.SlsJointType.NOSE to com.example.data.model.SlsJoint(
          com.example.data.model.SlsJointType.NOSE, 0.60f, 0.20f, 0.90f
        )
      )
    )
    val smoothed = com.example.engine.SlsPoseAnalyzer.smoothSkeleton(prevSkeleton, nextSkeleton, alpha = 0.50f)
    assertTrue(smoothed.isDetected)
    assertEquals(4, smoothed.jointCount)
    assertEquals(0.50f, smoothed.joints[com.example.data.model.SlsJointType.NOSE]!!.normX, 0.001f)
  }
}
