package com.hyperwhisper.localization

object RussianStrings : Strings {
    // App Info
    override val appName = "HyperWhisper"
    override val imeName = "HyperWhisper Голосовая Клавиатура"

    // Common Actions
    override val cancel = "Отмена"
    override val save = "Сохранить"
    override val delete = "Удалить"
    override val edit = "Редактировать"
    override val add = "Добавить"
    override val close = "Закрыть"
    override val back = "Назад"
    override val copy = "Копировать"
    override val clear = "Очистить"

    // Keyboard UI
    override val tapToSpeak = "Нажмите для записи"
    override val recording = "Запись..."
    override val processing = "Обработка..."
    override val space = "пробел"
    override val pasteLastTranscription = "ВСТАВИТЬ ПОСЛЕДНЮЮ (удержать: история)"
    override val pasteLastHold = "ВСТАВИТЬ ПОСЛЕДНЮЮ (удержать: история)"
    override val holdForHistory = "Удержать для истории"

    // Buttons
    override val switchKeyboard = "Сменить клавиатуру"
    override val helpAndAbout = "Справка и О программе"
    override val settings = "Настройки"
    override val viewLogs = "Показать логи"
    override val stopRecording = "Остановить запись"
    override val startRecording = "Начать запись"

    // Content Descriptions (for accessibility)
    override val switchKeyboardDesc = "Сменить клавиатуру"
    override val helpAndAboutDesc = "Справка и О программе"
    override val settingsDesc = "Настройки"
    override val viewLogsDesc = "Показать логи"
    override val cancelDesc = "Отмена"
    override val enterDesc = "Ввод"

    // Language Selectors
    override val inputLanguageSpeech = "Язык ввода (речь)"
    override val outputLanguageTranslation = "Язык вывода (перевод)"
    override val autoDetect = "Авто-определение"
    override val searchLanguages = "Поиск..."
    override val searchPlaceholder = "Поиск..."
    override val noLanguagesFound = "Языки не найдены"

    // Settings Screen
    override val settingsTitle = "Настройки HyperWhisper"
    override val apiConfiguration = "Настройка API"
    override val apiProvider = "Провайдер API"
    override val baseUrl = "Базовый URL"
    override val baseUrlHint = "Должен заканчиваться на /"
    override val apiKey = "Ключ API"
    override val apiKeyPlaceholder = "sk-..."
    override val modelId = "ID модели"
    override val testConnection = "Проверить соединение"
    override val testingConnection = "Проверка соединения..."
    override val resetToDefaults = "Сбросить к настройкам по умолчанию"
    override val reset = "СБРОСИТЬ"
    override val saveAndCloseSettings = "Сохранить и закрыть настройки"
    override val inputLanguageHintLabel = "Подсказка языка ввода"
    override val inputLanguageHintText = "Используйте только для авто-определения (язык речи)\nПримечание: Большинство моделей игнорирует это\n• whisper-1 и варианты: Игнорируется\n• distil-whisper-large-v3: Использует"
    override val viewApiLogs = "Показать логи API"
    override val logsInfoTitle = "Логи API и диагностики"
    override val logsInfoDescription = "Просмотр подробных логов вызовов API, этапов обработки и ошибок. Логи очищаются при перезапуске приложения."

    // API Providers
    override val providerDescription = "Выберите провайдера распознавания речи"

    // Voice Modes
    override val voiceModes = "Режимы обработки голоса"
    override val selectMode = "Выберите режим"
    override val addVoiceMode = "Добавить голосовой режим"
    override val editVoiceMode = "Редактировать голосовой режим"
    override val deleteVoiceMode = "Удалить режим"
    override val modeName = "Название режима"
    override val systemPrompt = "Системный промпт"
    override val enterPrompt = "Введите системный промпт для этого режима"

    // Default Voice Modes
    override val modeVerbatim = "Точная копия"
    override val modeVerbatimPrompt = "Расшифровать аудио точно как произнесено."
    override val modeFixGrammar = "Исправить грамматику"
    override val modeFixGrammarPrompt = "Расшифровать это аудио и исправить любые грамматические, орфографические ошибки и пунктуацию, сохраняя исходный смысл и тон."
    override val modePromptFormatter = "Форматировщик промптов"
    override val modePromptFormatterPrompt = "Переформулировать ввод пользователя в четкий, эффективный промпт, подходящий для обработки LLM. Улучшить ясность, добавить необходимый контекст и структурировать для оптимального понимания ИИ. Сохранить намерение пользователя, делая его более точным и действенным."
    override val modeLlmResponse = "Ответ LLM"
    override val modeLlmResponsePrompt = "Пользователь задает вопрос. Предоставить прямой, краткий ответ на вопрос без дополнительных объяснений или контекста. Вернуть ТОЛЬКО сам ответ."

    // Appearance Settings
    override val appearanceSettings = "Внешний вид"
    override val colorScheme = "Цветовая схема"
    override val useDynamicColor = "Использовать динамические цвета"
    override val useDynamicColorDesc = "Соответствовать цветам обоев системы"
    override val themeMode = "Режим темы"
    override val darkMode = "Темный режим"
    override val textSize = "Размер текста"
    override val fontFamily = "Семейство шрифтов"
    override val uiLanguage = "Язык интерфейса"

    // Dark Mode Options
    override val darkModeFollowSystem = "Следовать системе"
    override val darkModeAlwaysLight = "Всегда светлый"
    override val darkModeAlwaysDark = "Всегда темный"

