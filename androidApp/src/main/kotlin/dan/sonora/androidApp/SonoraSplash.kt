package dan.sonora.androidApp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val SplashBackground = Color(0xFF0F121E)
private val AccentBlue = Color(0xFF3993F3)

/**
 * Must stay in sync with `splash_vinyl.xml`, the system splash icon. The system
 * scales that icon into a fixed 288dp box, so its viewport is padded by `288 / 136`
 * to land the vinyl at this size. Changing this without changing that viewport makes
 * the logo jump when Compose takes over from the system splash.
 */
private val LogoWidth = 136.dp

/**
 * Bounds of the mark itself inside Sonora.svg's 1024x1024 icon viewport, excluding
 * the rounded-square icon padding. Cropping to these is what keeps the logo from
 * reading as a launcher icon floating in the middle of the screen.
 */
private const val MARK_LEFT = 191f
private const val MARK_TOP = 198f
private const val MARK_RIGHT = 951f
private const val MARK_BOTTOM = 848f
private const val MARK_W = MARK_RIGHT - MARK_LEFT
private const val MARK_H = MARK_BOTTOM - MARK_TOP

private const val REVEAL_MS = 400
private const val FADE_MS = 120

/**
 * Wide enough to cover the waveform ribbon (~48 units at its widest) without
 * reaching the neighbouring pass, which closes to ~47 units near the trough.
 * Tune this first if the reveal either clips the ribbon edges or lights up a
 * disconnected segment early.
 */
private const val SPINE_STROKE = 48f

/**
 * Length of the soft gradient at the reveal front, as a fraction of the spine. Short
 * enough that the front stays a legible edge rather than a wash over the whole path.
 */
private const val LEAD_FRACTION = 0.09f
private const val LEAD_SLICES = 14
private const val HIGHLIGHT_ALPHA = 0.3f

/**
 * Ease-out that still moves throughout: the front covers ~25% in the first fifth and
 * ~8% in the last, so it reads as one continuous stroke that settles at the tip.
 * A sharper curve (a steep p1y) finishes most of the path within a few frames and
 * reads as the waveform appearing rather than being drawn.
 */
private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0.35f, 1f)
@Composable
fun SonoraSplash(onFinished: () -> Unit) {
	val reveal = remember { Animatable(0f) }
	val fade = remember { Animatable(1f) }

	LaunchedEffect(Unit) {
		reveal.animateTo(1f, tween(REVEAL_MS, easing = RevealEasing))
		fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
		onFinished()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.graphicsLayer { alpha = fade.value }
			.background(SplashBackground)
			.pointerInput(Unit) {
				awaitPointerEventScope {
					while (true) {
						awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
					}
				}
			},
		contentAlignment = Alignment.Center
	) {
		SonoraLogo(
			revealProgress = reveal.value,
			modifier = Modifier
				.width(LogoWidth)
				.aspectRatio(MARK_W / MARK_H)
		)
	}
}

@Composable
private fun SonoraLogo(revealProgress: Float, modifier: Modifier = Modifier) {
	val logo = remember { LogoGeometry() }

	Canvas(modifier) {
		val scale = size.width / MARK_W
		withTransform({
			translate(-MARK_LEFT * scale, -MARK_TOP * scale)
			scale(scale, scale, Offset.Zero)
		}) {
			drawPath(logo.ring, SolidColor(AccentBlue))
			drawPath(logo.disc, logo.discBrush)
			drawPath(logo.hole, SolidColor(SplashBackground))
			drawPath(logo.arcOuterLeft, logo.arcOuterLeftBrush)
			drawPath(logo.arcRight, SolidColor(AccentBlue))
			drawPath(logo.arcInnerLeft, logo.arcInnerLeftBrush)
			drawWaveform(logo, revealProgress)
		}
	}
}

/**
 * Reveals the waveform by clipping it to a progressively longer segment of its own
 * centreline, so the visible front travels along the ribbon from the point it exits
 * the vinyl out to the tip — a drawing motion rather than a wipe.
 */
