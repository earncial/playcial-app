package com.playcial.app.ui.common

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

enum class DialogType { DELETE, WARNING, SUCCESS, ERROR, INFO, PERMISSION, LOADING }

/**
 * Replaces every use of android.app.AlertDialog in the app. Rounded card,
 * dim scrim, animated icon pop-in, ripple buttons.
 */
class PlaycialDialog private constructor(
    private val type: DialogType,
    private val title: String,
    private val message: String,
    private val positiveText: String?,
    private val negativeText: String?,
    private val onPositive: (() -> Unit)?,
    private val onNegative: (() -> Unit)?,
    private val cancelable: Boolean
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(cancelable)

        val accent = when (type) {
            DialogType.DELETE, DialogType.ERROR -> "#E53935"
            DialogType.WARNING -> "#FB8C00"
            DialogType.SUCCESS -> "#43A047"
            DialogType.PERMISSION, DialogType.INFO, DialogType.LOADING -> "#00AAFF"
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 64, 56, 40)
            background = GradientDrawable().apply {
                setColor(resolveSurfaceColor())
                cornerRadius = 40f
            }
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(140, 140)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(accent + "26"))
            }
            setColorFilter(Color.parseColor(accent))
            setPadding(28, 28, 28, 28)
            setImageResource(iconForType(type))
            scaleX = 0f
            scaleY = 0f
        }
        card.addView(icon)

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 12)
        }
        card.addView(titleView)

        val messageView = TextView(requireContext()).apply {
            text = message
            textSize = 14f
            alpha = 0.75f
            gravity = Gravity.CENTER
        }
        card.addView(messageView)

        if (type != DialogType.LOADING) {
            val buttonRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 0)
            }

            negativeText?.let { text ->
                val btn = Button(requireContext()).apply {
                    this.text = text
                    setTextColor(Color.parseColor("#666666"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00000000"))
                        cornerRadius = 24f
                    }
                    setOnClickListener {
                        onNegative?.invoke()
                        dismiss()
                    }
                }
                buttonRow.addView(btn)
            }

            positiveText?.let { text ->
                val btn = Button(requireContext()).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(accent))
                        cornerRadius = 24f
                    }
                    setOnClickListener {
                        onPositive?.invoke()
                        dismiss()
                    }
                }
                buttonRow.addView(btn)
            }
            card.addView(buttonRow)
        }

        dialog.setContentView(card)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setDimAmount(0.6f)

        dialog.setOnShowListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(icon, View.SCALE_X, 0f, 1f),
                    ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0f, 1f)
                )
                duration = 320
                interpolator = OvershootInterpolator()
                start()
            }
        }

        return dialog
    }

    private fun resolveSurfaceColor(): Int {
        val nightModeFlags = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            Color.parseColor("#1E1E1E")
        } else {
            Color.WHITE
        }
    }

    private fun iconForType(type: DialogType): Int = android.R.drawable.ic_dialog_info

    class Builder {
        private var type = DialogType.INFO
        private var title = ""
        private var message = ""
        private var positiveText: String? = "OK"
        private var negativeText: String? = "Cancel"
        private var onPositive: (() -> Unit)? = null
        private var onNegative: (() -> Unit)? = null
        private var cancelable = true

        fun type(t: DialogType) = apply { type = t }
        fun title(t: String) = apply { title = t }
        fun message(m: String) = apply { message = m }
        fun positive(text: String, action: (() -> Unit)? = null) = apply {
            positiveText = text; onPositive = action
        }
        fun negative(text: String?, action: (() -> Unit)? = null) = apply {
            negativeText = text; onNegative = action
        }
        fun cancelable(c: Boolean) = apply { cancelable = c }

        fun build() = PlaycialDialog(
            type, title, message, positiveText, negativeText, onPositive, onNegative, cancelable
        )
    }

    companion object {
        const val TAG = "PlaycialDialog"
    }
}
