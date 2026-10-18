package com.veltrix.vpn.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.veltrix.vpn.BuildConfig
import com.veltrix.vpn.bg.Status
import com.veltrix.vpn.data.Store

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) vm.start()
        else toast(t("VPN permission is required to connect.", "Za spajanje treba dozvola za VPN."))
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val scan = registerForActivityResult(ScanContract()) { r -> r.contents?.let { vm.login(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.red, background = C.bg, surface = C.card)) {
                Root()
            }
        }
        if (Store.autoConnect && Store.link.isNotBlank() && vm.status.value == Status.Stopped) connect()
    }

    override fun onResume() {
        super.onResume()
        // povratak iz browsera poslije plaćanja: osvježi nalog
        if (vm.loggedIn.value) vm.refresh(silent = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // veltrix://import?url=… (dugme na stranici kupca)
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "veltrix") vm.login(data.toString())
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    fun connect() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!vm.prepare()) return
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else vm.start()
    }

    fun toggle() {
        when (vm.status.value) {
            Status.Started -> vm.stop()
            Status.Stopped -> connect()
            else -> {}
        }
    }

    fun scanQr() {
        scan.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(true)
                .setPrompt(t("Scan the QR code from your account page", "Skeniraj QR kod sa stranice svog naloga")),
        )
    }

    fun pasteText(): String = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()

    @Composable
    private fun Root() {
        val loggedIn by vm.loggedIn.collectAsState()
        val msg by vm.message.collectAsState()
        val err by vm.error.collectAsState()
        LaunchedEffect(msg) { msg?.let { toast(it); vm.message.value = null } }
        LaunchedEffect(err) { err?.let { toast(t("Connection failed: ", "Spajanje nije uspjelo: ") + it) } }

        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(C.red.copy(alpha = .22f), C.bg), radius = 1400f),
            ),
        ) {
            val must by vm.mustUpdate.collectAsState()
            val lc by vm.langChanged.collectAsState()
            LaunchedEffect(lc) { if (lc > vm.langHandled) { vm.langHandled = lc; recreate() } }
            when {
                must != null -> MustUpdateScreen(must!!)
                !loggedIn -> LoginScreen()
                else -> MainTabs()
            }
            UpdateDialog()
        }
    }

    /** Panel je ugasio ovu verziju aplikacije: samo dugme za ažuriranje. */
    @Composable
    private fun MustUpdateScreen(msg: String) {
        val u by vm.update.collectAsState()
        val progress by vm.updateProgress.collectAsState()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = C.red, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(18.dp))
            Text(t("Update required", "Potrebno ažuriranje"), color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(msg, color = C.soft, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(t("Your version: ", "Tvoja verzija: ") + BuildConfig.VERSION_NAME, color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = { p }, color = C.red, trackColor = C.line2, modifier = Modifier.fillMaxWidth())
                Text(t("Downloading… ", "Skidanje… ") + "${(p * 100).toInt()}%", color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (u != null) {
                BigButton(t("Update to ", "Ažuriraj na ") + u!!.version, Icons.Outlined.SystemUpdate, filled = true) { vm.installUpdate() }
            } else {
                BigButton(t("Download new version", "Preuzmi novu verziju"), null, filled = true) {
                    runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"))) }
                }
            }
        }
    }

    @Composable
    private fun UpdateDialog() {
        val u by vm.update.collectAsState()
        val dismissed by vm.updateDismissed.collectAsState()
        val progress by vm.updateProgress.collectAsState()
        val info = u ?: return
        if (dismissed && progress == null) return
        AlertDialog(
            onDismissRequest = { if (progress == null) vm.updateDismissed.value = true },
            containerColor = C.card,
            icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = C.red) },
            title = { Text(t("New version available", "Nova verzija je dostupna"), color = C.text) },
            text = {
                Column {
                    Text("${BuildConfig.VERSION_NAME}  →  ${info.version}", color = C.soft, fontSize = 15.sp)
                    info.notes.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = C.muted, fontSize = 13.sp)
                    }
                    progress?.let { p ->
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { p }, color = C.red, trackColor = C.line2, modifier = Modifier.fillMaxWidth())
                        Text(t("Downloading… ", "Skidanje… ") + "${(p * 100).toInt()}%", color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                if (progress == null) TextButton({ vm.installUpdate() }) { Text(t("Update", "Ažuriraj"), color = C.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                if (progress == null) TextButton({ vm.updateDismissed.value = true }) { Text(t("Later", "Kasnije"), color = C.soft) }
            },
        )
    }

    @Composable
    private fun MainTabs() {
        var tab by remember { mutableIntStateOf(0) }
        var sub by remember { mutableStateOf("") }
        val acc by vm.account.collectAsState()
        BackHandler(enabled = sub.isNotEmpty() || tab != 0) { if (sub.isNotEmpty()) sub = "" else tab = 0 }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    sub == "apps" -> AppsScreen(vm) { sub = "" }
                    sub == "devices" -> DevicesScreen(vm) { sub = "" }
                    sub == "history" -> HistoryScreen(vm) { sub = "" }
                    sub == "diag" && (acc?.features?.diag != false || acc?.features?.speedtest != false) -> DiagnosticsScreen(vm) { sub = "" }
                    tab == 0 -> HomeScreen(vm, onToggle = { toggle() }, onLocations = { tab = 1 }, onSettings = { tab = 3 }, onSpeed = { sub = "diag" })
                    tab == 1 -> LocationsScreen(vm, onPicked = { tab = 0 })
                    tab == 2 -> SecurityScreen(vm, onApps = { sub = "apps" })
                    else -> AccountScreen(vm, onSub = { sub = it })
                }
            }
            if (sub.isEmpty()) {
                Row(Modifier.fillMaxWidth().background(if (com.veltrix.vpn.data.Store.amoled) Color.Black else Color(0xFF0E0E13)).padding(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavItem(t("Home", "Početna"), Icons.Filled.Home, Icons.Outlined.Home, tab == 0) { tab = 0 }
                    NavItem(t("Locations", "Lokacije"), Icons.Filled.LocationOn, Icons.Outlined.LocationOn, tab == 1) { tab = 1 }
                    NavItem(t("Security", "Sigurnost"), Icons.Filled.Shield, Icons.Outlined.Shield, tab == 2) { tab = 2 }
                    NavItem(t("Account", "Nalog"), Icons.Filled.Person, Icons.Outlined.Person, tab == 3) { tab = 3 }
                }
            }
        }
    }

    @Composable
    private fun NavItem(label: String, on: ImageVector, off: ImageVector, selected: Boolean, onClick: () -> Unit) {
        Column(
            Modifier.width(84.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(if (selected) on else off, null, tint = if (selected) C.red else C.muted, modifier = Modifier.size(26.dp))
            Text(label, color = if (selected) C.red else C.muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Box(Modifier.padding(top = 4.dp).width(44.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.red else Color.Transparent))
        }
    }

    @Composable
    private fun LoginScreen() {
        val loading by vm.loading.collectAsState()
        var input by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Logo(110.dp)
            Spacer(Modifier.height(20.dp))
            Text("VELTRIX", color = C.text, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("V P N", color = C.redHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                t("Scan the QR code or paste the access link you received after purchase.", "Skeniraj QR kod ili zalijepi pristupni link koji si dobio nakon kupovine."),
                color = C.soft, fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            BigButton(t("Scan QR code", "Skeniraj QR kod"), Icons.Outlined.QrCodeScanner) { scanQr() }
            Spacer(Modifier.height(18.dp))
            Text(t("or", "ili"), color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(C.card)
                    .border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (input.isEmpty()) Text(t("Access link (https://…/s/…)", "Pristupni link (https://…/s/…)"), color = C.muted, fontSize = 15.sp)
                    BasicTextField(input, { input = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 15.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { input = pasteText() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ContentPaste, t("Paste", "Zalijepi"), tint = C.soft)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) {
                CircularProgressIndicator(color = C.red)
            } else {
                BigButton(t("Log in", "Prijavi se"), null, filled = false) { vm.login(input) }
            }
            Spacer(Modifier.height(26.dp))
            var showRef by remember { mutableStateOf(false) }
            var ref by remember { mutableStateOf("") }
            val cfg by vm.loginCfg.collectAsState()
            if (!loading && cfg.trial) {
                BigButton(t("Try free", "Probaj besplatno"), null, filled = true) { vm.trial(ref) }
                Spacer(Modifier.height(10.dp))
                if (!cfg.ref) {
                    // kod prijatelja isključen u panelu
                } else if (!showRef) {
                    Text(t("Have a friend's code?", "Imaš kod prijatelja?"), color = C.redHi, fontSize = 14.sp, modifier = Modifier.clickable { showRef = true }.padding(6.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (ref.isEmpty()) Text(t("Friend's code (optional)", "Kod prijatelja (nije obavezno)"), color = C.muted, fontSize = 15.sp)
                            BasicTextField(ref, { ref = it.uppercase().take(12) }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 15.sp, letterSpacing = 2.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                t("Don't have an account? Contact the seller you bought from.", "Nemaš nalog? Javi se prodavcu od kojeg kupuješ."),
                color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
