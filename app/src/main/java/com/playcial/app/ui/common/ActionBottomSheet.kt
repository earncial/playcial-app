package com.playcial.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

data class ActionItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val iconRes: Int,
    val isDestructive: Boolean = false
)

/**
 * Reusable animated bottom sheet used for every contextual menu in the app
 * (video actions, folder actions, sort, filter, etc.) instead of default
 * Android popup menus / dialogs.
 */
class ActionBottomSheet(
    private val title: String,
    private val items: List<ActionItem>,
    private val onItemSelected: (ActionItem) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 48)
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(16, 8, 16, 24)
        }
        root.addView(titleView)

        items.forEach { item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16, 24, 16, 24)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    onItemSelected(item)
                    dismiss()
                }
            }

            val icon = ImageView(requireContext()).apply {
                setImageResource(item.iconRes)
                layoutParams = LinearLayout.LayoutParams(64, 64)
                if (item.isDestructive) {
                    setColorFilter(android.graphics.Color.parseColor("#E53935"))
                }
            }
            row.addView(icon)

            val textContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 0, 0, 0)
            }
            val titleText = TextView(requireContext()).apply {
                text = item.title
                textSize = 15f
                if (item.isDestructive) setTextColor(android.graphics.Color.parseColor("#E53935"))
            }
            textContainer.addView(titleText)
            item.description?.let { desc ->
                val descText = TextView(requireContext()).apply {
                    text = desc
                    textSize = 12f
                    alpha = 0.6f
                }
                textContainer.addView(descText)
            }
            row.addView(textContainer)
            root.addView(row)
        }

        return root
    }

    companion object {
        const val TAG = "ActionBottomSheet"
    }
}
