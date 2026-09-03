package com.bjch.fastregistration.automation

internal enum class DepartmentNavigationStep {
    SELECT_CATEGORY,
    WAIT_FOR_DEPARTMENT_LIST,
    SELECT_DEPARTMENT
}

internal object DepartmentNavigationGate {
    fun next(categoryClicked: Boolean, targetDepartmentVisible: Boolean): DepartmentNavigationStep = when {
        !categoryClicked -> DepartmentNavigationStep.SELECT_CATEGORY
        !targetDepartmentVisible -> DepartmentNavigationStep.WAIT_FOR_DEPARTMENT_LIST
        else -> DepartmentNavigationStep.SELECT_DEPARTMENT
    }
}

