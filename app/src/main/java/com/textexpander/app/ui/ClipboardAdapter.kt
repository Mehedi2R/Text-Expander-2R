package com.textexpander.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.textexpander.app.data.ClipboardEntity
import com.textexpander.app.databinding.ItemClipboardBinding

class ClipboardAdapter(
    private val onCopyClick: (ClipboardEntity) -> Unit,
    private val onConvertToShortcutClick: (ClipboardEntity) -> Unit,
    private val onDeleteClick: (ClipboardEntity) -> Unit
) : ListAdapter<ClipboardEntity, ClipboardAdapter.ClipboardViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val binding = ItemClipboardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClipboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private val expandedItemIds = mutableSetOf<Long>()

    inner class ClipboardViewHolder(
        private val binding: ItemClipboardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClipboardEntity) {
            val isExpanded = expandedItemIds.contains(item.id)
            if (isExpanded) {
                binding.tvClipboardContent.maxLines = Integer.MAX_VALUE
                binding.tvClipboardContent.ellipsize = null
            } else {
                binding.tvClipboardContent.maxLines = 3
                binding.tvClipboardContent.ellipsize = android.text.TextUtils.TruncateAt.END
            }

            binding.tvClipboardContent.text = item.content
            binding.tvClipboardCount.text = "1x"

            // Clicking the card or text toggles expansion (Image 1 collapsed -> Image 2 cp2 expanded)
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (expandedItemIds.contains(item.id)) {
                        expandedItemIds.remove(item.id)
                    } else {
                        expandedItemIds.add(item.id)
                    }
                    notifyItemChanged(pos)
                }
            }

            // Long click allows options (Delete, Copy, Snippet)
            binding.root.setOnLongClickListener {
                onDeleteClick(item)
                true
            }

            // Tap copy icon to copy full text
            binding.btnCopyClipboardItem.setOnClickListener {
                onCopyClick(item)
            }

            // Tap snippet icon to convert to text expander shortcut
            binding.btnConvertToShortcut.setOnClickListener {
                onConvertToShortcutClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ClipboardEntity>() {
        override fun areItemsTheSame(oldItem: ClipboardEntity, newItem: ClipboardEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClipboardEntity, newItem: ClipboardEntity): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.content == newItem.content &&
                    oldItem.copyCount == newItem.copyCount &&
                    oldItem.copiedAt == newItem.copiedAt
        }
    }
}
