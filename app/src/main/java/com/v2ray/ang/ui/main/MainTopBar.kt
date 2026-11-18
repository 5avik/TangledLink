package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar

@Composable
fun MainTopBar(isLoading: Boolean) {
    AppTopBar(
        title = stringResource(R.string.app_name),
        onBackClick = {},
        isLoading = isLoading,
        navigationIcon = {}
    )
}
