package com.tonezen.app.ui.profile

import androidx.compose.runtime.Composable

@Composable
internal fun resolveAccountError(error: String?): String? = when (error) {
    ProfileViewModel.ACCOUNT_OFFLINE_ERROR -> "Нужно подключение к интернету"
    ProfileViewModel.PASSWORD_MISMATCH_ERROR -> "Пароли не совпадают"
    ProfileViewModel.NOT_SIGNED_IN_ERROR -> "Войдите в аккаунт"
    ProfileViewModel.PASSWORD_TOO_SHORT_ERROR -> "Пароль должен быть не короче 12 символов"
    ProfileViewModel.PROFILE_UPDATE_FAILED_ERROR -> "Не удалось сохранить профиль"
    ProfileViewModel.PASSWORD_CHANGE_FAILED_ERROR -> "Не удалось сменить пароль"
    ProfileViewModel.REFERRAL_CODE_FAILED_ERROR -> "Не удалось загрузить реферальный код"
    else -> null
}

@Composable
internal fun resolveAvatarUploadError(error: String?): String? = when (error) {
    ProfileViewModel.ACCOUNT_OFFLINE_ERROR -> "Нужно подключение к интернету"
    ProfileViewModel.NOT_SIGNED_IN_ERROR -> "Войдите в аккаунт"
    ProfileViewModel.AVATAR_UPLOAD_FAILED_ERROR -> "Не удалось загрузить аватар"
    else -> null
}