private fun DrawScope.drawWaveform(logo: LogoGeometry, progress: Float) {
	if (progress >= 1f) {
		drawPath(logo.waveform, logo.waveformBrush)
		return
	}
	if (progress <= 0f) return

	val front = progress * logo.spineLength
	clipPath(logo.outlineOf(0f, front, logo.revealPath, logo.revealOutline)) {
		drawPath(logo.waveform, logo.waveformBrush)
	}

	val strength = leadingEdgeStrength(progress)
	if (strength <= 0f) return

	// Brighten the front of the reveal so the eye follows it, fading back over a
	// short run of slices so the edge reads as a moving front, not a hard cap.
	val lead = logo.spineLength * LEAD_FRACTION
	val tail = (front - lead).coerceAtLeast(0f)
	if (front - tail <= 0f) return
	for (i in 0 until LEAD_SLICES) {
		val from = tail + (front - tail) * i / LEAD_SLICES
		val to = tail + (front - tail) * (i + 1) / LEAD_SLICES
		if (to - from <= 0f) continue
		clipPath(logo.outlineOf(from, to, logo.slicePath, logo.sliceOutline)) {
			drawPath(
				path = logo.waveform,
				brush = SolidColor(Color.White),
				alpha = strength * HIGHLIGHT_ALPHA * (i + 1f) / LEAD_SLICES
			)
		}
	}
}

private fun leadingEdgeStrength(progress: Float): Float {
	val rampIn = (progress / 0.04f).coerceIn(0f, 1f)
	// Hold almost to the tip so the front stays followable for the whole stroke,
	// then drop quickly so it lands rather than dimming out early.
	val rampOut = 1f - ((progress - 0.92f) / 0.08f).coerceIn(0f, 1f)
	return rampIn * rampOut
}

/**
 * Logo paths, brushes and reveal scratch buffers, all in the source SVG's 1024-unit
 * space so the geometry and the reveal share one coordinate system. Allocated once
 * and reused across frames.
 */
private class LogoGeometry {
	val ring = parse(RING_PATH)
	val disc = parse(DISC_PATH)
	val hole = parse(HOLE_PATH)
	val waveform = parse(WAVEFORM_PATH)
	val arcOuterLeft = parse(ARC_OUTER_LEFT_PATH)
	val arcRight = parse(ARC_RIGHT_PATH)
	val arcInnerLeft = parse(ARC_INNER_LEFT_PATH)

	val discBrush = Brush.linearGradient(
		listOf(Color(0xFF774CFB), Color(0xFF6C77F9)),
		start = Offset(516.198f, 611.003f),
		end = Offset(516.301f, 434.999f)
	)
	val waveformBrush = Brush.linearGradient(
		listOf(Color(0xFF4062F9), Color(0xFF329FF3)),
		start = Offset(712.428f, 773.559f),
		end = Offset(740.695f, 412.152f)
	)
	val arcOuterLeftBrush = Brush.linearGradient(
		listOf(Color(0xFF3094F3), Color(0xFF34BEEF)),
		start = Offset(402.600f, 523.376f),
		end = Offset(392.038f, 288.191f)
	)
	val arcInnerLeftBrush = Brush.linearGradient(
		listOf(Color(0xFF3191F4), Color(0xFF32ABF1)),
		start = Offset(442.272f, 520.946f),
		end = Offset(427.126f, 364.002f)
	)

	val measure = PathMeasure().apply { setPath(buildSpine(SPINE_POINTS), false) }
	val spineLength = measure.length
	val revealPath = Path()
	val revealOutline = Path()
	val slicePath = Path()
	val sliceOutline = Path()

	/**
	 * Butt cap: a round cap projects half the stroke width (24 units) past the
	 * measured tip, softening the leading edge into a blob instead of a followable
	 * front, and making the highlight's ramp slices overlap and compound alpha.
	 * Round join still smooths the ribbon's curves.
	 */
	private val maskStroke = android.graphics.Paint().apply {
		style = android.graphics.Paint.Style.STROKE
		strokeWidth = SPINE_STROKE
		strokeCap = android.graphics.Paint.Cap.BUTT
		strokeJoin = android.graphics.Paint.Join.ROUND
	}

