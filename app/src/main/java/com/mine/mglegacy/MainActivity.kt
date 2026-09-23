package com.mine.mglegacy

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * MGLegacy settings screen.
 *
 * IMPORTANT / HONEST NOTE ON HOW THIS ACTUALLY WORKS:
 * FCL never opens this app or calls into its code. FCL only reads the static
 * <meta-data> in AndroidManifest.xml once, when it scans installed apps.
 * This screen writes a real MobileGlues config file (config.json) into the
 * folder named by the MG_DIR_PATH env var declared in the manifest.
 * MobileGlues (libmobileglues.so) reads that folder itself when the game
 * launches - so the flow is: open this app -> change settings -> tap Save
 * -> THEN launch Minecraft in FCL. Changes made after the game has already
 * started will not apply until the next launch.
 *
 * Every key written below (customGLVersion, fsr1Setting, maxGlslCacheSize,
 * enableAngle, enableExtComputeShader, enableExtDirectStateAccess,
 * enableExtTimerQuery, ignoreError) was found as a literal string inside
 * libmobileglues.so's own debug-log format strings
 * ("[MobileGlues] Setting: <key> = <value>"), so the KEYS are confirmed
 * real. What is NOT independently confirmed: the exact valid values for
 * customGLVersion, and the exact integer meaning of each fsr1Setting level
 * (the 0/1/2/3 mapping below is inferred from the option order MobileGlues
 * itself logs nearby - Off/Performance/Balanced/Quality - not verified
 * against source).
 */
class MainActivity : ComponentActivity() {

    private val candidateLaunchers = listOf(
        "com.tungsten.fcl",
        "com.tungsten.fcl.ngg",
        "com.tungsten.fcl.qualcommdr",
        "com.tungsten.fcm",
        "com.movtery.zalithlauncher.v2",
        "com.maxjubayeryt.copper",
        "net.kdt.pojavlaunch"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme {
                MgLegacyScreen(
                    candidates = candidateLaunchers,
                    findAngleDir = ::findAngleDir,
                    borrowAngleFrom = ::borrowAngleFrom,
                    angleReady = ::angleReady,
                    loadConfig = ::loadConfig,
                    saveConfig = ::saveConfig
                )
            }
        }
    }

    private fun mgDir(): File = File(filesDir, "mg").apply { mkdirs() }
    private fun angleDir(): File = File(filesDir, "angle")
    private fun configFile(): File = File(mgDir(), "config.json")

    private fun angleReady(): Boolean {
        val dir = angleDir()
        return File(dir, "libEGL_angle.so").exists() && File(dir, "libGLESv2_angle.so").exists()
    }

    private fun findAngleDir(pkg: String): File? = try {
        val info: ApplicationInfo = packageManager.getApplicationInfo(pkg, 0)
        val dir = info.nativeLibraryDir?.let { File(it) }
        if (dir != null && File(dir, "libEGL_angle.so").exists() && File(dir, "libGLESv2_angle.so").exists()) dir else null
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun borrowAngleFrom(sourceDir: File): Boolean {
        val destDir = angleDir().apply { mkdirs() }
        return copyFile(File(sourceDir, "libEGL_angle.so"), File(destDir, "libEGL_angle.so")) &&
                copyFile(File(sourceDir, "libGLESv2_angle.so"), File(destDir, "libGLESv2_angle.so"))
    }

    private fun copyFile(src: File, dst: File): Boolean = try {
        FileInputStream(src).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } }
        true
    } catch (e: Exception) {
        false
    }

    private fun loadConfig(): MgConfig {
        val f = configFile()
        if (!f.exists()) return MgConfig()
        return try {
            val j = JSONObject(f.readText())
            MgConfig(
                customGLVersion = j.optString("customGLVersion", ""),
                fsr1Setting = j.optInt("fsr1Setting", 0),
                maxGlslCacheSize = j.optInt("maxGlslCacheSize", 0),
                enableAngle = j.optBoolean("enableAngle", false),
                enableExtComputeShader = j.optBoolean("enableExtComputeShader", true),
                enableExtDirectStateAccess = j.optBoolean("enableExtDirectStateAccess", true),
                enableExtTimerQuery = j.optBoolean("enableExtTimerQuery", true),
                ignoreError = j.optInt("ignoreError", 0) == 1
            )
        } catch (e: Exception) {
            MgConfig()
        }
    }

    private fun saveConfig(c: MgConfig): Boolean = try {
        val j = JSONObject()
        if (c.customGLVersion.isNotBlank()) j.put("customGLVersion", c.customGLVersion)
        j.put("fsr1Setting", c.fsr1Setting)
        j.put("maxGlslCacheSize", c.maxGlslCacheSize)
        j.put("enableAngle", c.enableAngle)
        j.put("enableExtComputeShader", c.enableExtComputeShader)
        j.put("enableExtDirectStateAccess", c.enableExtDirectStateAccess)
        j.put("enableExtTimerQuery", c.enableExtTimerQuery)
        j.put("ignoreError", if (c.ignoreError) 1 else 0)
        configFile().writeText(j.toString(2))
        true
    } catch (e: Exception) {
        false
    }
}

data class MgConfig(
    val customGLVersion: String = "",
    val fsr1Setting: Int = 0,
    val maxGlslCacheSize: Int = 0,
    val enableAngle: Boolean = false,
    val enableExtComputeShader: Boolean = true,
    val enableExtDirectStateAccess: Boolean = true,
    val enableExtTimerQuery: Boolean = true,
    val ignoreError: Boolean = true
)

