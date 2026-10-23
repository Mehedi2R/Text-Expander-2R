package com.textexpander.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.textexpander.app.data.ShortcutSummary
import com.textexpander.app.databinding.ItemShortcutBinding

class ShortcutsAdapter(
    private val onItemClick: (ShortcutSummary) -> Unit,
    private val onMenuClick: (ShortcutSummary) -> Unit,
    private val onItemLongClick: ((ShortcutSummary) -> Unit)? = null,
    private val onCopyClick: ((ShortcutSummary) -> Unit)? = null
) : ListAdapter<ShortcutSummary, ShortcutsAdapter.ShortcutViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShortcutSummary) {
            binding.tvShortcutKey.text = item.shortcutKey
            binding.tvExpandedPreview.text = item.snippet

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick?.invoke(item)
                true
            }
            binding.btnShortcutCopy.setOnClickListener {
                onCopyClick?.invoke(item)
            }
            binding.btnShortcutMenu.setOnClickListener { onMenuClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ShortcutSummary>() {
        override fun areItemsTheSame(oldItem: ShortcutSummary, newItem: ShortcutSummary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ShortcutSummary, newItem: ShortcutSummary): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.shortcutKey == newItem.shortcutKey &&
                    oldItem.snippet == newItem.snippet &&
                    oldItem.charCount == newItem.charCount &&
                    oldItem.textByteSize == newItem.textByteSize &&
                    oldItem.updatedAt == newItem.updatedAt
        }
    }
}
