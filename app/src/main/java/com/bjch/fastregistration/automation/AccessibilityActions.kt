package com.bjch.fastregistration.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityActions(
    private val service: AccessibilityService,
    private val logger: PerformanceLogger
) {
    fun findExactText(tree: NodeTree, text: String): List<TreeNode> = tree.exact(text)

    fun findContainsText(tree: NodeTree, text: String): List<TreeNode> = tree.containing(text)

    fun findClickableParent(node: AccessibilityNodeInfo, maximumLevels: Int = 6): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(maximumLevels + 1) {
            val candidate = current ?: return null
            if (candidate.isEnabled && candidate.isClickable) return candidate
            current = candidate.parent
        }
        return null
    }

    fun clickText(
        tree: NodeTree,
        text: String,
        exact: Boolean = true,
        selector: (TreeNode) -> Boolean = { true }
    ): Boolean {
        val nodes = (if (exact) findExactText(tree, text) else findContainsText(tree, text))
            .filter { it.enabled && it.visible && selector(it) }
        val target = nodes.minByOrNull { area(it.bounds) }
        return click(target)
    }

    fun click(target: TreeNode?): Boolean {
        if (target != null) {
            val sourceNode = target.node
            val clickable = sourceNode?.let(::findClickableParent)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            if (sourceNode?.isEnabled == true && sourceNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            if (!target.bounds.isEmpty && tap(target.bounds.exactCenterX(), target.bounds.exactCenterY())) return true
        }
        return false
    }

    /** Click a visual control at its exact recognized position before trying node ancestors. */
    fun clickPrecise(target: TreeNode?): Boolean {
        if (target == null) return false
        if (!target.bounds.isEmpty && tap(target.bounds.exactCenterX(), target.bounds.exactCenterY())) {
            return true
        }
        return click(target)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 45L))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun pullDownMiniPrograms(tree: NodeTree): Boolean {
        val x = tree.screenWidth * 0.50f
        val path = Path().apply {
            moveTo(x, tree.screenHeight * 0.18f)
            lineTo(x, tree.screenHeight * 0.72f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 560L))
            .build()
        logger.info("Pulling down the WeChat recent mini-program drawer")
        return service.dispatchGesture(gesture, null, null)
    }

    fun swipeUp(tree: NodeTree): Boolean {
        val x = tree.screenWidth * 0.82f
        val path = Path().apply {
            moveTo(x, tree.screenHeight * 0.82f)
            lineTo(x, tree.screenHeight * 0.43f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 260L))
            .build()
        logger.info("Scrolling doctor cards to find configured doctor")
        return service.dispatchGesture(gesture, null, null)
    }

    fun swipeDown(tree: NodeTree): Boolean {
        val x = tree.screenWidth * 0.82f
        val path = Path().apply {
            moveTo(x, tree.screenHeight * 0.43f)
            lineTo(x, tree.screenHeight * 0.82f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 260L))
            .build()
        logger.info("Returning to the date grid before the next grab scan")
        return service.dispatchGesture(gesture, null, null)
    }

    private fun area(bounds: Rect): Long = bounds.width().toLong() * bounds.height().toLong()
}

