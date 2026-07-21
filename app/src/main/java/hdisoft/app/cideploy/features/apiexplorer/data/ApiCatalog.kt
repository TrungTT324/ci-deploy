package hdisoft.app.cideploy.features.apiexplorer.data

/**
 * Static list of ci-backend REST endpoints, mirroring ci-backend/internal/api/router.go.
 * Only JSON-in/JSON-out routes are listed — multipart artifact upload and binary
 * artifact/OTA-apk downloads are excluded since this screen only renders text responses.
 */
object ApiCatalog {

    val all: List<ApiEndpoint> = listOf(
        ApiEndpoint("healthz", "Health check", "GET", "/healthz"),
        ApiEndpoint(
            "ota-version", "OTA latest version", "GET",
            "/ota/{projectCode}/ci-deploy-version.json"
        ),
        ApiEndpoint(
            "device-checkin", "Device checkin", "POST",
            "/api/v1/devices/checkin",
            """{"deviceUid":"test-device-uid","companyCode":"xsofts","projectCode":"ci-deploy","model":"Pixel 6","osVersion":"14","currentVersion":"1.0.1"}"""
        ),

        ApiEndpoint(
            "company-create", "Create company", "POST",
            "/api/v1/companies",
            """{"name":"xSofts","code":"xsofts"}"""
        ),
        ApiEndpoint("company-list", "List companies", "GET", "/api/v1/companies"),

        ApiEndpoint(
            "project-create", "Create project", "POST",
            "/api/v1/companies/{companyId}/projects",
            """{"name":"CI-Deploy Android","code":"ci-deploy","platform":"android","repoUrl":"","description":""}"""
        ),
        ApiEndpoint("project-list", "List projects", "GET", "/api/v1/companies/{companyId}/projects"),
        ApiEndpoint("project-get", "Get project", "GET", "/api/v1/projects/{projectId}"),
        ApiEndpoint(
            "project-update", "Update project", "PATCH",
            "/api/v1/projects/{projectId}",
            """{"description":"","repoUrl":"","platform":"android"}"""
        ),

        ApiEndpoint("environment-list", "List environments", "GET", "/api/v1/projects/{projectId}/environments"),
        ApiEndpoint(
            "environment-create", "Create environment", "POST",
            "/api/v1/projects/{projectId}/environments",
            """{"name":"staging","sortOrder":4}"""
        ),
        ApiEndpoint(
            "environment-update", "Update environment", "PATCH",
            "/api/v1/projects/{projectId}/environments/{envId}",
            """{"name":"qa","sortOrder":2}"""
        ),
        ApiEndpoint(
            "environment-delete", "Delete environment", "DELETE",
            "/api/v1/projects/{projectId}/environments/{envId}"
        ),
        ApiEndpoint(
            "environment-current", "Current deployment for environment", "GET",
            "/api/v1/projects/{projectId}/environments/{envId}/current"
        ),

        ApiEndpoint(
            "release-create", "Create release", "POST",
            "/api/v1/projects/{projectId}/releases",
            """{"version":"1.0.2","buildNo":202607130000,"releaseNote":"","sourceType":"commit","sourceUrl":"","sourceRef":"","createdBy":"CI"}"""
        ),
        ApiEndpoint("release-list", "List releases", "GET", "/api/v1/projects/{projectId}/releases"),
        ApiEndpoint("release-get", "Get release detail", "GET", "/api/v1/releases/{releaseId}"),

        ApiEndpoint(
            "issue-create", "Add ticket to release", "POST",
            "/api/v1/releases/{releaseId}/issues",
            """{"provider":"github_pr","url":"https://github.com/x/x/pull/42","refKey":"#42","title":"Auto Wi-Fi connect","issueType":"feature"}"""
        ),
        ApiEndpoint("issue-list", "List release tickets", "GET", "/api/v1/releases/{releaseId}/issues"),
        ApiEndpoint("issue-delete", "Remove ticket", "DELETE", "/api/v1/releases/{releaseId}/issues/{issueId}"),

        ApiEndpoint(
            "deployment-create", "Record deployment", "POST",
            "/api/v1/releases/{releaseId}/deployments",
            """{"environmentId":1,"status":"deployed","deployedBy":"CI","deployedNote":"","targetUrl":""}"""
        ),
        ApiEndpoint("deployment-list", "List deployments", "GET", "/api/v1/releases/{releaseId}/deployments"),

        ApiEndpoint(
            "test-create", "Record QA test", "POST",
            "/api/v1/releases/{releaseId}/tests",
            """{"deviceId":1,"environmentId":1,"status":"pass","testedBy":"trungtt","notes":"OK"}"""
        ),
        ApiEndpoint("test-list-by-release", "List tests for release", "GET", "/api/v1/releases/{releaseId}/tests"),
        ApiEndpoint("test-list-by-device", "List tests for device", "GET", "/api/v1/devices/{deviceId}/tests"),

        ApiEndpoint(
            "device-create", "Add device manually", "POST",
            "/api/v1/companies/{companyId}/devices",
            """{"name":"Galaxy A52 (QA-02)","model":"SM-A525F","osVersion":"13"}"""
        ),
        ApiEndpoint("device-list", "List company devices", "GET", "/api/v1/companies/{companyId}/devices"),
        ApiEndpoint("device-get", "Get device detail", "GET", "/api/v1/devices/{deviceId}"),

        ApiEndpoint(
            "token-create", "Create API token", "POST",
            "/api/v1/companies/{companyId}/tokens",
            """{"label":"CI token"}"""
        ),
        ApiEndpoint("token-list", "List company tokens", "GET", "/api/v1/companies/{companyId}/tokens"),
        ApiEndpoint("token-revoke", "Revoke token", "DELETE", "/api/v1/tokens/{tokenId}")
    )

    fun find(id: String): ApiEndpoint? = all.firstOrNull { it.id == id }
}