	/**
	 * Fills [out] with the outline of the spine between [from] and [to], for use as a
	 * clip region. clipPath needs an enclosed area, so the stroked centreline has to
	 * be converted to a fillable outline rather than drawn as a stroke.
	 */
	fun outlineOf(from: Float, to: Float, segment: Path, out: Path): Path {
		segment.reset()
		measure.getSegment(from, to, segment, true)
		out.reset()
		maskStroke.getFillPath(segment.asAndroidPath(), out.asAndroidPath())
		return out
	}

	private fun parse(data: String) = PathParser().parsePathString(data).toPath()
}

/** Catmull-Rom through the centreline samples, for a spine that is smooth to trace. */
private fun buildSpine(points: List<Offset>): Path {
	val path = Path()
	path.moveTo(points.first().x, points.first().y)
	for (i in 0 until points.size - 1) {
		val p0 = points[(i - 1).coerceAtLeast(0)]
		val p1 = points[i]
		val p2 = points[i + 1]
		val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
		path.cubicTo(
			p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
			p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
			p2.x, p2.y
		)
	}
	return path
}

/**
 * Centreline of the waveform ribbon, sampled by averaging its outbound and return
 * edges. Starts inside the vinyl's annulus at the ribbon's rounded tail, sweeps
 * around the disc to the tall spike, then down to the trough and out to the tip.
 */
private val SPINE_POINTS = listOf(
	Offset(511f, 743f),
	Offset(569f, 737f),
	Offset(634f, 720f),
	Offset(688f, 675f),
	Offset(717f, 614f),
	Offset(736f, 560f),
	Offset(741f, 533f),
	Offset(748f, 496f),
	Offset(785f, 429f),
	Offset(807f, 453f),
	Offset(819f, 497f),
	Offset(825f, 537f),
	Offset(833f, 570f),
	Offset(858f, 599f),
	Offset(880f, 570f),
	Offset(892f, 553f),
	Offset(899f, 535f),
	Offset(906f, 522f),
	Offset(920f, 515f),
	Offset(948f, 518f)
)

private const val RING_PATH =
	"M497.962 201.714C504.844 201.053 517.949 201.593 524.784 201.969C569.079 204.18 612.391 215.82 651.826 236.11C663.508 242.265 674.851 249.042 685.806 256.412C696.505 263.648 734.136 292.435 737.49 304.016C738.725 308.172 738.174 312.655 735.968 316.388C733.486 320.679 731.031 321.926 726.467 323.247C717.56 326.083 711.343 318.56 705.394 312.952C692.112 300.433 678.199 288.517 662.732 278.636C599.424 237.617 522.351 223.596 448.654 239.69C374.365 255.842 309.564 300.905 268.565 364.926C227.409 429.057 213.28 506.859 229.259 581.366C246.05 656.315 291.692 721.631 356.298 763.168C426.337 807.349 500.269 819.647 580.89 801.4C617.893 790.787 643.128 781.351 675.685 759.34C688.974 750.455 701.504 740.484 713.146 729.529C719.687 723.343 725.761 716.411 732.639 710.677C734.525 709.105 736.011 708.668 738.422 708.414C742.636 707.948 746.855 709.251 750.072 712.012C758.478 719.219 755.713 729.593 749.04 736.803C694.492 795.737 615.895 834.834 535.604 840.347C450.786 846.562 367.001 818.617 302.895 762.732C237.407 706.561 197.326 626.362 191.715 540.266C186.046 456.121 216.65 371.824 272.032 308.628C316.963 257.137 377.624 221.906 444.613 208.397C462.904 204.654 479.442 202.93 497.962 201.714Z"

private const val DISC_PATH =
	"M508.709 434.748C557.483 430.508 600.502 466.511 604.919 515.27C609.336 564.029 573.488 607.177 524.746 611.771C475.753 616.388 432.335 580.316 427.895 531.307C423.455 482.298 459.684 439.011 508.709 434.748Z"

private const val HOLE_PATH =
	"M512.009 497.754C521.307 496.161 530.744 499.69 536.716 506.994C542.688 514.297 544.272 524.247 540.864 533.044C537.456 541.841 529.582 548.127 520.248 549.501C506.051 551.591 492.814 541.871 490.557 527.699C488.3 513.527 497.864 500.177 512.009 497.754Z"

