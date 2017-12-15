/*
 * Chromer
 * Copyright (C) 2017 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browsing.tabs

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.text.TextUtils
import android.widget.Toast
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.appdetect.AppDetectionManager
import arun.com.chromer.browsing.article.ArticleLauncher
import arun.com.chromer.browsing.article.ChromerArticleActivity
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.data.apps.DefaultAppRepository
import arun.com.chromer.data.website.DefaultWebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.isPackageInstalled
import arun.com.chromer.settings.Preferences
import arun.com.chromer.util.DocumentUtils
import arun.com.chromer.util.RxEventBus
import arun.com.chromer.util.SafeIntent
import arun.com.chromer.util.Utils
import arun.com.chromer.webheads.ui.ProxyActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTabsManager
@Inject
constructor(
        val application: Application,
        val preferences: Preferences,
        val appDetectionManager: AppDetectionManager,
        val appRepository: DefaultAppRepository,
        val websiteRepository: DefaultWebsiteRepository,
        val rxEventBus: RxEventBus
) : TabsManager {
    // Event for minimize command.
    data class MinimizeEvent(val url: String)

    override fun openUrl(context: Context, website: Website, fromApp: Boolean, fromWebHeads: Boolean) {
        // Open in web heads mode if we this command did not come from web heads.
        if (preferences.webHeads() && !fromWebHeads) {
            openWebHeads(context, website.preferredUrl())
            return
        }

        // Check if already an instance for this URL is there in our tasks
        if (reOrderTabByUrl(context, website)) {
            // Just bring it to front
            return
        }

        // Check if we should try to find AMP version of incoming url.
        if (preferences.ampMode()) {
            if (!TextUtils.isEmpty(website.ampUrl)) {
                // We already got the amp url, so open it in a browsing tab.
                openBrowsingTab(context, Uri.parse(website.ampUrl))
            } else {
                // Open a proxy activity, attempt an extraction then show.
            }
            return
        }

        if (preferences.articleMode()) {
            // Launch article mode
            openArticle(context, website.preferredUri())
            return
        }

        // At last, if everything failed then launch normally in browsing activity.
        openBrowsingTab(context, website.preferredUri())
    }

    override fun reOrderTabByUrl(context: Context, website: Website): Boolean {
        val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (Utils.isLollipopAbove()) {
            for (task in am.appTasks) {
                val info = DocumentUtils.getTaskInfoFromTask(task)
                info?.let {
                    try {
                        val intent = info.baseIntent
                        val url = intent.dataString!!
                        val componentClassName = intent.component!!.className
                        val taskComponentMatches = (componentClassName == CustomTabActivity::class.java.name
                                || componentClassName == ChromerArticleActivity::class.java.name
                                || componentClassName == WebViewActivity::class.java.name)

                        val urlMatches = (url.equals(website.url, ignoreCase = true)
                                || url.equals(website.preferredUrl(), ignoreCase = true)
                                || url.equals(website.ampUrl, ignoreCase = true))

                        if (taskComponentMatches && urlMatches) {
                            Timber.d("Moved tab to front %s", url)
                            task.moveToFront()
                            return true
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }
        }
        return false
    }

    override fun minimizeTabByUrl(url: String) {
        rxEventBus.post(MinimizeEvent(url))
    }

    override fun processIncomingIntent(activity: Activity, intent: Intent) {
        // Safety check against malicious intents
        val safeIntent = SafeIntent(intent)
        val url = safeIntent.dataString

        // The first thing to check is if we should blacklist.
        if (preferences.blacklist()) {
            val lastApp = appDetectionManager.nonFilteredPackage
            if (lastApp.isNotEmpty() && appRepository.isPackageBlacklisted(lastApp)) {
                doBlacklistAction(activity, safeIntent)
                return
            }
        }

        // Open url normally
        openUrl(activity, Website(url), fromApp = false, fromWebHeads = false)
    }

    private fun openArticle(context: Context, uri: Uri) {
        ArticleLauncher.from(context, uri)
                .applyCustomizations()
                .launch()
    }

    override fun openBrowsingTab(context: Context, uri: Uri, smart: Boolean) {
        if (!(smart && reOrderTabByUrl(context, Website(uri.toString())))) {
            val canSafelyOpenCCT = CustomTabs.getCustomTabSupportingPackages(context).isNotEmpty()
            val isIncognito = preferences.incognitoMode()

            val tabActivity: Intent
            if (!isIncognito && canSafelyOpenCCT) {
                tabActivity = Intent(context, CustomTabActivity::class.java).apply {
                    data = uri
                }
            } else {
                tabActivity = Intent(context, WebViewActivity::class.java).apply {
                    data = uri
                }
            }
            if (preferences.mergeTabs()) {
                tabActivity.addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
                tabActivity.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            if (context !is Activity) {
                tabActivity.addFlags(FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(tabActivity)
        }
    }

    override fun openWebHeads(context: Context, url: String) {
        if (Utils.isOverlayGranted(context)) {
            val webHeadLauncher = Intent(context, ProxyActivity::class.java)
            webHeadLauncher.addFlags(FLAG_ACTIVITY_NEW_TASK)
            /*if (!isFromNewTab && !isFromOurApp) {
                webHeadLauncher.addFlags(FLAG_ACTIVITY_CLEAR_TASK)
            }
            webHeadLauncher.putExtra(EXTRA_KEY_FROM_NEW_TAB, isFromNewTab)
            webHeadLauncher.putExtra(EXTRA_KEY_SKIP_EXTRACTION, skipExtraction)*/
            webHeadLauncher.data = Uri.parse(url)
            context.startActivity(webHeadLauncher)
        } else {
            Utils.openDrawOverlaySettings(context)
        }

        if (preferences.aggressiveLoading()) {
            // Project boom boom.
        }
    }


    /**
     * Performs the blacklist action which is opening the given url in user's secondary browser.
     */
    private fun doBlacklistAction(activity: Activity, safeIntent: SafeIntent) {
        // Perform a safe copy of this intent
        val intentCopy = Intent().apply {
            data = safeIntent.data
            safeIntent.unsafe.extras?.let {
                putExtras(it)
            }
        }
        val secondaryBrowser = preferences.secondaryBrowserPackage()
        if (secondaryBrowser == null) {
            showSecondaryBrowserHandlingError(activity, activity.getText(R.string.secondary_browser_not_error))
            return
        }
        if (activity.packageManager.isPackageInstalled(secondaryBrowser)) {
            intentCopy.`package` = secondaryBrowser
            try {
                activity.startActivity(intentCopy)
                if (BuildConfig.DEBUG) {
                    Toast.makeText(activity, "Blacklisted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showSecondaryBrowserHandlingError(activity, activity.getText(R.string.secondary_browser_launch_error))
            }
        } else {
            showSecondaryBrowserHandlingError(activity, activity.getText(R.string.secondary_browser_not_installed))
        }
    }

    /**
     * Shows a error dialog for various blacklist errors.
     */
    private fun showSecondaryBrowserHandlingError(activity: Activity, message: CharSequence) {
        MaterialDialog.Builder(activity)
                .title(R.string.secondary_browser_launching_error_title)
                .content(message)
                .iconRes(R.mipmap.ic_launcher)
                .positiveText(R.string.launch_setting)
                .negativeText(android.R.string.cancel)
                .theme(Theme.LIGHT)
                .positiveColorRes(R.color.colorAccent)
                .negativeColorRes(R.color.colorAccent)
                .onPositive { _, _ ->
                    val chromerIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                    chromerIntent!!.addFlags(FLAG_ACTIVITY_CLEAR_TOP)
                    activity.startActivity(chromerIntent)
                }
                .dismissListener({ activity.finish() }).show()
    }
}