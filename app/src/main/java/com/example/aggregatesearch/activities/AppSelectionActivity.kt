package com.example.aggregatesearch.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class AppSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var allApps: List<AppInfo>
    private lateinit var filteredApps: MutableList<AppInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyView: TextView
    private lateinit var searchEditText: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        // 初始化视图
        recyclerView = findViewById(R.id.recyclerViewApps)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        searchEditText = findViewById(R.id.editTextSearchApp)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // 设置搜索功能
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterApps(searchEditText.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s.toString())
            }
        })

        // 加载应用列表
        loadApps()
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        Thread {
            // 获取所有已安装的应用信息
            val packageManager = packageManager

            // 处理Android 11及以上版本的包可见性问题
            // 使用queryIntentActivities获取所有可用应用
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            val installedApps = ArrayList<AppInfo>()

            // 获取所有应用
            try {
                // 先尝试获取有启动图标的���用
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val pkgAppsList = packageManager.queryIntentActivities(mainIntent, 0)

                for (resolveInfo in pkgAppsList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    val appLabel = resolveInfo.loadLabel(packageManager).toString()
                    try {
                        val icon = packageManager.getApplicationIcon(packageName)
                        installedApps.add(
                            AppInfo(
                                name = appLabel,
                                packageName = packageName,
                                icon = icon
                            )
                        )
                    } catch (e: Exception) {
                        // 忽略无法获取图标的应用
                    }
                }

                // 然后尝试获取已安装但没有启动图标的应用
                val installedPackages = packageManager.getInstalledPackages(0)
                for (packageInfo in installedPackages) {
                    val packageName = packageInfo.packageName
                    // 检查是否已经添加过
                    if (installedApps.none { it.packageName == packageName }) {
                        try {
                            val appLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                            val icon = packageManager.getApplicationIcon(packageName)
                            installedApps.add(
                                AppInfo(
                                    name = appLabel,
                                    packageName = packageName,
                                    icon = icon
                                )
                            )
                        } catch (e: Exception) {
                            // 忽略无法获取信息的应用
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 按名称排序
            allApps = installedApps.sortedBy { it.name.lowercase(Locale.getDefault()) }
            filteredApps = allApps.toMutableList()

            runOnUiThread {
                progressBar.visibility = View.GONE
                if (filteredApps.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    adapter = AppAdapter(filteredApps) { position ->
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_PACKAGE_NAME, filteredApps[position].packageName)
                        resultIntent.putExtra(EXTRA_APP_NAME, filteredApps[position].name)
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                    recyclerView.adapter = adapter
                }
            }
        }.start()
    }

    private fun filterApps(query: String) {
        if (!::allApps.isInitialized) return

        val searchText = query.lowercase(Locale.getDefault()).trim()

        if (searchText.isEmpty()) {
            filteredApps = allApps.toMutableList()
        } else {
            filteredApps = allApps.filter { app ->
                app.name.lowercase(Locale.getDefault()).contains(searchText) ||
                app.packageName.lowercase(Locale.getDefault()).contains(searchText)
            }.toMutableList()
        }

        if (filteredApps.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        if (::adapter.isInitialized) {
            adapter.updateApps(filteredApps)
        }
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private class AppAdapter(
        private var apps: List<AppInfo>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        fun updateApps(newApps: List<AppInfo>) {
            val oldApps = apps
            apps = newApps
            notifyDataSetChanged() // 简单起见使用notifyDataSetChanged，实际项目中可以使用DiffUtil优化
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.textViewAppName.text = app.name
            holder.textViewPackageName.text = app.packageName
            holder.imageViewIcon.setImageDrawable(app.icon)
            holder.itemView.setOnClickListener { onItemClick(position) }
        }

        override fun getItemCount(): Int = apps.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textViewAppName: TextView = view.findViewById(R.id.textViewAppName)
            val textViewPackageName: TextView = view.findViewById(R.id.textViewPackageName)
            val imageViewIcon: ImageView = view.findViewById(R.id.imageViewAppIcon)
        }
    }
}
