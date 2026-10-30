package com.magicsmart.core.engine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.magicsmart.core.ResultStatus

/** يفتح صفحة بمبادرة المستخدم فقط، ويصنّف الأخطاء. فتح الصفحة ليس تفعيلاً لأي إعداد. */
object Launcher {
    fun launch(context: Context, spec: LaunchSpec): TestOutcome {
        val intent = Intent().apply {
            spec.action?.let { action = it }
            spec.component?.let { component = it }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED,
                "فُتحت الصفحة. لم يُغيَّر أي إعداد؛ التفعيل يتم يدوياً منك."
            )
        } catch (e: ActivityNotFoundException) {
            TestOutcome(ResultStatus.UNSUPPORTED, "ActivityNotFoundException: لا يوجد نشاط لهذا المسار")
        } catch (e: SecurityException) {
            TestOutcome(ResultStatus.BLOCKED, "SecurityException: النظام منع فتح الصفحة")
        } catch (e: Exception) {
            TestOutcome(ResultStatus.FAILED, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
