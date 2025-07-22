/**
 * 对话框管理器
 * 负责处理所有的添加、编辑、删除确认对话框
 * 包括：添加分组、添加链接、编辑分组、编辑链接、删除确认等对话框
 */
package com.example.aggregatesearch.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.aggregatesearch.R
import com.example.aggregatesearch.SearchViewModel
import com.example.aggregatesearch.UrlLauncher
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.databinding.DialogAddGroupBinding
import com.example.aggregatesearch.databinding.DialogAddUrlBinding
import com.example.aggregatesearch.databinding.DialogEditGroupBinding
import com.example.aggregatesearch.databinding.DialogEditUrlBinding
import com.example.aggregatesearch.utils.IconLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DialogManager(
    private val context: Context,
    private val searchViewModel: SearchViewModel,
    private val appSelectionResultLauncher: ActivityResultLauncher<Intent>,
    private val onAppSelected: (String, String) -> Unit,
    private val onRefreshIconsForGroup: (Long) -> Unit,
    private val getCurrentSearchQuery: () -> String,
    private val onExitEditMode: () -> Unit = {}
) {

    private var currentPackageNameEditText: EditText? = null
    private var currentSelectedAppNameTextView: TextView? = null
    private var onAppSelectedGlobal: ((String, String) -> Unit)? = null

    fun showAddGroupDialog() {
        val dialogBinding = DialogAddGroupBinding.inflate(LayoutInflater.from(context))
        AlertDialog.Builder(context)
            .setTitle("添加分组")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val groupName = dialogBinding.editTextGroupName.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    searchViewModel.addGroup(groupName)
                    onExitEditMode() // 添加后退出编辑模式
                } else {
                    Toast.makeText(context, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showAddUrlDialog(preSelectedGroup: UrlGroup? = null) {
        val dialogBinding = DialogAddUrlBinding.inflate(LayoutInflater.from(context))
        val currentGroups = searchViewModel.allGroups.value

        if (currentGroups.isEmpty()) {
            Toast.makeText(context, "请先创建一个分组", Toast.LENGTH_SHORT).show()
            return
        }

        val groupNames = currentGroups.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerGroup.adapter = adapter

        if (preSelectedGroup != null) {
            val idx = currentGroups.indexOfFirst { g -> g.id == preSelectedGroup.id }
            if (idx >= 0) dialogBinding.spinnerGroup.setSelection(idx)
        } else {
            dialogBinding.spinnerGroup.setSelection(0)
        }

        var selectedPackageName = ""
        setupAppSelection(dialogBinding.editTextPackageName, dialogBinding.textViewSelectedAppName) { packageName, appName ->
            selectedPackageName = packageName
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            launchAppSelection()
        }

        dialogBinding.btnCancelAppBinding.setOnClickListener {
            selectedPackageName = ""
            dialogBinding.editTextPackageName.setText("")
            dialogBinding.textViewSelectedAppName.visibility = View.GONE
            Toast.makeText(context, "已取消应用绑定", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("添加搜索链接")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim()
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val selectedGroupPosition = dialogBinding.spinnerGroup.selectedItemPosition
            val selectedGroup = currentGroups[selectedGroupPosition]

            if (name.isNotEmpty()) {
                val searchUrl = SearchUrl(
                    name = name,
                    urlPattern = pattern,
                    groupId = selectedGroup.id,
                    packageName = selectedPackageName
                )
                searchViewModel.insert(searchUrl)
                onExitEditMode() // 添加后退出编辑模式
                dialog.dismiss()
            } else {
                Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showEditUrlDialog(searchUrl: SearchUrl) {
        val dialogBinding = DialogEditUrlBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setTitle("编辑搜索链接")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        // 应用主题颜色
        applyThemeColor(dialogBinding)

        // 预填充现有值
        dialogBinding.editTextUrlName.setText(searchUrl.name)
        dialogBinding.editTextUrlPattern.setText(searchUrl.urlPattern)

        // 设置图标相关
        setupIconSettings(dialogBinding, searchUrl)

        // 设置应用绑定
        var selectedPackageName = searchUrl.packageName
        setupAppBindingInEditDialog(dialogBinding, selectedPackageName) { packageName ->
            selectedPackageName = packageName
        }

        // 测试链接按钮
        dialogBinding.btnTestUrl.setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim().ifEmpty { "测试" }
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val tempUrl = searchUrl.copy(name = name, urlPattern = pattern, packageName = selectedPackageName)
            UrlLauncher.launchSearchUrls(context, getCurrentSearchQuery(), listOf(tempUrl))
        }

        // 删除按钮
        dialogBinding.buttonDeleteUrlDialog.setOnClickListener {
            showDeleteUrlConfirmationDialog(searchUrl, dialog)
        }

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim()
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val useTextIcon = dialogBinding.radioButtonTextIcon.isChecked
            var iconText = dialogBinding.editTextIconText.text.toString().trim()

            if (useTextIcon && iconText.isEmpty()) {
                iconText = name.take(1)
            }

            val bgColor = (dialogBinding.viewIconBackgroundColor.background as? ColorDrawable)?.color ?: searchUrl.iconBackgroundColor

            if (name.isNotEmpty()) {
                val updatedUrl = searchUrl.copy(
                    name = name,
                    urlPattern = pattern,
                    packageName = selectedPackageName,
                    useTextIcon = useTextIcon,
                    iconText = iconText,
                    iconBackgroundColor = bgColor
                )

                searchViewModel.updateUrl(updatedUrl)
                // 保存后自动退出编辑模式，让用户立即看到更新后的内容
                onExitEditMode()
                Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showEditGroupDialog(group: UrlGroup) {
        val dialogBinding = DialogEditGroupBinding.inflate(LayoutInflater.from(context))
        var selectedColor: String? = group.color

        // 应用主题颜色
        applyGroupEditThemeColor(dialogBinding)

        dialogBinding.editTextGroupName.setText(group.name)
        selectedColor?.let {
            dialogBinding.viewSelectedGroupColor.setBackgroundColor(it.toColorInt())
        }

        dialogBinding.buttonSelectGroupColor.setOnClickListener {
            showColorPicker(selectedColor ?: "#FFFFFF") { color, hexColor ->
                selectedColor = hexColor
                dialogBinding.viewSelectedGroupColor.setBackgroundColor(color)
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("编辑分组")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialogBinding.buttonDeleteGroupDialog.setOnClickListener {
            showDeleteGroupConfirmationDialog(group, dialog)
        }

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextGroupName.text.toString().trim()

            if (name.isNotEmpty()) {
                val updatedGroup = group.copy(name = name, color = selectedColor)
                updateGroupAndRefresh(updatedGroup)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "分组名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showDeleteUrlConfirmationDialog(searchUrl: SearchUrl, editDialog: Dialog? = null) {
        AlertDialog.Builder(context)
            .setTitle("删除确认")
            .setMessage("确定要删除 ${searchUrl.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.delete(searchUrl)
                editDialog?.dismiss()

                // 删除后自动退出编辑模式
                onExitEditMode()
                Toast.makeText(context, "已删除: ${searchUrl.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showDeleteGroupConfirmationDialog(group: UrlGroup, editDialog: Dialog? = null) {
        AlertDialog.Builder(context)
            .setTitle("删除确认")
            .setMessage("确定要删除分组 ${group.name} 及其包含的所有链接？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.deleteGroup(group)
                editDialog?.dismiss()

                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                    delay(150)
                    // 删除分组后也自动退出编辑模式
                    onExitEditMode()
                    Toast.makeText(context, "已删除分组: ${group.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupAppSelection(packageNameEditText: EditText, appNameTextView: TextView, onSelected: (String, String) -> Unit) {
        currentPackageNameEditText = packageNameEditText
        currentSelectedAppNameTextView = appNameTextView
        onAppSelectedGlobal = onSelected
    }

    private fun launchAppSelection() {
        appSelectionResultLauncher.launch(Intent(context, com.example.aggregatesearch.activities.AppSelectionActivity::class.java))
    }

    private fun applyThemeColor(dialogBinding: DialogEditUrlBinding) {
        val colorStr = (context as android.app.Activity).getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val color = colorStr.toColorInt()
        val colorStateList = ColorStateList.valueOf(color)

        dialogBinding.btnTestUrl.backgroundTintList = colorStateList
        dialogBinding.buttonDeleteUrlDialog.backgroundTintList = colorStateList
        dialogBinding.btnSelectApp.setTextColor(color)
        dialogBinding.btnCancelAppBinding.setTextColor(color)
    }

    private fun applyGroupEditThemeColor(dialogBinding: DialogEditGroupBinding) {
        val colorStr = (context as android.app.Activity).getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val color = colorStr.toColorInt()
        val colorStateList = ColorStateList.valueOf(color)

        dialogBinding.buttonSelectGroupColor.backgroundTintList = colorStateList
        dialogBinding.buttonDeleteGroupDialog.backgroundTintList = colorStateList
        dialogBinding.buttonSelectGroupColor.setTextColor(Color.WHITE)
        dialogBinding.buttonDeleteGroupDialog.setTextColor(Color.WHITE)
    }

    private fun setupIconSettings(dialogBinding: DialogEditUrlBinding, searchUrl: SearchUrl) {
        dialogBinding.radioButtonTextIcon.isChecked = searchUrl.useTextIcon
        dialogBinding.radioButtonAutoIcon.isChecked = !searchUrl.useTextIcon
        dialogBinding.layoutTextIcon.visibility = if (searchUrl.useTextIcon) View.VISIBLE else View.GONE

        val iconText = if (searchUrl.iconText.isNotBlank()) searchUrl.iconText else searchUrl.name
        dialogBinding.editTextIconText.setText(iconText)
        dialogBinding.viewIconBackgroundColor.setBackgroundColor(searchUrl.iconBackgroundColor)

        updateIconPreview(dialogBinding, searchUrl)

        dialogBinding.radioGroupIconType.setOnCheckedChangeListener { _, checkedId ->
            val useTextIcon = checkedId == R.id.radioButtonTextIcon
            dialogBinding.layoutTextIcon.visibility = if (useTextIcon) View.VISIBLE else View.GONE
            updateIconPreview(dialogBinding, searchUrl.copy(useTextIcon = useTextIcon))
        }

        dialogBinding.btnRefreshIcon.setOnClickListener {
            if (dialogBinding.radioButtonAutoIcon.isChecked) {
                Toast.makeText(context, "正在刷新图标...", Toast.LENGTH_SHORT).show()
                val tempUrl = searchUrl.copy()
                tempUrl.forceRefresh = true
                updateIconPreview(dialogBinding, tempUrl)
            }
        }

        dialogBinding.editTextIconText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (dialogBinding.radioButtonTextIcon.isChecked) {
                    val currentBgColor = (dialogBinding.viewIconBackgroundColor.background as? ColorDrawable)?.color ?: searchUrl.iconBackgroundColor
                    updateIconPreview(dialogBinding, searchUrl.copy(iconText = s.toString(), iconBackgroundColor = currentBgColor))
                }
            }
        })

        dialogBinding.viewIconBackgroundColor.setOnClickListener {
            showColorPicker(searchUrl.iconBackgroundColor) { color, _ ->
                dialogBinding.viewIconBackgroundColor.setBackgroundColor(color)
                if (dialogBinding.radioButtonTextIcon.isChecked) {
                    val currentIconText = dialogBinding.editTextIconText.text.toString()
                    updateIconPreview(dialogBinding, searchUrl.copy(iconText = currentIconText, iconBackgroundColor = color))
                }
            }
        }
    }

    private fun setupAppBindingInEditDialog(dialogBinding: DialogEditUrlBinding, initialPackageName: String, onPackageSelected: (String) -> Unit) {
        var selectedPackageName = initialPackageName
        dialogBinding.editTextPackageName.setText(selectedPackageName)

        if (selectedPackageName.isNotEmpty()) {
            try {
                val packageInfo = (context as android.app.Activity).packageManager.getApplicationInfo(selectedPackageName, 0)
                val appName = (context as android.app.Activity).packageManager.getApplicationLabel(packageInfo).toString()
                dialogBinding.textViewSelectedAppName.text = context.getString(R.string.selected_app_format, appName)
                dialogBinding.textViewSelectedAppName.visibility = View.VISIBLE
                dialogBinding.btnCancelAppBinding.visibility = View.VISIBLE
            } catch (e: Exception) {
                dialogBinding.textViewSelectedAppName.visibility = View.GONE
                dialogBinding.btnCancelAppBinding.visibility = View.GONE
            }
        }

        setupAppSelection(dialogBinding.editTextPackageName, dialogBinding.textViewSelectedAppName) { packageName, appName ->
            selectedPackageName = packageName
            onPackageSelected(packageName)
            if (packageName.isNotEmpty()) {
                dialogBinding.btnCancelAppBinding.visibility = View.VISIBLE
            }
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            launchAppSelection()
        }

        dialogBinding.btnCancelAppBinding.setOnClickListener {
            selectedPackageName = ""
            onPackageSelected("")
            dialogBinding.editTextPackageName.setText("")
            dialogBinding.textViewSelectedAppName.text = ""
            dialogBinding.textViewSelectedAppName.visibility = View.GONE
            dialogBinding.btnCancelAppBinding.visibility = View.GONE
            Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateIconPreview(dialogBinding: DialogEditUrlBinding, searchUrl: SearchUrl) {
        if (searchUrl.useTextIcon) {
            val text = dialogBinding.editTextIconText.text.toString().takeIf { it.isNotBlank() }
                ?: searchUrl.iconText.takeIf { it.isNotBlank() }
                ?: searchUrl.name

            val bgColor = (dialogBinding.viewIconBackgroundColor.background as? ColorDrawable)?.color
                ?: searchUrl.iconBackgroundColor

            val drawable = IconLoader.createTextIcon(context, text, bgColor)
            dialogBinding.imageViewIconPreview.setImageDrawable(drawable)
            dialogBinding.btnRefreshIcon.visibility = View.GONE
        } else {
            IconLoader.loadIcon(context, searchUrl, dialogBinding.imageViewIconPreview)
            dialogBinding.btnRefreshIcon.visibility = View.VISIBLE
        }
    }

    private fun showColorPicker(defaultColor: String, onColorSelected: (Int, String) -> Unit) {
        try {
            com.example.aggregatesearch.utils.ColorPickerDialog.Builder(context)
                .setTitle("选择颜色")
                .setColorShape(true)
                .setDefaultColor(defaultColor)
                .setColorListener { color, hexColor ->
                    onColorSelected(color, hexColor)
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(context, "打开颜色选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showColorPicker(defaultColor: Int, onColorSelected: (Int, String) -> Unit) {
        try {
            com.example.aggregatesearch.utils.ColorPickerDialog.Builder(context)
                .setTitle("选择图标颜色")
                .setColorShape(true)
                .setDefaultColor(defaultColor)
                .setColorListener { color, hexColor ->
                    onColorSelected(color, hexColor)
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(context, "打开颜色选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateGroupAndRefresh(group: UrlGroup) {
        searchViewModel.updateGroup(group)
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            delay(150)
            // 保存后自动退出编辑模式，让用户立即看到更新后的内容
            onExitEditMode()
        }
    }
}
