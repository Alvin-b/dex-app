package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CargoPackage
import com.example.data.Employee
import com.example.ui.DexcargoViewModel
import com.example.ui.Screen
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SalesRepHomeScreen(viewModel: DexcargoViewModel) {
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()
    val alerts by viewModel.broadcastMessages.collectAsState()
    val backendCommissions by viewModel.backendCommissions.collectAsState()

    val myPackages = remember(packages, currentEmp) {
        val currentId = currentEmp?.id ?: ""
        if (currentId.isBlank()) packages else packages.filter { it.salesRep.contains(currentId, ignoreCase = true) || it.salesRep.contains(currentEmp?.name ?: "", ignoreCase = true) }
    }

    val totalCost = remember(myPackages) {
        myPackages.filter { it.status == "collected" }.sumOf { it.cost }
    }
    val commissionAmount = remember(backendCommissions, myPackages) {
        if (backendCommissions.isNotEmpty()) {
            backendCommissions.filter { it.employeeId == currentEmp?.id }.sumOf { it.amount }.toInt()
        } else {
            (totalCost * 0.10).toInt()
        }
    }

    val regCount = myPackages.count { it.status == "registered" }
    val paidCount = myPackages.count { it.status == "paid" }
    val readyCount = myPackages.count { it.status == "paid" }
    val doneCount = myPackages.count { it.status == "collected" }

    val myAlerts = remember(alerts) {
        alerts.filter { it.target == "all" || it.target == "sr" }
    }

    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(messages = myAlerts, onDismiss = { showNotificationsDialog = false })
    }

    val empName = currentEmp?.name ?: "Sales Representative"
    val empId = currentEmp?.id ?: "SR"
    val empInitials = empName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        // TOP PROFILE BAR
        item {
            EmployeeProfileBar(
                name = empName,
                roleLabel = "Sales Representative",
                id = empId,
                badgeColor = OrangeAccent,
                initials = if (empInitials.isNotBlank()) empInitials else "SR",
                notificationCount = myAlerts.size,
                onNotificationClick = { showNotificationsDialog = true },
                onProfileClick = { viewModel.navigateTo(Screen.ProfileSettings) }
            )
        }

        // COMMISSION HERO
        item {
            CommissionHeroCard(
                label = "This Month's Commission",
                amount = "KES ${commissionAmount.toLocaleString()}",
                indicator = "▲ 18.2% from last month",
                icon = "💰",
                gradientBg = Brush.linearGradient(
                    colors = listOf(OrangeAccentBg, Color(0x05F59E0B), DarkSurface)
                ),
                onClick = { viewModel.navigateTo(Screen.MyCommissions) }
            )
        }

        // SYSTEM ALERTS BROADCAST
        if (myAlerts.isNotEmpty()) {
            item {
                BroadcastAlertSection(myAlerts.first().message, color = OrangeAccent)
            }
        }

        // SEARCH & SCAN STICKER ACTIONS
        item {
            QuickSearchScanSection(
                onSearchFocus = { viewModel.navigateTo(Screen.PackageList) },
                onScanClick = { viewModel.navigateTo(Screen.ScanSticker) }
            )
        }

        // OVERVIEW STATS GRID
        item {
            SectionTitle(text = "My Activity Overview")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = regCount.toString(),
                    label = "Registered",
                    icon = "📦",
                    color = OrangeAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "all"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = paidCount.toString(),
                    label = "Paid Shipments",
                    icon = "💳",
                    color = GreenAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "cleared"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = readyCount.toString(),
                    label = "Ready/Uncollected",
                    icon = "⏳",
                    color = PurpleAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "cleared"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = doneCount.toString(),
                    label = "Delivered",
                    icon = "✅",
                    color = BlueAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "cleared"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
            }
        }

        // RECENT RECODS LIST
        item {
            SectionTitle(
                text = "Recent Client Packages",
                actionText = "View List",
                onActionClick = { viewModel.navigateTo(Screen.PackageList) }
            )
        }

        if (myPackages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No registered packages.", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(myPackages.takeLast(4).reversed()) { pkg ->
                PackageItemRow(pkg = pkg, onClick = {
                    viewModel.selectedPackageId.value = pkg.id
                    viewModel.navigateTo(Screen.PackageDetails)
                })
            }
        }
    }
}

