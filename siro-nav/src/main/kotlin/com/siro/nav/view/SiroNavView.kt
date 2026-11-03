package com.siro.nav.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siro.nav.engine.NavEvent

class SiroNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val instructionLabel = TextView(context)
    private val distanceLabel = TextView(context)
    private val speedLabel = TextView(context)
    private val remainingLabel = TextView(context)
    private val exitButton = Button(context)

    var onExitListener: (() -> Unit)? = null

    init {
        setupUI(context)
    }

    private fun setupUI(context: Context) {
        val dp = context.resources.displayMetrics.density

        // Top Banner
        val topBanner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 25, 25, 25))
                cornerRadius = 16 * dp
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), 0)
                gravity = Gravity.TOP
            }
        }

        instructionLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }

        distanceLabel.apply {
            setTextColor(Color.parseColor("#34D399"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }

        topBanner.addView(instructionLabel)
        topBanner.addView(distanceLabel)
        addView(topBanner)

        // Speed Badge
        val speedBadge = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((3 * dp).toInt(), Color.RED)
                cornerRadius = 24 * dp
            }
            layoutParams = LayoutParams(
                (48 * dp).toInt(),
                (48 * dp).toInt()
            ).apply {
                setMargins((16 * dp).toInt(), 0, 0, (88 * dp).toInt())
                gravity = Gravity.BOTTOM or Gravity.START
            }
        }

        speedLabel.apply {
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        speedBadge.addView(speedLabel)
        addView(speedBadge)

        // Bottom Bar
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 30, 30, 30))
                cornerRadius = 20 * dp
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (64 * dp).toInt()
            ).apply {
                setMargins((12 * dp).toInt(), 0, (12 * dp).toInt(), (16 * dp).toInt())
                gravity = Gravity.BOTTOM
            }
        }

        remainingLabel.apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        exitButton.apply {
            text = "خروج"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = 12 * dp
            }
            setOnClickListener { onExitListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams((72 * dp).toInt(), (38 * dp).toInt())
        }

        bottomBar.addView(remainingLabel)
        bottomBar.addView(exitButton)
        addView(bottomBar)
    }

    fun updateInstruction(instruction: NavEvent.Instruction) {
        instructionLabel.text = instruction.instruction
        distanceLabel.text = formatDistance(instruction.distanceMeters)
    }

    fun updateProgress(progress: NavEvent.Progress) {
        speedLabel.text = progress.currentSpeedKmh.toInt().toString()
        val km = String.format("%.1f كم", progress.remainingDistanceMeters / 1000.0)
        val mins = "${progress.remainingDurationSeconds / 60} دقيقة"
        remainingLabel.text = "$km • $mins"
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format("بعد %.1f كم", meters / 1000.0)
        } else {
            "بعد ${meters.toInt()} م"
        }
    }
}
