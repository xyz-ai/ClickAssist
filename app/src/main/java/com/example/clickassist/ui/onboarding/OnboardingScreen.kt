package com.example.clickassist.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.clickassist.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val pages = listOf(
        R.string.onboarding_title_1 to R.string.onboarding_desc_1,
        R.string.onboarding_title_2 to R.string.onboarding_desc_2,
        R.string.onboarding_title_3 to R.string.onboarding_desc_3,
        R.string.onboarding_title_4 to R.string.onboarding_desc_4,
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_header_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.onboarding_header_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val item = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = stringResource(item.first),
                        modifier = Modifier.padding(top = 32.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(item.second),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 20.dp else 8.dp, 8.dp)
                            .background(
                                color = if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage < pages.lastIndex) {
                    OutlinedButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text(text = stringResource(R.string.onboarding_skip))
                    }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage == pages.lastIndex) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (pagerState.currentPage == pages.lastIndex) {
                                R.string.onboarding_get_started
                            } else {
                                R.string.tutorial_next
                            },
                        ),
                    )
                }
            }
        }
    }
}
