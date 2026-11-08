package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.data.HdRezkaClient
import com.hdrezka.tv.data.MovieItem
import com.hdrezka.tv.data.PersonLink

@Composable
fun PersonScreen(
    person: PersonLink,
    onBack: () -> Unit,
    onOpen: (MovieItem) -> Unit
) {
    BackHandler(onBack = onBack)
    var name by remember(person.url) { mutableStateOf(person.name) }
    var items by remember(person.url) { mutableStateOf<List<MovieItem>>(emptyList()) }
    var loading by remember(person.url) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(person.url) {
        loading = true
        error = null
        runCatching { HdRezkaClient.personPage(person.url) }
            .onSuccess { (loadedName, loadedItems) ->
                if (loadedName.isNotBlank()) name = loadedName
                items = loadedItems
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceStrong)
            ) {
                Text("Назад", color = Color.White)
            }
        }

        Spacer(Modifier.height(18.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = TextSecondary, fontSize = 13.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PosterCard(item = item, focusRequester = null, onOpen = { onOpen(item) })
                }
            }
        }
    }
}
