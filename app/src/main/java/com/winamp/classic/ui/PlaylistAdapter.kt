package com.winamp.classic.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.winamp.classic.R
import com.winamp.classic.model.Track

class PlaylistAdapter(
    private var tracks: List<Track>,
    private var selectedIndex: Int,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrackInfo: TextView = view.findViewById(R.id.tvTrackInfo)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val container: View = view.findViewById(R.id.itemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_winamp_playlist_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        val indexStr = (position + 1).toString()
        holder.tvTrackInfo.text = "$indexStr. ${track.artist} - ${track.title}"
        holder.tvDuration.text = track.getFormattedDuration()

        if (position == selectedIndex) {
            // Selected track - bright Winamp blue background banner
            holder.container.setBackgroundColor(Color.parseColor("#0000A8"))
            holder.tvTrackInfo.setTextColor(Color.WHITE)
            holder.tvDuration.setTextColor(Color.WHITE)
            holder.tvTrackInfo.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            holder.tvDuration.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        } else {
            // Unselected - dark background with retro green text
            holder.container.setBackgroundColor(Color.TRANSPARENT)
            holder.tvTrackInfo.setTextColor(Color.parseColor("#00FF2A"))
            holder.tvDuration.setTextColor(Color.parseColor("#00FF2A"))
            holder.tvTrackInfo.typeface = Typeface.MONOSPACE
            holder.tvDuration.typeface = Typeface.MONOSPACE
        }

        holder.itemView.setOnClickListener {
            val prevSelected = selectedIndex
            selectedIndex = holder.bindingAdapterPosition
            notifyItemChanged(prevSelected)
            notifyItemChanged(selectedIndex)
            onItemClick(selectedIndex)
        }
    }

    override fun getItemCount(): Int = tracks.size

    fun updateTracks(newTracks: List<Track>, newSelectedIndex: Int) {
        tracks = newTracks
        selectedIndex = newSelectedIndex
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        val prev = selectedIndex
        selectedIndex = index
        notifyItemChanged(prev)
        notifyItemChanged(selectedIndex)
    }
}
