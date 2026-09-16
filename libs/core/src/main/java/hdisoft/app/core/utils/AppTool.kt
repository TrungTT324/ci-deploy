package hdisoft.app.core.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import java.util.Locale

object AppTool {
    private const val TAG = "AppTool"

    /**
     * Launch an app based on a query parameter which can be the app name,
     * package name, or a case-insensitive substring of either.
     *
     * @param context The application context.
     * @param query The app name, package name, or search query.
     * @return True if a matching app was found and launched, false otherwise.
     */
    fun openApp(context: Context, query: String): Boolean {
        Log.i(TAG, "Attempting to open app with query: '$query'")
        val pm = context.packageManager
        
        // 1. Direct try: check if query is a valid package name
        try {
            val intent = pm.getLaunchIntentForPackage(query)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Successfully opened app by exact package name match: '$query'")
                return true
            }
        } catch (e: Exception) {
            // Ignore and fall back to query search
        }

        // 2. Search try: query launcher intents and search
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val launchables = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query launcher activities: ${e.message}")
            emptyList<ResolveInfo>()
        }

        val queryLower = query.trim().lowercase(Locale.ROOT)
        if (queryLower.isEmpty()) {
            Log.w(TAG, "Query is empty, ignoring.")
            return false
        }

        var bestMatch: ResolveInfo? = null
        var matchType = 99 // Lower number means stronger match priority
        var matchedName = ""
        var matchedPkg = ""

        for (resolveInfo in launchables) {
            val pkg = resolveInfo.activityInfo.packageName
            val appLabel = resolveInfo.loadLabel(pm).toString()
            val labelLower = appLabel.lowercase(Locale.ROOT)
            val pkgLower = pkg.lowercase(Locale.ROOT)

            if (labelLower == queryLower) {
                bestMatch = resolveInfo
                matchType = 1
                matchedName = appLabel
                matchedPkg = pkg
                break // Exact app name match is the absolute best match
            } else if (pkgLower == queryLower) {
                if (matchType > 2) {
                    bestMatch = resolveInfo
                    matchType = 2
                    matchedName = appLabel
                    matchedPkg = pkg
                }
            } else if (labelLower.contains(queryLower)) {
                if (matchType > 3) {
                    bestMatch = resolveInfo
                    matchType = 3
                    matchedName = appLabel
                    matchedPkg = pkg
                }
            } else if (pkgLower.contains(queryLower)) {
                if (matchType > 4) {
                    bestMatch = resolveInfo
                    matchType = 4
                    matchedName = appLabel
                    matchedPkg = pkg
                }
            }
        }

        if (bestMatch != null) {
            try {
                val actInfo = bestMatch.activityInfo
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(actInfo.packageName, actInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
                Log.i(TAG, "Successfully opened app '$matchedName' ($matchedPkg) using match type $matchType.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start activity for match: ${e.message}")
            }
        }

        Log.e(TAG, "No app matching '$query' found.")
        return false
    }
}
