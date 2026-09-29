package dev.cartu.lector

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

internal fun FragmentActivity.showDictionaryEditor(
    text: String,
    dictionary: PersonalDictionary,
    onClosed: (changed: Boolean) -> Unit,
) {
    val existing = dictionary.entry(text)
    var selectedColorIndex = existing?.colorIndex ?: dictionary.nextColorIndex()

    val translation = EditText(this).apply {
        hint = "Traducción (opcional)"
        setText(existing?.translation.orEmpty())
    }
    val swatches = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, 0)
    }
    fun renderSwatches() {
        swatches.removeAllViews()
        DICTIONARY_COLORS.forEachIndexed { index, color ->
            swatches.addView(
                colorSwatch(color, selected = index == selectedColorIndex) {
                    selectedColorIndex = index
                    renderSwatches()
                }
            )
        }
    }
    renderSwatches()

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(translation)
        addView(swatches)
    }

    val dialog = AlertDialog.Builder(this)
        .setTitle(text)
        .setView(content)
        .setPositiveButton("Guardar") { _, _ ->
            val saved = dictionary.saveEntry(text, translation.text.toString(), selectedColorIndex)
            if (saved == null) {
                toast("Selección no válida para el diccionario.")
                onClosed(false)
            } else {
                toast(if (existing == null) "«$saved» agregado al diccionario." else "«$saved» actualizado.")
                onClosed(true)
            }
        }
        .setNegativeButton("Cancelar") { _, _ -> onClosed(false) }
        .apply {
            if (existing != null) {
                setNeutralButton("Eliminar") { _, _ ->
                    if (dictionary.deleteEntry(text)) toast("«$text» eliminado del diccionario.")
                    onClosed(true)
                }
            }
        }
        .show()

    translation.requestFocus()
    dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
}

private fun FragmentActivity.colorSwatch(color: Int, selected: Boolean, onClick: () -> Unit): View =
    View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorWithAlpha(color, 255))
            setStroke(dp(if (selected) 3 else 1), if (selected) Color.BLACK else 0x33000000)
        }
        setOnClickListener { onClick() }
    }

private fun FragmentActivity.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun FragmentActivity.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
