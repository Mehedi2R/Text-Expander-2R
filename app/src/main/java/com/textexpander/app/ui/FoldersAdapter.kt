package com.textexpander.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.textexpander.app.databinding.ItemFolderCategoryBinding

data class FolderCategory(
    val name: String,
    val count: Int
)

class FoldersAdapter(
    private val onFolderClick: (FolderCategory) -> Unit,
    private val onFolderLongClick: ((FolderCategory) -> Unit)? = null
) : ListAdapter<FolderCategory, FoldersAdapter.FolderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FolderCategory) {
            binding.tvFolderName.text = item.name
            val countStr = if (item.count == 1) "1 text snippet" else "${item.count} text snippets"
            binding.tvFolderCount.text = countStr

            binding.root.setOnClickListener { onFolderClick(item) }
            binding.root.setOnLongClickListener {
                onFolderLongClick?.invoke(item)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FolderCategory>() {
        override fun areItemsTheSame(oldItem: FolderCategory, newItem: FolderCategory): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: FolderCategory, newItem: FolderCategory): Boolean {
            return oldItem == newItem
        }
    }
}