    // Advanced Settings
    override val advancedSettings = "Дополнительно"
    override val autoCopyToClipboard = "Авто-копирование в буфер"
    override val autoCopyToClipboardDesc = "Автоматически копировать расшифровки"
    override val enableHistoryPanel = "Включить панель истории"
    override val enableHistoryPanelDesc = "Длительное нажатие кнопки вставки для просмотра истории"

    // About Screen
    override val aboutHyperWhisper = "О HyperWhisper"
    override val version = "Версия"
    override val versionCode = "Код"
    override val description = "HyperWhisper — это метод ввода голос-в-текст (клавиатура), который использует передовые API распознавания речи для обеспечения быстрой и точной расшифровки. Разработан для разработчиков и опытных пользователей, желающих настроить свой голосовой ввод."
    override val features = "Возможности:"
    override val featuresList = "• Настраиваемый провайдер API (OpenAI, Groq, OpenRouter и др.)\n• Несколько голосовых режимов (Точная копия, Исправление грамматики, Вежливый и др.)\n• Поддержка различных языков ввода и вывода\n• Современный, отзывчивый интерфейс на Jetpack Compose"
    override val usageStatisticsAndCosts = "Статистика использования и затраты:"
    override val noUsageDataYet = "Данных об использовании пока нет. Начните использовать клавиатуру, чтобы увидеть статистику!"
    override val totalAudio = "Всего аудио"
    override val estimatedTotalCost = "Приблизительная общая стоимость"
    override val modelBreakdown = "Разбивка по моделям:"
    override val inputTokens = "Ввод"
    override val outputTokens = "Вывод"
    override val totalTokens = "Всего"
    override val audioBasedPricing = "Ценообразование на основе аудио (токены не отслеживаются)"
    override val costsEstimateNote = "* Затраты оцениваются на основе текущих цен API. Фактические затраты могут отличаться."
    override val clearStatistics = "ОЧИСТИТЬ СТАТИСТИКУ"

    // Logs Screen
    override val traceLogs = "Журналы трассировки"
    override val diagnosticLogs = "Диагностические журналы"
    override val diagnosticLogsDesc = "Эти журналы показывают вызовы API, этапы обработки и ошибки. Журналы очищаются при перезапуске приложения. Вы можете скопировать или очистить их с помощью кнопок выше."
    override val logsStoredAt = "Журналы хранятся в"
    override val noLogsYet = "Журналов пока нет.\nНачните использовать клавиатуру, чтобы увидеть журналы активности."
    override val copyLogs = "Копировать журналы"
    override val clearLogs = "Очистить журналы"
    override val logsCopiedToClipboard = "Журналы скопированы в буфер обмена"

    // Configuration Info
    override val currentConfiguration = "Текущая конфигурация"
    override val provider = "Провайдер"
    override val transcriptionModel = "Модель расшифровки"
    override val postProcessingModel = "Модель пост-обработки"
    override val postProcessingModelDesc = "gpt-4o-mini (для не-verbatim режимов и перевода)"
    override val none = "Нет"
    override val keepOriginal = "оставить оригинал"
    override val notConfigured = "Не настроено"

    // Transcription History
    override val transcriptionHistory = "История расшифровок"
    override val historyCount = "{count}/20"
    override val noHistoryYet = "История пока пуста"
    override val clearAll = "ОЧИСТИТЬ ВСЁ"

    // Connection Test
    override val connectionTesting = "Проверка соединения..."
    override val connectionSuccess = "Соединение успешно! API отвечает."
    override val connectionFailed = "Ошибка соединения"
    override val authenticationFailed = "Ошибка аутентификации. Проверьте ваш ключ API."
    override val endpointNotFound = "Эндпоинт не найден. Проверьте базовый URL и ID модели."
    override val connectionTimeout = "Тайм-аут соединения. Проверьте ваше интернет-соединение."
    override val sslError = "Ошибка SSL/TLS. Проверьте URL эндпоинта (https)."

    // Errors
    override val error = "Ошибка"
    override val copyError = "КОПИРОВАТЬ ОШИБКУ"
    override val openSettings = "ОТКРЫТЬ НАСТРОЙКИ"
    override val dismiss = "ЗАКРЫТЬ"
    override val errorConfigureApiKey = "Пожалуйста, настройте ключ API в настройках"
    override val errorNoModeSelected = "Голосовой режим не выбран"
    override val errorRecordingFailed = "Ошибка записи"
    override val errorNetworkFailed = "Ошибка сети. Пожалуйста, проверьте ваше соединение."
    override val errorApiCall = "Ошибка API"
    override val errorPermissionMicrophone = "Разрешение на микрофон не предоставлено. Пожалуйста, включите доступ к микрофону в настройках Android."
    override val errorMicrophoneInUse = "Нет доступа к микрофону. Возможно, он используется другим приложением."

    // Processing Info Toast
    override val translated = "Переведено на"
    override val twoStepProcessing = "Двухэтапная обработка"

    // Time units
    override val minutes = "м"
    override val seconds = "с"

    // Input Field Information
    override val inputFieldType = "Тип"
    override val inputFieldApp = "Приложение"
    override val inputFieldAction = "Действие"
    override val fieldTypeText = "Текст"
    override val fieldTypeEmail = "Email"
    override val fieldTypePassword = "Пароль"
    override val fieldTypeNumber = "Число"
    override val fieldTypePhone = "Телефон"
    override val fieldTypeUrl = "URL"
    override val fieldTypeMultiline = "Многострочный"
    override val fieldTypeUnknown = "Неизвестно"
    override val actionNone = "Нет"
    override val actionDone = "Готово"
    override val actionGo = "Перейти"
    override val actionSearch = "Поиск"
    override val actionSend = "Отправить"
    override val actionNext = "Далее"
    override val actionPrevious = "Назад"
}
