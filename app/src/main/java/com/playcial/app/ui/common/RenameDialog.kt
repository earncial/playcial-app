package com.playcial.app.ui.common

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class RenameDialog(
    private val currentName: String,
    private val onConfirm: (String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 48, 56, 40)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 40f
            }
        }

        card.addView(TextView(requireContext()).apply {
            text = "Rename"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val input = EditText(requireContext()).apply {
            setText(currentName)
            setSelection(currentName.length)
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setStroke(2, Color.parseColor("#00AAFF"))
                cornerRadius = 16f
            }
        }
        card.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })

        val buttonRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 32, 0, 0)
        }
        buttonRow.addView(Button(requireContext()).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#666666"))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            setOnClickListener { dismiss() }
        })
        buttonRow.addView(Button(requireContext()).apply {
            text = "Rename"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00AAFF"))
                cornerRadius = 24f
            }
            setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) onConfirm(newName)
                dismiss()
            }
        })
        card.addView(buttonRow)

        dialog.setContentView(card)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setDimAmount(0.6f)
        return dialog
    }

    companion object {
        const val TAG = "RenameDialog"
    }
}
