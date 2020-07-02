package dk.sdu.cloud.project.utils

import dk.sdu.cloud.project.api.ProjectRole

fun userRoleChangeTemplate(
    recipient: String,
    subjectToChange:String,
    roleChange: ProjectRole,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that the user: $subjectToChange, has had their role changed to: ${roleChange.name}
    in the project: $projectTitle.
    
"""
