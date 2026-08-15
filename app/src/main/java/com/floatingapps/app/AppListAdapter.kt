package com.floatingapps.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val onClick: (FloatableApp) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var allItems: List<FloatableApp> = emptyList()
    private var shownItems: List<FloatableApp> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val label: TextView = view.findViewById(R.id.tvAppLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_app_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = shownItems[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount(): Int = shownItems.size

    fun submitList(items: List<FloatableApp>) {
        allItems = items
        shownItems = items
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        shownItems = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