@Composable
fun LogisticsManagerHomeScreen(viewModel: DexcargoViewModel) {
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()
    val alerts by viewModel.broadcastMessages.collectAsState()
    val backendCommissions by viewModel.backendCommissions.collectAsState()

    val totalCost = remember(packages) {
        packages.size * 300 // KES 300 per sorted package
    }
    val sortingCommission = remember(backendCommissions, packages) {
        if (backendCommissions.isNotEmpty()) {
            backendCommissions.filter { it.employeeId == currentEmp?.id }.sumOf { it.amount }.toInt()
        } else {
            totalCost
        }
    }

    val totalManaged = packages.size
    val unpaidCount = packages.count { it.status == "registered" }
    val readyCount = packages.count { it.status == "paid" }
    val clearedCount = packages.count { it.status == "collected" }

    val myAlerts = remember(alerts) {
        alerts.filter { it.target == "all" || it.target == "lm" }
    }

    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(messages = myAlerts, onDismiss = { showNotificationsDialog = false })
    }

    val empName = currentEmp?.name ?: "Logistics Manager"
    val empId = currentEmp?.id ?: "LM"
    val empInitials = empName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        item {
            EmployeeProfileBar(
                name = empName,
                roleLabel = "Logistics Manager",
                id = empId,
                badgeColor = BlueAccent,
                initials = if (empInitials.isNotBlank()) empInitials else "LM",
                notificationCount = myAlerts.size,
                onNotificationClick = { showNotificationsDialog = true },
                onProfileClick = { viewModel.navigateTo(Screen.ProfileSettings) }
            )
        }

        item {
            CommissionHeroCard(
                label = "Operational Sorting Commission",
                amount = "KES ${sortingCommission.toLocaleString()}",
                indicator = "▲ 24% from last month",
                icon = "📦",
                gradientBg = Brush.linearGradient(
                    colors = listOf(BlueAccentBg, Color(0x053B6BF5), DarkSurface)
                ),
                onClick = { viewModel.navigateTo(Screen.MyCommissions) }
            )
        }

        if (myAlerts.isNotEmpty()) {
            item {
                BroadcastAlertSection(myAlerts.first().message, color = BlueAccent)
            }
        }

        item {
            QuickSearchScanSection(
                onSearchFocus = { viewModel.navigateTo(Screen.PackageList) },
                onScanClick = { viewModel.navigateTo(Screen.ScanSticker) }
            )
        }

        item {
            SectionTitle(text = "Warehouse Flow")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = totalManaged.toString(),
                    label = "Total Managed",
                    icon = "🏢",
                    color = BlueAccent,
                    onClick = { viewModel.navigateTo(Screen.PackageList) }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = unpaidCount.toString(),
                    label = "Unpaid In-Bond",
                    icon = "⏳",
                    color = OrangeAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "all"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = readyCount.toString(),
                    label = "Ready for Collection",
                    icon = "📋",
                    color = PurpleAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "cleared"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = clearedCount.toString(),
                    label = "Cleared/Dispatched",
                    icon = "✅",
                    color = GreenAccent,
                    onClick = {
                        viewModel.packageListFilter.value = "cleared"
                        viewModel.navigateTo(Screen.PackageList)
                    }
                )
            }
        }

        item {
            SectionTitle(text = "Warehouse Staff & Tasks")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Staff", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Text("6 Clerks", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("👥", fontSize = 18.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pending Sorts", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Text("7 Cargoes", color = OrangeAccent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("⚡", fontSize = 18.sp)
                    }
                }
            }
        }

        item {
            SectionTitle(
                text = "Recent Warehouse Registrations",
                actionText = "Scan Cargo",
                onActionClick = { viewModel.navigateTo(Screen.ScanSticker) }
            )
        }

        if (packages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Warehouse empty.", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(packages.takeLast(4).reversed()) { pkg ->
                PackageItemRow(pkg = pkg, onClick = {
                    viewModel.selectedPackageId.value = pkg.id
                    viewModel.navigateTo(Screen.PackageDetails)
                })
            }
        }
    }
}

