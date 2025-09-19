package com.gvam.kioskportal.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gvam.kioskportal.model.AppEntry

class PortalGridAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<PortalGridAdapter.VH>() {

    private val data = mutableListOf<AppEntry>()

    fun submit(items: List<AppEntry>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    class VH(itemView: View, private val onClick: (String) -> Unit)
        : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(entry: AppEntry) {
            title.text = entry.label
            itemView.setOnClickListener { onClick(entry.packageName) }
        }
    }
}
