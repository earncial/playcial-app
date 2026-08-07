package com.playcial.app.ui.recyclebin

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.playcial.app.data.local.RecycleBinEntity
import com.playcial.app.databinding.ActivityRecycleBinBinding
import com.playcial.app.ui.common.DialogType
import com.playcial.app.ui.common.PlaycialDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private val viewModel: RecycleBinViewModel by viewModels()
    private lateinit var adapter: RecycleBinAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RecycleBinAdapter(
            onRestore = { entry -> viewModel.restore(entry) },
            onDeleteForever = { entry -> confirmPermanentDelete(entry) }
        )
        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        lifecycleScope.launch {
            viewModel.items.collectLatest { items ->
                adapter.submitList(items)
                binding.emptyState.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.events.collectLatest { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmPermanentDelete(entry: RecycleBinEntity) {
        PlaycialDialog.Builder()
            .type(DialogType.DELETE)
            .title("Delete forever?")
            .message("${entry.displayName} will be permanently removed. This cannot be undone.")
            .positive("Delete forever") { viewModel.deleteForever(entry) }
            .negative("Cancel")
            .build()
            .show(supportFragmentManager, PlaycialDialog.TAG)
    }
}
