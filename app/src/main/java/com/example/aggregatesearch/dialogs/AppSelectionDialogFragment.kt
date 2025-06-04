package com.example.aggregatesearch.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aggregatesearch.adapters.AppAdapter
import com.example.aggregatesearch.databinding.DialogSelectAppBinding
import com.example.aggregatesearch.utils.AppPackageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用选择对话框片段
 */
class AppSelectionDialogFragment : DialogFragment() {

    private var _binding: DialogSelectAppBinding? = null
    private val binding get() = _binding!!

    private lateinit var appPackageManager: AppPackageManager
    private lateinit var appAdapter: AppAdapter

    private var searchJob: Job? = null
    private var onAppSelected: ((packageName: String, appName: String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSelectAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appPackageManager = AppPackageManager(requireContext())

        setupRecyclerView()
        setupSearchInput()
        setupButtons()

        // 加载应用列表
        loadAppsList()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appInfo ->
            onAppSelected?.invoke(appInfo.packageName, appInfo.appName)
            dismiss()
        }

        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }
    }

    private fun setupSearchInput() {
        binding.editTextSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // 添加防抖动延迟
                    val query = s.toString().trim()
                    if (query.isNotEmpty()) {
                        val searchResults = appPackageManager.searchApps(query)
                        appAdapter.submitList(searchResults)
                        updateLoadingVisibility(false)
                    } else {
                        loadAppsList()
                    }
                }
            }
        })
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnClearPackage.setOnClickListener {
            onAppSelected?.invoke("", "")
            dismiss()
        }
    }

    private fun loadAppsList() {
        binding.textViewLoading.visibility = View.VISIBLE
        binding.recyclerViewApps.visibility = View.GONE

        lifecycleScope.launch {
            val apps = appPackageManager.getNonSystemApps()
            appAdapter.submitList(apps)
            updateLoadingVisibility(false)
        }
    }

    private fun updateLoadingVisibility(isLoading: Boolean) {
        binding.textViewLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.recyclerViewApps.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    fun setOnAppSelectedListener(listener: (packageName: String, appName: String) -> Unit) {
        this.onAppSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        searchJob?.cancel()
    }

    companion object {
        const val TAG = "AppSelectionDialogFragment"

        fun newInstance(): AppSelectionDialogFragment {
            return AppSelectionDialogFragment()
        }
    }
}
