package com.catmap.dubai.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.HelpContact
import com.catmap.dubai.ui.components.*
import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HelpDirectoryScreen(nav: NavHostController) {
    val container=LocalContainer.current
    val context=LocalContext.current
    val runner=rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val contacts by remember { container.helpDirectory.all() }.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf<HelpContact?>(null) }
    val admin=me?.role=="admin"
    Scaffold(topBar={AppTopBar(stringResource(R.string.help_directory),onBack={nav.popBackStack()}) {
        if(admin) TextButton(onClick={editing=HelpContact()}) { Text(stringResource(R.string.add)) }
    }}) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when {
                contacts==null -> items(4, contentType = { "loading" }) { SkeletonRow() }
                contacts!!.isEmpty() -> item { EmptyState(com.catmap.dubai.ui.icons.catMapVector(CatMapIcons.HELP),stringResource(R.string.no_help_contacts)) }
                else -> items(contacts!!,key={it.id},contentType={"help-contact"}) { contact ->
                    Card(shape=MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row { CatMapIcon(CatMapIcons.HELP); Text(contact.name,Modifier.padding(start=8.dp),style=MaterialTheme.typography.titleMedium) }
                            Text(stringResource(helpKindLabel(contact.kind)),style=MaterialTheme.typography.labelLarge)
                            if(contact.demo) StatusPill(stringResource(R.string.demo_entry_label),MaterialTheme.colorScheme.tertiary)
                            if(contact.address.isNotBlank()) Text(contact.address)
                            if(contact.note.isNotBlank()) Text(contact.note)
                            Row {
                                if(contact.phone.isNotBlank()) TextButton(onClick={openContact(context,"tel:"+contact.phone.filter { it.isDigit() || it=='+' })}) { Text(contact.phone) }
                                if(contact.website.startsWith("https://")) TextButton(onClick={openContact(context,contact.website)}) { Text(stringResource(R.string.help_website)) }
                                if(admin) TextButton(onClick={editing=contact}) { Text(stringResource(R.string.edit)) }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var kind by remember(original.id) { mutableStateOf(original.kind) }
        var phone by remember(original.id) { mutableStateOf(original.phone) }
        var address by remember(original.id) { mutableStateOf(original.address) }
        var website by remember(original.id) { mutableStateOf(original.website) }
        var note by remember(original.id) { mutableStateOf(original.note) }
        var demo by remember(original.id) { mutableStateOf(original.demo) }
        AlertDialog(onDismissRequest={if(!runner.busy) editing=null},title={Text(stringResource(R.string.help_directory))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it.take(80)},label={Text(stringResource(R.string.name))})
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("vet","shelter","emergency","other").forEach { k -> FilterChip(kind==k,{kind=k},label={Text(stringResource(helpKindLabel(k)))}) } }
                OutlinedTextField(phone,{phone=it.take(40)},label={Text(stringResource(R.string.help_phone))})
                OutlinedTextField(address,{address=it.take(300)},label={Text(stringResource(R.string.help_address))})
                OutlinedTextField(website,{website=it.take(300)},label={Text(stringResource(R.string.help_website))})
                OutlinedTextField(note,{note=it.take(600)},label={Text(stringResource(R.string.notes))})
                com.catmap.dubai.ui.stations.SwitchRow(stringResource(R.string.demo_contact),demo) {demo=it}
                if(original.id.isNotBlank()) TextButton(enabled=!runner.busy,onClick={runner.run(R.string.deleted,onSuccess={editing=null}) {container.helpDirectory.delete(original)}}) {Text(stringResource(R.string.delete))}
            }
        },confirmButton={TextButton(enabled=name.isNotBlank()&&!runner.busy&&(website.isBlank()||website.startsWith("https://")),onClick={
            runner.run(R.string.saved,onSuccess={editing=null}) {container.helpDirectory.save(original.copy(name=name,kind=kind,phone=phone,address=address,website=website,note=note,demo=demo))}
        }) {Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick={editing=null}) {Text(stringResource(R.string.cancel))}})
    }
}

private fun helpKindLabel(kind:String)=when(kind) {
    "vet" -> R.string.help_kind_vet
    "shelter" -> R.string.help_kind_shelter
    "emergency" -> R.string.help_kind_emergency
    else -> R.string.help_kind_other
}
