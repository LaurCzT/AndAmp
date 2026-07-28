package com.winamp.classic.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import com.winamp.classic.R

class WinampEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onBandLevelChangedListener: ((bandIndex: Int, levelDb: Int) -> Unit)? = null
    var onEqEnabledChangedListener: ((Boolean) -> Unit)? = null
    var onPresetSelectedListener: ((presetName: String) -> Unit)? = null

    val bandSeekBars = mutableListOf<SeekBar>()
    var isEqOn: Boolean = true

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_winamp_equalizer, this, true)

        val btnOn = findViewById<Button>(R.id.btnEqOn)
        val btnPresets = findViewById<Button>(R.id.btnEqPresets)

        btnOn.setOnClickListener {
            isEqOn = !isEqOn
            btnOn.isSelected = isEqOn
            btnOn.setTextColor(if (isEqOn) Color.parseColor("#00FF2A") else Color.parseColor("#808080"))
            onEqEnabledChangedListener?.invoke(isEqOn)
        }

        btnPresets.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            val presets = listOf("Flat", "Rock", "Pop", "Techno", "Dance", "Soft", "Classical", "Full Bass")
            for (p in presets) {
                popup.menu.add(p)
            }
            popup.setOnMenuItemClickListener { item ->
                val name = item.title.toString()
                applyPresetValues(name)
                onPresetSelectedListener?.invoke(name)
                true
            }
            popup.show()
        }

        val seekIds = listOf(
            R.id.seekEq60, R.id.seekEq170, R.id.seekEq310, R.id.seekEq600,
            R.id.seekEq1k, R.id.seekEq3k, R.id.seekEq6k, R.id.seekEq12k,
            R.id.seekEq14k, R.id.seekEq16k
        )

        for ((idx, id) in seekIds.withIndex()) {
            val sb = findViewById<SeekBar>(id)
            if (sb != null) {
                bandSeekBars.add(sb)
                sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val levelDb = progress - 12
                            onBandLevelChangedListener?.invoke(idx, levelDb)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        }
    }

    private fun applyPresetValues(presetName: String) {
        val levels = when (presetName) {
            "Rock" -> listOf(4, 3, 2, 0, -1, 1, 3, 4, 4, 4)
            "Pop" -> listOf(-1, 1, 3, 4, 3, 0, -1, -1, 0, 1)
            "Techno" -> listOf(4, 3, 0, -2, -2, 0, 3, 4, 4, 3)
            "Dance" -> listOf(5, 4, 2, 0, 0, -2, -3, -3, 0, 0)
            "Soft" -> listOf(2, 1, 0, -1, 0, 1, 2, 3, 4, 4)
            "Classical" -> listOf(4, 3, 2, 2, -1, -1, 0, 2, 3, 3)
            "Full Bass" -> listOf(6, 5, 4, 2, 0, -2, -4, -5, -6, -6)
            else -> listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0) // Flat
        }

        for (i in bandSeekBars.indices) {
            val lvl = levels.getOrElse(i) { 0 }
            bandSeekBars[i].progress = lvl + 12
            onBandLevelChangedListener?.invoke(i, lvl)
        }
    }
}