@Composable
fun SalesManagerHomeScreen(viewModel: DexcargoViewModel) {
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()
    val alerts by viewModel.broadcastMessages.collectAsState()
    val employeesList by viewModel.employees.collectAsState()
    val backendCommissions by viewModel.backendCommissions.collectAsState()

    val totalPaidRevenue = remember(packages) {
        packages.filter { it.status != "registered" }.sumOf { it.cost }
    }
    val smCommission = remember(backendCommissions, packages) {
        if (backendCommissions.isNotEmpty()) {
            backendCommissions.filter { it.employeeId == currentEmp?.id }.sumOf { it.amount }.toInt()
        } else {
            (totalPaidRevenue * 0.05).toInt() + (packages.size * 50)
        }
    }

    val totalTeamPackages = packages.size
    val salesRepsCount = employeesList.count { it.role == "sr" }

    val myAlerts = remember(alerts) {
        alerts.filter { it.target == "all" || it.target == "sm" }
    }

    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(messages = myAlerts, onDismiss = { showNotificationsDialog = false })
    }

    val empName = currentEmp?.name ?: "Sales Manager"
    val empId = currentEmp?.id ?: "SM"
    val empInitials = empName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        item {
            EmployeeProfileBar(
                name = empName,
                roleLabel = "Sales Manager",
                id = empId,
                badgeColor = GreenAccent,
                initials = if (empInitials.isNotBlank()) empInitials else "SM",
                notificationCount = myAlerts.size,
                onNotificationClick = { showNotificationsDialog = true },
                onProfileClick = { viewModel.navigateTo(Screen.ProfileSettings) }
            )
        }

        item {
            CommissionHeroCard(
                label = "This Month's Manager Override",
                amount = "KES ${smCommission.toLocaleString()}",
                indicator = "▲ 32.5% vs target",
                icon = "📈",
                gradientBg = Brush.linearGradient(
                    colors = listOf(GreenAccentBg, Color(0x0510B981), DarkSurface)
                ),
                onClick = { viewModel.navigateTo(Screen.MyCommissions) }
            )
        }

        if (myAlerts.isNotEmpty()) {
            item {
                BroadcastAlertSection(myAlerts.first().message, color = GreenAccent)
            }
        }

        item {
            SectionTitle(text = "Team Pipeline Overview")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = if (salesRepsCount > 0) salesRepsCount.toString() else "1",
                    label = "Sales Reps",
                    icon = "🤝",
                    color = OrangeAccent
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = totalTeamPackages.toString(),
                    label = "Team Packages",
                    icon = "📦",
                    color = BlueAccent,
                    onClick = { viewModel.navigateTo(Screen.PackageList) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "KES ${totalPaidRevenue.toLocaleString()}",
                    label = "Gross Revenue",
                    icon = "💰",
                    color = GreenAccent
                )
                val conversionRate = if (packages.isNotEmpty()) (packages.count { it.status != "registered" } * 100 / packages.size) else 0
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "$conversionRate%",
                    label = "Conversion Rate",
                    icon = "📊",
                    color = PurpleAccent
                )
            }
        }

        item {
            SectionTitle(text = "Top Performing Representatives")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val salesReps = employeesList.filter { it.role == "sr" }
                if (salesReps.isNotEmpty()) {
                    salesReps.forEachIndexed { index, rep ->
                        val repPackages = packages.filter { it.salesRep.contains(rep.id, ignoreCase = true) || it.salesRep.contains(rep.name, ignoreCase = true) }
                        val repRevenue = repPackages.filter { it.status == "collected" }.sumOf { it.cost }
                        val repComm = (repRevenue * 0.10).toInt()
                        val medal = when (index) {
                            0 -> "🏆"
                            1 -> "🥈"
                            else -> "🥉"
                        }
                        LeaderboardCard(
                            name = rep.name,
                            earned = "KES ${repComm.toLocaleString()} Earned",
                            rank = medal
                        )
                    }
                } else {
                    LeaderboardCard(
                        name = "John Kamau",
                        earned = "KES ${(totalPaidRevenue * 0.10).toInt().toLocaleString()} Earned",
                        rank = "🏆"
                    )
                }
            }
        }

        item {
            SectionTitle(text = "My Manager Overrides Breakdown")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val salesReps = employeesList.filter { it.role == "sr" }
                    if (salesReps.isNotEmpty()) {
                        salesReps.forEachIndexed { idx, rep ->
                            val repPackages = packages.filter { it.salesRep.contains(rep.id, ignoreCase = true) || it.salesRep.contains(rep.name, ignoreCase = true) }
                            val repRevenue = repPackages.filter { it.status == "collected" }.sumOf { it.cost }
                            val repOverride = (repRevenue * 0.05).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${rep.name} Override (5%)", color = TextSecondary, fontSize = 11.5.sp)
                                Text("KES ${repOverride.toLocaleString()}", color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            if (idx < salesReps.size - 1) {
                                Divider(color = DarkBorder)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("John Kamau Override (5%)", color = TextSecondary, fontSize = 11.5.sp)
                            Text("KES ${(totalPaidRevenue * 0.05).toInt().toLocaleString()}", color = OrangeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(viewModel: DexcargoViewModel) {
    val currentEmp by viewModel.currentEmployee.collectAsState()
    val employeesList by viewModel.employees.collectAsState()
    val packages by viewModel.cargoPackages.collectAsState()
    val notifications by viewModel.paymentNotifications.collectAsState()
    val alerts by viewModel.broadcastMessages.collectAsState()
    val backendCommissions by viewModel.backendCommissions.collectAsState()

    var showNotificationsDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationsDialog(messages = alerts, onDismiss = { showNotificationsDialog = false })
    }

    val totalManaged = packages.size
    val totalRevenue = packages.filter { it.status != "registered" }.sumOf { it.cost }
    val grossPaidComm = remember(backendCommissions, packages) {
        if (backendCommissions.isNotEmpty()) {
            backendCommissions.sumOf { it.amount }.toInt()
        } else {
            (totalRevenue * 0.15).toInt() + (packages.size * 300)
        }
    }

    val activeClientsCount = remember(packages) {
        packages.map { it.consignee }.filter { it.isNotBlank() }.distinct().size
    }

    val unlinkedNotifsCount = notifications.count { it.status == "PENDING" }
    val totalOutstanding = packages.filter { it.status == "registered" }.sumOf { it.cost }
    val airPackagesCount = packages.count { it.mode == "Air Freight" }
    val seaPackagesCount = packages.count { it.mode == "Sea Freight" }
    val collectedCount = packages.count { it.status == "collected" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(bottom = 76.dp)
    ) {
        item {
            EmployeeProfileBar(
                name = "Admin Console",
                roleLabel = "System Root",
                id = currentEmp?.id ?: "ADM-001",
                badgeColor = PurpleAccent,
                initials = "AD",
                notificationCount = alerts.size,
                onNotificationClick = { showNotificationsDialog = true },
                onProfileClick = { viewModel.navigateTo(Screen.ProfileSettings) }
            )
        }

        item {
            CommissionHeroCard(
                label = "Gross Platform Commissions Paid",
                amount = "KES ${grossPaidComm.toLocaleString()}",
                indicator = "★ Admin does not earn personal commission",
                icon = "⚙️",
                gradientBg = Brush.linearGradient(
                    colors = listOf(PurpleAccentBg, Color(0x058B5CF6), DarkSurface)
                )
            )
        }

        item {
            SectionTitle(text = "Enterprise Metrics")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = totalManaged.toLocaleString(),
                    label = "Total Packages",
                    icon = "🏢",
                    color = BlueAccent,
                    onClick = { viewModel.navigateTo(Screen.PackageList) }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "KES ${totalRevenue.toLocaleString()}",
                    label = "Gross Revenue",
                    icon = "💰",
                    color = GreenAccent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = employeesList.count { it.isActive }.toString(),
                    label = "Active Employees",
                    icon = "👥",
                    color = OrangeAccent
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = if (activeClientsCount > 0) activeClientsCount.toString() else "0",
                    label = "Active Clients",
                    icon = "🏛️",
                    color = PurpleAccent
                )
            }
        }

        item {
            SectionTitle(text = "📊 Operational & Financial Insights")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "KES ${totalOutstanding.toLocaleString()}",
                    label = "Awaiting Payment",
                    icon = "⏳",
                    color = OrangeAccent
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "$unlinkedNotifsCount Receipts",
                    label = "Unlinked Receipts",
                    icon = "🧾",
                    color = PurpleAccent,
                    onClick = { viewModel.navigateTo(Screen.PaymentNotificationCenter) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "✈️ $airPackagesCount / 🚢 $seaPackagesCount",
                    label = "Air vs. Sea Freight",
                    icon = "📦",
                    color = BlueAccent
                )
                val rate = if (packages.isNotEmpty()) (collectedCount * 100 / packages.size) else 0
                StatCard(
                    modifier = Modifier.weight(1f),
                    num = "$rate%",
                    label = "Package Collection Rate",
                    icon = "✅",
                    color = GreenAccent
                )
            }
        }

        // DISPATCH BROADCAST CONTROL
        item {
            SectionTitle(text = "🔊 Dispatch Hub Broadcast")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BROADCAST ALERT MESSAGE",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val textState = viewModel.broadcastText.collectAsState()
                    DexTextField(
                        value = textState.value,
                        onValueChange = { viewModel.broadcastText.value = it },
                        label = "",
                        placeholder = "Type notification to employees..."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targetState = viewModel.broadcastTarget.collectAsState()
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val label = when (targetState.value) {
                                    "all" -> "All Employees"
                                    "sr" -> "Sales Reps"
                                    "lm" -> "Logistics Mgrs"
                                    else -> "Sales Leads"
                                }
                                Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, "expand", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Employees", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.broadcastTarget.value = "all"
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sales Representatives", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.broadcastTarget.value = "sr"
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logistics Managers", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.broadcastTarget.value = "lm"
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sales Managers", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.broadcastTarget.value = "sm"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.submitBroadcastMessage() },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Send Alert", color = Color(0xFF1A1200), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // EMPLOYEE DIRECTORY
        item {
            SectionTitle(text = "👥 Employee Directory")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // List
                    employeesList.forEach { emp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${emp.name} (${emp.id})",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val roleText = when (emp.role) {
                                    "sr" -> "Sales Rep"
                                    "lm" -> "Logistics Mgr"
                                    "sm" -> "Sales Manager"
                                    else -> "Admin"
                                }
                                Text("$roleText · ${emp.email}", color = TextSecondary, fontSize = 9.5.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (emp.isActive) GreenAccentBg else RedAccentBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (emp.isActive) "Active" else "Inactive",
                                        color = if (emp.isActive) GreenAccent else RedAccent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (emp.id != "ADM-001") {
                                    Button(
                                        onClick = { viewModel.toggleEmployeeActiveState(emp.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (emp.isActive) RedAccent else BlueAccent
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(26.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (emp.isActive) "Deactivate" else "Activate",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // Registration section
                    Text(
                        "REGISTER NEW EMPLOYEE",
                        color = TextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val regName by viewModel.empRegName.collectAsState()
                    val regEmail by viewModel.empRegEmail.collectAsState()
                    val regPass by viewModel.empRegPass.collectAsState()
                    val regRole by viewModel.empRegRole.collectAsState()

                    DexTextField(
                        value = regName,
                        onValueChange = { viewModel.empRegName.value = it },
                        label = "",
                        placeholder = "Employee Display Name"
                    )
                    DexTextField(
                        value = regEmail,
                        onValueChange = { viewModel.empRegEmail.value = it },
                        label = "",
                        placeholder = "Email / ID"
                    )
                    DexTextField(
                        value = regPass,
                        onValueChange = { viewModel.empRegPass.value = it },
                        label = "",
                        placeholder = "Initial Password"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var roleDropdownExpanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { roleDropdownExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val label = when (regRole) {
                                    "sr" -> "Sales Rep"
                                    "lm" -> "Logistics Mgr"
                                    else -> "Sales Manager"
                                }
                                Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, "expand", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = roleDropdownExpanded,
                                onDismissRequest = { roleDropdownExpanded = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sales Representative", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.empRegRole.value = "sr"
                                        roleDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logistics Manager", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.empRegRole.value = "lm"
                                        roleDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sales Manager", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        viewModel.empRegRole.value = "sm"
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.registerNewEmployee() },
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Add User", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // PAYROLL LEDGER
        item {
            SectionTitle(text = "Corporate Payroll & Commission Ledger")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (employeesList.isNotEmpty()) {
                        employeesList.forEach { emp ->
                            val color = when (emp.role) {
                                "lm" -> BlueAccent
                                "sm" -> GreenAccent
                                "sr" -> OrangeAccent
                                else -> PurpleAccent
                            }
                            val commAmount = when (emp.role) {
                                "lm" -> packages.count { it.status == "collected" } * 300
                                "sm" -> (packages.filter { it.status == "collected" }.sumOf { it.cost } * 0.05).toInt()
                                "sr" -> {
                                    val repPkgs = packages.filter { it.salesRep.contains(emp.id, ignoreCase = true) || it.salesRep.contains(emp.name, ignoreCase = true) }
                                    (repPkgs.filter { it.status == "collected" }.sumOf { it.cost } * 0.10).toInt()
                                }
                                else -> 0
                            }
                            LedgerRow(
                                label = emp.name,
                                id = emp.id,
                                amount = "KES ${commAmount.toLocaleString()}",
                                color = color
                            )
                        }
                    } else {
                        LedgerRow(label = "Mary Wanjiku", id = "LM-001", amount = "KES ${(packages.size * 300).toLocaleString()}", color = BlueAccent)
                        LedgerRow(label = "John Kamau", id = "SR-002", amount = "KES ${(totalRevenue * 0.10).toInt().toLocaleString()}", color = OrangeAccent)
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS FOR HOME DASHBOARDS ---

@Composable
fun EmployeeProfileBar(
    name: String,
    roleLabel: String,
    id: String,
    badgeColor: Color,
    initials: String,
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Good Morning,", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "$name 👋",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$roleLabel · $id",
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notification Bell with Badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, CircleShape)
                    .clickable { onNotificationClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = if (notificationCount > 0) OrangeAccent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notificationCount.toString(),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            RoleBadge(role = id.split("-")[0].lowercase(), id = id.split("-")[1])

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(badgeColor, badgeColor.copy(alpha = 0.5f))
                        )
                    )
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    messages: List<com.example.data.BroadcastMessage>,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notifications Inbox (${messages.size})",
                        color = OrangeAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notifications yet.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { msg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Notification: ${msg.target.uppercase()}",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val time = msg.timestamp ?: msg.createdAt
                                    Text(
                                        text = if (time.contains("T")) time.substringBefore("T") else time,
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.message,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommissionHeroCard(
    label: String,
    amount: String,
    indicator: String,
    icon: String,
    gradientBg: Brush,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradientBg)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = amount,
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(indicator, color = GreenAccent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBorder),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun BroadcastAlertSection(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                "🔊 SYSTEM BROADCASTS",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun QuickSearchScanSection(onSearchFocus: () -> Unit, onScanClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .clickable { onSearchFocus() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, "Search", tint = TextMuted, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Search or scan stickers...", color = TextMuted, fontSize = 11.5.sp)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BlueAccent)
                .clickable { onScanClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCodeScanner, "Scan", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun StatCard(
    num: String,
    label: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 14.sp)
            }
            Column {
                Text(
                    text = num,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(label, color = TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun LeaderboardCard(name: String, earned: String, rank: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(BlueAccentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 12.sp)
                }
                Column {
                    Text(name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(earned, color = OrangeAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(rank, fontSize = 16.sp)
        }
    }
}

@Composable
fun LedgerRow(label: String, id: String, amount: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.dp, Color.Transparent)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoleBadge(role = id.split("-")[0].lowercase(), id = id.split("-")[1])
            Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(amount, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PackageItemRow(pkg: CargoPackage, onClick: () -> Unit) {
    val modeIcon = if (pkg.mode == "Air Freight") "✈️" else "🚢"
    val modeClassColor = if (pkg.mode == "Air Freight") OrangeAccent else BlueAccent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CargoThumbnail(pkg = pkg, onClick = onClick)

        Spacer(modifier = Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.id,
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${pkg.consignee} · ${pkg.desc}",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "KES ${pkg.cost.toLocaleString()}",
                color = OrangeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(status = pkg.status)
        }
    }
}

// Utility to format numbers with commas
fun Int.toLocaleString(): String {
    return "%,d".format(this)
}
