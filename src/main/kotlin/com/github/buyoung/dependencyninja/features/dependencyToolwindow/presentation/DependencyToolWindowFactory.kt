package com.github.buyoung.dependencyninja.features.dependencyToolwindow.presentation

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class DependencyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DependencyToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class DependencyToolWindowPanel(project: Project) : JPanel(BorderLayout()) {
    private val service = project.service<DependencyNinjaProjectService>()
    private val rootNode = DefaultMutableTreeNode("Dependencies")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = JTree(treeModel)

    init {
        service.addSnapshotListener { refreshTree() }

        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener {
            service.refreshInBackground(showNotification = true)
        }

        add(refreshButton, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        refreshTree()
    }

    private fun refreshTree() {
        rootNode.removeAllChildren()
        val snapshot = service.snapshot()
        val groupedByModule = snapshot.updates.groupBy { it.declared.moduleName }

        groupedByModule.toSortedMap().forEach { (module, moduleUpdates) ->
            val moduleNode = DefaultMutableTreeNode(module)
            val byEcosystem = moduleUpdates.groupBy { it.declared.coordinate.ecosystem }
            byEcosystem.toSortedMap(compareBy { it.name }).forEach { (ecosystem, ecosystemUpdates) ->
                val ecosystemNode = DefaultMutableTreeNode(ecosystem.name)
                ecosystemUpdates.sortedBy { it.declared.coordinate.displayName() }.forEach { update ->
                    val marker = when (update.status) {
                        DependencyStatus.OUTDATED -> "OUTDATED"
                        DependencyStatus.UP_TO_DATE -> "UP-TO-DATE"
                        DependencyStatus.UNKNOWN -> "UNKNOWN"
                    }
                    val label = buildString {
                        append(update.declared.coordinate.displayName())
                        append("  ")
                        append(update.declared.currentVersion)
                        append(" -> ")
                        append(update.latestVersion ?: "?")
                        append(" [")
                        append(marker)
                        append(']')
                    }
                    ecosystemNode.add(DefaultMutableTreeNode(label))
                }
                moduleNode.add(ecosystemNode)
            }
            rootNode.add(moduleNode)
        }

        treeModel.reload()
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }
}
