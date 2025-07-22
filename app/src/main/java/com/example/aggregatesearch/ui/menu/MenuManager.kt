/**
 * 菜单管理器
 * 负责处理主Activity的菜单相关功能
 * 包括：菜单创建、菜单项点击处理、编辑模式切换、设置界面跳转等
 */
package com.example.aggregatesearch.ui.menu

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.example.aggregatesearch.R
import com.example.aggregatesearch.SettingsActivity

class MenuManager(
    private val context: Context,
    private val onEditModeToggle: () -> Unit,
    private val onPinCurrentSearch: () -> Unit,
    private val onShowAddDialog: () -> Unit,
    private val onShowHelpDialog: () -> Unit
) {

    private var menuEditMode: MenuItem? = null
    private var menuPlus: MenuItem? = null
    private var isEditMode = false

    fun onCreateOptionsMenu(menu: Menu): Boolean {
        (context as android.app.Activity).menuInflater.inflate(R.menu.main_menu, menu)
        menuEditMode = menu.findItem(R.id.menu_edit_mode)
        menuPlus = menu.findItem(R.id.menu_plus)
        // 初始化时，添加按钮不可见
        menuPlus?.isVisible = isEditMode
        return true
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit_mode -> {
                // 切换编辑模式
                toggleEditMode()
                true
            }
            R.id.menu_pin -> {
                onPinCurrentSearch()
                true
            }
            R.id.menu_plus -> {
                onShowAddDialog()
                true
            }
            R.id.menu_settings -> {
                // 打开设置界面
                context.startActivity(Intent(context, SettingsActivity::class.java))
                true
            }
            R.id.menu_help -> {
                // 显示使用说明
                onShowHelpDialog()
                true
            }
            else -> false
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        updateEditModeUI()
        onEditModeToggle()
    }

    private fun updateEditModeUI() {
        // 更新编辑按钮图标
        menuEditMode?.icon = androidx.core.content.ContextCompat.getDrawable(
            context,
            if (isEditMode) R.drawable.ic_close else R.drawable.ic_edit
        )

        // 根据编辑模式控制添加按钮的可见性
        menuPlus?.isVisible = isEditMode
    }

    fun getEditMode(): Boolean {
        return isEditMode
    }

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            updateEditModeUI()
        }
    }

    /**
     * 强制退出编辑模式
     */
    fun forceExitEditMode() {
        if (isEditMode) {
            isEditMode = false
            updateEditModeUI()
        }
    }

    /**
     * 创建帮助对话框内容
     */
    fun createHelpDialog(): AlertDialog {
        val dialogView = (context as android.app.Activity).layoutInflater.inflate(R.layout.dialog_help, null)
        return AlertDialog.Builder(context)
            .setTitle("使用说明")
            .setView(dialogView)
            .setPositiveButton("知道了", null)
            .create()
    }

    /**
     * 更新菜单状态
     */
    fun updateMenuState() {
        updateEditModeUI()
    }
}