@Composable
private fun MgLegacyScreen(
    candidates: List<String>,
    findAngleDir: (String) -> File?,
    borrowAngleFrom: (File) -> Boolean,
    angleReady: () -> Boolean,
    loadConfig: () -> MgConfig,
    saveConfig: (MgConfig) -> Boolean
) {
    var config by remember { mutableStateOf(loadConfig()) }
    var tab by remember { mutableStateOf(0) }
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var angleMsg by remember {
        mutableStateOf(if (angleReady()) "ANGLE ready (already borrowed)" else "No ANGLE borrowed yet")
    }
    val found = remember { candidates.mapNotNull { pkg -> findAngleDir(pkg)?.let { pkg to it } } }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 0.dp)) {
                Text("MgLegacy alpha 0.1", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("SFPEW / MobileGlues settings", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Renderer") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Experimental") })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (tab == 0) {
                    RendererTab(
                        config = config,
                        onChange = { config = it },
                        candidates = found,
                        angleMsg = angleMsg,
                        onBorrow = { pkg, dir ->
                            angleMsg = if (borrowAngleFrom(dir)) "ANGLE borrowed from $pkg"
                            else "Could not copy files from $pkg"
                        }
                    )
                } else {
                    ExperimentalTab(config = config, onChange = { config = it })
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF9100))
                        .clickable {
                            saveMsg = if (saveConfig(config)) "Saved. Now launch Minecraft in FCL."
                            else "Could not save config.json"
                        }
                        .padding(16.dp)
                ) {
                    Text("Save settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                saveMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF2F2F7))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

@Composable
private fun RendererTab(
    config: MgConfig,
    onChange: (MgConfig) -> Unit,
    candidates: List<Pair<String, File>>,
    angleMsg: String,
    onBorrow: (String, File) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Text("Custom GL version", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Confirmed real setting (customGLVersion). Leave blank for MobileGlues' default. Exact accepted value format is not confirmed - try something like \"4.6\" if you experiment.",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            SimpleTextField(
                value = config.customGLVersion,
                placeholder = "(default)",
                onValue = { onChange(config.copy(customGLVersion = it)) }
            )
        }

        SectionCard {
            Text("FSR upscaling", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Confirmed real AMD FSR1 support (fsr1Setting). Level-to-number mapping below is inferred, not verified.",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Off" to 0, "Perf." to 1, "Bal." to 2, "Quality" to 3).forEach { (label, value) ->
                    Chip(label, selected = config.fsr1Setting == value) {
                        onChange(config.copy(fsr1Setting = value))
                    }
                }
            }
        }

        SectionCard {
            Text("Shader cache", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Confirmed real setting (maxGlslCacheSize). Unit (entries vs MB) is not confirmed - 0 likely means default/unlimited.",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            SimpleTextField(
                value = if (config.maxGlslCacheSize == 0) "" else config.maxGlslCacheSize.toString(),
                placeholder = "0 (default)",
                onValue = { v -> onChange(config.copy(maxGlslCacheSize = v.toIntOrNull() ?: 0)) },
                numeric = true
            )
        }

        SectionCard {
            Text("ANGLE", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ToggleRow("Enable ANGLE (enableAngle)", config.enableAngle) {
                onChange(config.copy(enableAngle = it))
            }
            Spacer(Modifier.height(8.dp))
            Text(angleMsg, fontSize = 12.sp, color = Color(0xFFB25400))
            Spacer(Modifier.height(8.dp))
            Text("Borrow ANGLE files from an installed launcher:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            if (candidates.isEmpty()) {
                Text("None found. Install/keep FCL, Zalith, Copper, or PojavLauncher.", fontSize = 11.sp, color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidates.forEach { (pkg, dir) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .clickable { onBorrow(pkg, dir) }
                                .padding(10.dp)
                        ) {
                            Text(pkg, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentalTab(config: MgConfig, onChange: (MgConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Text("Ignore GL errors", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Confirmed real setting (ignoreError). This is the most likely real fix for a crash caused by legacy texture-format calls 1.16.5 makes that this renderer rejects. Defaults ON here for that reason.",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow("ignoreError", config.ignoreError) { onChange(config.copy(ignoreError = it)) }
        }

        SectionCard {
            Text("GL extensions", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "All three keys below are confirmed real. Turning one off if something breaks helps narrow down which extension a mod or shader depends on.",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            ToggleRow("Compute shaders (enableExtComputeShader)", config.enableExtComputeShader) {
                onChange(config.copy(enableExtComputeShader = it))
            }
            ToggleRow("Direct state access (enableExtDirectStateAccess)", config.enableExtDirectStateAccess) {
                onChange(config.copy(enableExtDirectStateAccess = it))
            }
            ToggleRow("Timer query (enableExtTimerQuery)", config.enableExtTimerQuery) {
                onChange(config.copy(enableExtTimerQuery = it))
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFFF9100) else Color(0xFFE5E5EA))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Color.White else Color.Black)
    }
}

@Composable
private fun SimpleTextField(value: String, placeholder: String, onValue: (String) -> Unit, numeric: Boolean = false) {
    // Minimal text field using BasicTextField would need extra imports; kept as
    // a plain Material3 approach for reliability.
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onValue(it) },
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
    )
}