private const val WAVEFORM_PATH =
	"M778.232 415.095C785.227 414.58 791.808 415.294 798.218 418.29C804.156 421.11 809.26 425.425 813.029 430.811C830.974 456.142 834.663 515.815 841.276 545.925C842.904 553.338 844.073 562.193 846.699 569.402C848.507 574.364 851.666 582.622 856.834 584.782C858.876 585.635 861.639 585.3 863.488 584.212C875.194 577.322 878.689 551.545 883.16 539.012C887.423 525.698 893.548 513.57 906.625 506.752C918.293 500.669 941.501 499.502 948.962 513.244C950.762 516.56 950.203 521.263 948.805 524.709C943.386 538.061 929.387 528.243 919.795 532.035C915.691 534.033 912.804 541.277 911.189 545.202C902.099 567.373 900.451 597.452 876.279 609.278C857.838 618.299 836.029 609.162 826.6 591.904C816.936 574.213 813.79 549.961 810.514 530.311C808.201 516.054 805.775 501.816 803.234 487.597C801.097 476.108 795.541 446.613 785.474 443.616C766.264 437.898 761.089 500.547 759.921 508.901C758.52 518.633 757.019 528.351 755.419 538.052C753.149 551.398 749.554 567.742 745.893 580.75C741.023 597.691 734.51 614.115 726.446 629.789C696.715 686.93 645.436 729.865 583.959 749.09C564.287 755.31 524.317 763.499 503.824 756.31C496.317 753.677 496.014 742.305 500.089 736.353C504.704 729.614 511.794 729.902 519.151 729.888C531.535 729.864 543.835 728.721 555.921 726.21C624.164 711.602 681.164 664.962 708.991 600.961C719.106 577.55 724.194 553.057 728.919 528.108C735.531 493.19 733.496 424.364 778.232 415.095Z"

private const val ARC_OUTER_LEFT_PATH =
	"M500.576 282.712C512.926 282.027 528.794 287.296 522.179 303.143C516.933 315.711 491.041 312.197 478.71 314.115C440.722 320.023 403.627 336.151 374.357 361.389C338.321 392.03 313.865 434.087 305.057 480.561C302.963 492.161 302.281 501.032 301.902 512.788C301.658 520.365 299.597 523.324 294.082 528.024C288.902 530.134 281.832 530.951 277.469 526.889C268.127 518.191 272.608 495.274 274.433 483.763C281.365 439.145 300.92 397.442 330.789 363.579C375.711 312.142 433.218 287.257 500.576 282.712Z"

private const val ARC_RIGHT_PATH =
	"M665.445 511.542C686.25 511.842 681.783 533.282 679.58 547.167C673.963 581.12 657.867 612.465 633.546 636.813C600.412 670.637 559.937 685.547 512.918 686.033C510.393 685.929 508.341 685.666 506.014 684.547C502.346 682.789 499.523 679.65 498.164 675.816C493.722 663.037 505.567 656.65 516.662 657.269C529.459 657.984 546.237 654.54 558.398 650.571C604.102 635.698 638.625 597.898 649.304 551.036C651.618 541.072 651.761 532.56 653.263 522.626C654.22 516.3 659.695 513.332 665.445 511.542Z"

private const val ARC_INNER_LEFT_PATH =
	"M504.847 356.223C507.171 355.98 509.795 355.988 512.053 356.615C516.138 357.726 519.597 360.449 521.634 364.16C525.777 371.869 522.027 380.736 514.001 383.885C508.283 386.128 501.466 384.944 495.509 385.802C479.912 388.046 464.178 392.455 450.077 399.511C414.153 417.467 388.127 450.529 379.109 489.664C377.447 497.082 376.687 503.566 376.356 511.154C375.957 520.285 374.377 525.233 365.73 529.336C364.928 529.504 364.119 529.637 363.306 529.737C342.292 532.127 346.226 507.586 348.145 494.851C352.678 463.895 366.287 434.976 387.251 411.752C418.034 377.312 459.032 358.728 504.847 356.223Z"
