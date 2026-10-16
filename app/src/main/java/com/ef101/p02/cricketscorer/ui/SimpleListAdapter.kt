package com.ef101.p02.cricketscorer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ef101.p02.cricketscorer.databinding.ItemTextBinding

/**
 * A tiny two-line list adapter shared by the teams, players and saved-matches
 * screens. Each row has a bold primary line and an optional secondary line.
 */
class SimpleListAdapter(
    private var items: List<Row>,
    private val onClick: ((Row) -> Unit)? = null,
    private val onLongClick: ((Row) -> Unit)? = null
) : RecyclerView.Adapter<SimpleListAdapter.VH>() {

    data class Row(
        val id: Int,
        val primary: String,
        val secondary: String? = null,
        val tag: Any? = null
    )

    class VH(val binding: ItemTextBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTextBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.binding.text1.text = row.primary
        holder.binding.text2.text = row.secondary.orEmpty()
        holder.binding.text2.visibility =
            if (row.secondary.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        holder.binding.root.setOnClickListener { onClick?.invoke(row) }
        holder.binding.root.setOnLongClickListener {
            onLongClick?.invoke(row)
            onLongClick != null
        }
    }

    fun submit(newItems: List<Row>) {
        items = newItems
        notifyDataSetChanged()
    }
}
