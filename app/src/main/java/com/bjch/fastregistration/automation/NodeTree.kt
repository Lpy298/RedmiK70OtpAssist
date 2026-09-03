package com.bjch.fastregistration.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

data class TreeNode(
    val index: Int,
    val parentIndex: Int,
    val depth: Int,
    val node: AccessibilityNodeInfo?,
    val className: String,
    val text: String,
    val hintText: String,
    val contentDescription: String,
    val viewId: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val visible: Boolean
) {
    val combinedText: String
        get() = listOf(text, hintText, contentDescription)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

data class TextRegion(
    val parentIndex: Int,
    val depth: Int,
    val className: String,
    val text: String,
    val bounds: Rect
)

class NodeTree private constructor(
    val nodes: List<TreeNode>,
    val screenWidth: Int,
    val screenHeight: Int,
    val sourceWindow: String,
    val selectionScore: Int,
    val windowCandidates: String
) {
    val allText: String by lazy {
        nodes.asSequence().map { it.combinedText }.filter { it.isNotBlank() }.joinToString("\n")
    }

    fun exact(text: String): List<TreeNode> = nodes.filter {
        normalize(it.text) == normalize(text) || normalize(it.contentDescription) == normalize(text)
    }

    fun containing(text: String): List<TreeNode> = nodes.filter {
        normalize(it.combinedText).contains(normalize(text))
    }

    fun hasExact(text: String): Boolean = exact(text).isNotEmpty()

    fun has(text: String): Boolean = containing(text).isNotEmpty()

    fun parentOf(node: TreeNode): TreeNode? = nodes.getOrNull(node.parentIndex)

    fun ancestors(node: TreeNode, maximum: Int = 6): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        var current = node
        repeat(maximum) {
            val parent = parentOf(current) ?: return result
            result += parent
            current = parent
        }
        return result
    }

    fun subtreeText(rootIndex: Int): String {
        return nodes.asSequence()
            .filter { it.index == rootIndex || isDescendantOf(it, rootIndex) }
            .map { it.combinedText }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun isDescendantOf(node: TreeNode, ancestorIndex: Int): Boolean {
        var parent = node.parentIndex
        var guard = 0
        while (parent >= 0 && guard++ < 20) {
            if (parent == ancestorIndex) return true
            parent = nodes.getOrNull(parent)?.parentIndex ?: -1
        }
        return false
    }

    fun withWindowSelection(score: Int, candidates: String): NodeTree = NodeTree(
        nodes = nodes,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        sourceWindow = sourceWindow,
        selectionScore = score,
        windowCandidates = candidates
    )

    companion object {
        fun from(
            root: AccessibilityNodeInfo,
            width: Int,
            height: Int,
            sourceWindow: String = "rootInActiveWindow"
        ): NodeTree {
            data class Pending(val node: AccessibilityNodeInfo, val parentIndex: Int, val depth: Int)

            val records = mutableListOf<TreeNode>()
            val queue = ArrayDeque<Pending>()
            queue.add(Pending(root, -1, 0))

            while (queue.isNotEmpty() && records.size < 10_000) {
                val pending = queue.removeFirst()
                val node = pending.node
                val index = records.size
                val bounds = Rect().also(node::getBoundsInScreen)
                records += TreeNode(
                    index = index,
                    parentIndex = pending.parentIndex,
                    depth = pending.depth,
                    node = node,
                    className = node.className?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty(),
                    hintText = node.hintText?.toString().orEmpty(),
                    contentDescription = node.contentDescription?.toString().orEmpty(),
                    viewId = node.viewIdResourceName.orEmpty(),
                    bounds = bounds,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    enabled = node.isEnabled,
                    selected = node.isSelected,
                    visible = node.isVisibleToUser
                )
                for (childIndex in 0 until node.childCount) {
                    node.getChild(childIndex)?.let { child ->
                        queue.add(Pending(child, index, pending.depth + 1))
                    }
                }
            }
            return NodeTree(
                nodes = records,
                screenWidth = width,
                screenHeight = height,
                sourceWindow = sourceWindow,
                selectionScore = 0,
                windowCandidates = ""
            )
        }

        fun fromTextRegions(
            regions: List<TextRegion>,
            width: Int,
            height: Int,
            sourceWindow: String = "SCREEN_OCR"
        ): NodeTree {
            val records = regions.mapIndexed { index, region ->
                TreeNode(
                    index = index,
                    parentIndex = region.parentIndex,
                    depth = region.depth,
                    node = null,
                    className = region.className,
                    text = region.text,
                    hintText = "",
                    contentDescription = "",
                    viewId = "",
                    bounds = Rect(region.bounds),
                    clickable = false,
                    editable = false,
                    enabled = true,
                    selected = false,
                    visible = !region.bounds.isEmpty
                )
            }
            return NodeTree(
                nodes = records,
                screenWidth = width,
                screenHeight = height,
                sourceWindow = sourceWindow,
                selectionScore = records.size,
                windowCandidates = "on-device Chinese OCR"
            )
        }

        fun normalize(value: String): String = value
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), "")
            .replace('～', '~')
            .replace('—', '-')
            .replace('–', '-')
            .trim()
    }
}

