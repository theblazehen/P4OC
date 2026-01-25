package dev.blazelight.p4oc.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectColorsTest {

    @Test
    fun `colorForProject returns same color for same project ID`() {
        val projectId = "project-123"
        
        val color1 = ProjectColors.colorForProject(projectId)
        val color2 = ProjectColors.colorForProject(projectId)
        
        assertEquals(color1, color2)
    }

    @Test
    fun `colorForProject is deterministic across multiple calls`() {
        val projectIds = listOf(
            "my-app",
            "backend-api", 
            "mobile-client",
            "shared-lib",
            "test-project"
        )
        
        // Get colors twice for each project
        val firstPass = projectIds.map { ProjectColors.colorForProject(it) }
        val secondPass = projectIds.map { ProjectColors.colorForProject(it) }
        
        assertEquals(firstPass, secondPass)
    }

    @Test
    fun `colorForProject handles empty string`() {
        // Should not throw
        val color = ProjectColors.colorForProject("")
        assertEquals(color, ProjectColors.colorForProject(""))
    }

    @Test
    fun `colorForProject handles special characters`() {
        val projectId = "my-project/with:special@chars"
        
        val color1 = ProjectColors.colorForProject(projectId)
        val color2 = ProjectColors.colorForProject(projectId)
        
        assertEquals(color1, color2)
    }
}
