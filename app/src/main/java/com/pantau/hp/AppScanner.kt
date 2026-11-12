package com.pantau.hp

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

// Pemindai heuristik (bukan database virus): menilai risiko dari izin, sumber
// pemasangan, layanan sensitif yang aktif, dan file APK di penyimpanan.
object AppScanner {
    data class Finding(
        val label: String,
        val pkg: String,
        val path: String,
        val score: Int,
        val reasons: List<String>
    )

    class Result(val apps: Int, val files: Int, val filesScanned: Boolean, val findings: List<Finding>)

    private val trusted = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket",
        "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market", "com.vivo.appstore",
        "com.amazon.venezia", "com.google.android.feedback"
    )
    private val disguise = Regex(".*\\.(jpg|jpeg|png|pdf|doc|docx|xls|xlsx|mp4|mp3|txt|zip)\\.apk$")

    fun canScanFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun permSignals(perms: Set<String>): List<Pair<Int, String>> {
        val l = ArrayList<Pair<Int, String>>()
        fun has(p: String) = perms.contains("android.permission.$p")
        if (has("SYSTEM_ALERT_WINDOW")) l.add(2 to "Bisa menimpa layar aplikasi lain")
        if (has("READ_SMS") || has("RECEIVE_SMS")) l.add(2 to "Bisa membaca SMS")
        if (has("SEND_SMS")) l.add(2 to "Bisa mengirim SMS")
        if (has("REQUEST_INSTALL_PACKAGES")) l.add(2 to "Bisa memasang APK lain")
        if (has("READ_CALL_LOG")) l.add(1 to "Membaca riwayat panggilan")
        if (has("RECORD_AUDIO")) l.add(1 to "Bisa merekam suara")
        if (has("CAMERA")) l.add(1 to "Bisa memakai kamera")
        if (has("READ_CONTACTS")) l.add(1 to "Membaca kontak")
        if (has("ACCESS_BACKGROUND_LOCATION")) l.add(1 to "Lokasi di latar belakang")
        if (has("MANAGE_EXTERNAL_STORAGE")) l.add(1 to "Akses ke semua file")
        if (has("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) l.add(1 to "Minta lolos penghemat baterai")
        return l
    }

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    private fun walk(dir: File, depth: Int, out: MutableList<File>, cnt: IntArray) {
        if (depth > 8 || cnt[0] > 60000) return
        val fs: Array<File>? = try { dir.listFiles() } catch (e: Exception) { null }
        if (fs == null) return
        for (f in fs) {
            cnt[0]++
            if (f.isDirectory) {
                if (!(depth == 0 && f.name == "Android")) walk(f, depth + 1, out, cnt)
            } else if (f.name.endsWith(".apk", true)) {
                out.add(f)
            }
        }
    }

    fun scan(ctx: Context): Result {
        val pm = ctx.packageManager
        val findings = ArrayList<Finding>()

        val acc = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: ""
        val nl = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins = try {
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet<String>()
        }

        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        var apps = 0
        for (p in pkgs) {
            if (p.packageName == ctx.packageName) continue
            val ai = p.applicationInfo ?: continue
            apps++
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val installer = installerOf(pm, p.packageName)
            val ok = system || (installer != null && installer in trusted)

            val sig = ArrayList<Pair<Int, String>>()
            if (!ok) sig.add(2 to "Dipasang dari luar toko resmi")
            sig.addAll(permSignals(p.requestedPermissions?.toSet() ?: emptySet()))
            if (acc.contains(p.packageName + "/")) sig.add(4 to "Layanan aksesibilitas aktif")
            if (nl.contains(p.packageName + "/")) sig.add(3 to "Bisa membaca semua notifikasi")
            if (admins.contains(p.packageName)) sig.add(4 to "Admin perangkat aktif")
            if (!system && !ok &&
                (p.packageName.startsWith("com.android.") || p.packageName.startsWith("com.google.android."))
            ) sig.add(3 to "Nama paket meniru aplikasi sistem")
            if (!system && pm.getLaunchIntentForPackage(p.packageName) == null) sig.add(1 to "Tanpa ikon peluncur")

            var score = 0
            val reasons = ArrayList<String>()
            for ((w, r) in sig) {
                val v = if (ok) (if (w >= 2) w / 2 else 0) else w
                if (v > 0) { score += v; reasons.add(r) }
            }
            if (score >= 3) {
                val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { p.packageName }
                findings.add(Finding(label, p.packageName, "", score, reasons))
            }
        }

        var files = 0
        var scanned = false
        if (canScanFiles(ctx)) {
            scanned = true
            val out = ArrayList<File>()
            val cnt = IntArray(1)
            walk(Environment.getExternalStorageDirectory(), 0, out, cnt)
            files = cnt[0]
            for (f in out) {
                val info = try {
                    pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_PERMISSIONS)
                } catch (e: Exception) {
                    null
                }
                val sig = ArrayList<Pair<Int, String>>()
                if (info == null) sig.add(1 to "File APK rusak atau tidak valid")
                else sig.addAll(permSignals(info.requestedPermissions?.toSet() ?: emptySet()))
                if (disguise.matches(f.name.lowercase())) sig.add(3 to "Nama file menyamar sebagai dokumen/media")
                val score = sig.sumOf { it.first }
                if (score >= 3) findings.add(Finding("File APK: " + f.name, "", f.absolutePath, score, sig.map { it.second }))
            }
        }

        findings.sortByDescending { it.score }
        return Result(apps, files, scanned, findings)
    }
}
