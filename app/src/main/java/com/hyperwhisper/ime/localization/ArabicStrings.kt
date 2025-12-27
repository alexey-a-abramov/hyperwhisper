package com.hyperwhisper.localization

object ArabicStrings : Strings {
    // App Info
    override val appName = "HyperWhisper"
    override val imeName = "لوحة مفاتيح HyperWhisper الصوتية"

    // Common Actions
    override val cancel = "إلغاء"
    override val save = "حفظ"
    override val delete = "حذف"
    override val edit = "تعديل"
    override val add = "إضافة"
    override val close = "إغلاق"
    override val back = "رجوع"
    override val copy = "نسخ"
    override val clear = "مسح"

    // Keyboard UI
    override val tapToSpeak = "انقر للتحدث"
    override val recording = "جارٍ التسجيل..."
    override val processing = "جارٍ المعالجة..."
    override val space = "مسافة"
    override val pasteLastTranscription = "لصق آخر نسخ (اضغط مطولاً: السجل)"
    override val pasteLastHold = "لصق آخر نسخ (اضغط مطولاً: السجل)"
    override val holdForHistory = "اضغط مطولاً للسجل"

    // Buttons
    override val switchKeyboard = "تبديل لوحة المفاتيح"
    override val helpAndAbout = "مساعدة ومعلومات"
    override val settings = "الإعدادات"
    override val viewLogs = "عرض السجلات"
    override val stopRecording = "إيقاف التسجيل"
    override val startRecording = "بدء التسجيل"

    // Content Descriptions (for accessibility)
    override val switchKeyboardDesc = "تبديل لوحة المفاتيح"
    override val helpAndAboutDesc = "مساعدة ومعلومات"
    override val settingsDesc = "الإعدادات"
    override val viewLogsDesc = "عرض السجلات"
    override val cancelDesc = "إلغاء"
    override val enterDesc = "إدخال"

    // Language Selectors
    override val inputLanguageSpeech = "لغة الإدخال (الكلام)"
    override val outputLanguageTranslation = "لغة الإخراج (الترجمة)"
    override val autoDetect = "كشف تلقائي"
    override val searchLanguages = "بحث..."
    override val searchPlaceholder = "بحث..."
    override val noLanguagesFound = "لم يتم العثور على لغات"

    // Settings Screen
    override val settingsTitle = "إعدادات HyperWhisper"
    override val apiConfiguration = "تكوين API"
    override val apiProvider = "مزود API"
    override val baseUrl = "عنوان URL الأساسي"
    override val baseUrlHint = "يجب أن ينتهي بـ /"
    override val apiKey = "مفتاح API"
    override val apiKeyPlaceholder = "sk-..."
    override val modelId = "معرف النموذج"
    override val testConnection = "اختبار الاتصال"
    override val testingConnection = "جارٍ اختبار الاتصال..."
    override val resetToDefaults = "إعادة تعيين إلى الافتراضي"
    override val reset = "إعادة تعيين"
    override val saveAndCloseSettings = "حفظ وإغلاق الإعدادات"
    override val inputLanguageHintLabel = "تلميح لغة الإدخال"
    override val inputLanguageHintText = "استخدم فقط للكشف التلقائي (لغة الكلام)\nملاحظة: معظم النماذج تتجاهل هذا\n• whisper-1 والأشكال المختلفة: يتم تجاهله\n• distil-whisper-large-v3: يستخدمه"
    override val viewApiLogs = "عرض سجلات API"
    override val logsInfoTitle = "سجلات API والتشخيص"
    override val logsInfoDescription = "عرض سجلات مفصلة لاستدعاءات API وخطوات المعالجة والأخطاء. يتم مسح السجلات عند إعادة تشغيل التطبيق."

    // API Providers
    override val providerDescription = "اختر مزود تحويل الكلام إلى نص"

    // Voice Modes
    override val voiceModes = "أوضاع معالجة الصوت"
    override val selectMode = "اختر الوضع"
    override val addVoiceMode = "إضافة وضع صوتي"
    override val editVoiceMode = "تعديل وضع صوتي"
    override val deleteVoiceMode = "حذف الوضع"
    override val modeName = "اسم الوضع"
    override val systemPrompt = "موجه النظام"
    override val enterPrompt = "أدخل موجه النظام لهذا الوضع"

    // Default Voice Modes
    override val modeVerbatim = "حرفي"
    override val modeVerbatimPrompt = "نسخ الصوت كما هو منطوق بالضبط."
    override val modeFixGrammar = "إصلاح القواعد"
    override val modeFixGrammarPrompt = "نسخ هذا الصوت وإصلاح أي أخطاء نحوية وإملائية وعلامات ترقيم مع الحفاظ على المعنى والنبرة الأصلية."
    override val modePromptFormatter = "منسق المطالبة"
    override val modePromptFormatterPrompt = "إعادة صياغة إدخال المستخدم إلى موجه واضح وفعال مناسب لمعالجة LLM. تحسين الوضوح وإضافة السياق الضروري وهيكلته لفهم أمثل للذكاء الاصطناعي. الحفاظ على نية المستخدم مع جعلها أكثر دقة وقابلية للتنفيذ."
    override val modeLlmResponse = "استجابة LLM"
    override val modeLlmResponsePrompt = "المستخدم يطرح سؤالاً. قدم إجابة مباشرة وموجزة للسؤال دون أي تفسير أو سياق إضافي. أرجع الإجابة فقط."

    // Appearance Settings
    override val appearanceSettings = "المظهر"
    override val colorScheme = "نظام الألوان"
    override val useDynamicColor = "استخدام الألوان الديناميكية"
    override val useDynamicColorDesc = "مطابقة ألوان خلفية النظام"
    override val themeMode = "وضع السمة"
    override val darkMode = "الوضع الداكن"
    override val textSize = "حجم النص"
    override val fontFamily = "عائلة الخط"
    override val uiLanguage = "لغة الواجهة"

    // Dark Mode Options
    override val darkModeFollowSystem = "متابعة النظام"
    override val darkModeAlwaysLight = "فاتح دائماً"
    override val darkModeAlwaysDark = "داكن دائماً"

    // Advanced Settings
    override val advancedSettings = "متقدم"
    override val autoCopyToClipboard = "نسخ تلقائي إلى الحافظة"
    override val autoCopyToClipboardDesc = "نسخ النسخ تلقائياً"
    override val enableHistoryPanel = "تمكين لوحة السجل"
    override val enableHistoryPanelDesc = "اضغط مطولاً على زر اللصق لعرض السجل"

    // About Screen
    override val aboutHyperWhisper = "حول HyperWhisper"
    override val version = "الإصدار"
    override val versionCode = "الرمز"
    override val description = "HyperWhisper هو طريقة إدخال صوت إلى نص (لوحة مفاتيح) تستخدم واجهات برمجة تطبيقات متقدمة للتعرف على الكلام لتوفير نسخ سريع ودقيق. مصمم للمطورين والمستخدمين المتقدمين الذين يريدون تخصيص تجربة الإدخال الصوتي الخاصة بهم."
    override val features = "المميزات:"
    override val featuresList = "• مزود API قابل للتخصيص (OpenAI، Groq، OpenRouter، إلخ.)\n• أوضاع صوتية متعددة (حرفي، إصلاح القواعد، مهذب، إلخ.)\n• دعم لغات الإدخال والإخراج المختلفة\n• واجهة مستخدم حديثة ومستجيبة مبنية بـ Jetpack Compose"
    override val usageStatisticsAndCosts = "إحصائيات الاستخدام والتكاليف:"
    override val noUsageDataYet = "لا توجد بيانات استخدام حتى الآن. ابدأ باستخدام لوحة المفاتيح لرؤية الإحصائيات!"
    override val totalAudio = "إجمالي الصوت"
    override val estimatedTotalCost = "التكلفة الإجمالية المقدرة"
    override val modelBreakdown = "تفصيل النموذج:"
    override val inputTokens = "الإدخال"
    override val outputTokens = "الإخراج"
    override val totalTokens = "الإجمالي"
    override val audioBasedPricing = "التسعير على أساس الصوت (لا يتم تتبع الرموز)"
    override val costsEstimateNote = "* التكاليف تقديرية بناءً على أسعار API الحالية. قد تختلف التكاليف الفعلية."
    override val clearStatistics = "مسح الإحصائيات"

    // Logs Screen
    override val traceLogs = "سجلات التتبع"
    override val diagnosticLogs = "سجلات التشخيص"
    override val diagnosticLogsDesc = "تظهر هذه السجلات استدعاءات API وخطوات المعالجة والأخطاء. يتم مسح السجلات عند إعادة تشغيل التطبيق. يمكنك نسخها أو مسحها باستخدام الأزرار أعلاه."
    override val logsStoredAt = "السجلات مخزنة في"
    override val noLogsYet = "لا توجد سجلات حتى الآن.\nابدأ باستخدام لوحة المفاتيح لرؤية سجلات النشاط."
    override val copyLogs = "نسخ السجلات"
    override val clearLogs = "مسح السجلات"
    override val logsCopiedToClipboard = "تم نسخ السجلات إلى الحافظة"

    // Configuration Info
    override val currentConfiguration = "التكوين الحالي"
    override val provider = "المزود"
    override val transcriptionModel = "نموذج النسخ"
    override val postProcessingModel = "نموذج ما بعد المعالجة"
    override val postProcessingModelDesc = "gpt-4o-mini (للأوضاع غير الحرفية والترجمة)"
    override val none = "لا شيء"
    override val keepOriginal = "الاحتفاظ بالأصلي"
    override val notConfigured = "غير مكون"

    // Transcription History
    override val transcriptionHistory = "سجل النسخ"
    override val historyCount = "{count}/20"
    override val noHistoryYet = "لا يوجد سجل حتى الآن"
    override val clearAll = "مسح الكل"

    // Connection Test
    override val connectionTesting = "جارٍ اختبار الاتصال..."
    override val connectionSuccess = "نجح الاتصال! API يستجيب."
    override val connectionFailed = "فشل الاتصال"
    override val authenticationFailed = "فشلت المصادقة. تحقق من مفتاح API الخاص بك."
    override val endpointNotFound = "لم يتم العثور على نقطة النهاية. تحقق من عنوان URL الأساسي ومعرف النموذج."
    override val connectionTimeout = "انتهت مهلة الاتصال. تحقق من اتصالك بالإنترنت."
    override val sslError = "خطأ SSL/TLS. تحقق من عنوان URL لنقطة النهاية (https)."

    // Errors
    override val error = "خطأ"
    override val copyError = "نسخ الخطأ"
    override val openSettings = "فتح الإعدادات"
    override val dismiss = "إغلاق"
    override val errorConfigureApiKey = "يرجى تكوين مفتاح API في الإعدادات"
    override val errorNoModeSelected = "لم يتم اختيار وضع صوتي"
    override val errorRecordingFailed = "فشل التسجيل"
    override val errorNetworkFailed = "خطأ في الشبكة. يرجى التحقق من اتصالك."
    override val errorApiCall = "خطأ في API"
    override val errorPermissionMicrophone = "لم يتم منح إذن الميكروفون. يرجى تمكين الوصول إلى الميكروفون في إعدادات Android."
    override val errorMicrophoneInUse = "لا يمكن الوصول إلى الميكروفون. قد يكون قيد الاستخدام بواسطة تطبيق آخر."

    // Processing Info Toast
    override val translated = "تمت الترجمة إلى"
    override val twoStepProcessing = "معالجة من خطوتين"

    // Time units
    override val minutes = "د"
    override val seconds = "ث"

    // Input Field Information
    override val inputFieldType = "النوع"
    override val inputFieldApp = "التطبيق"
    override val inputFieldAction = "الإجراء"
    override val fieldTypeText = "نص"
    override val fieldTypeEmail = "بريد إلكتروني"
    override val fieldTypePassword = "كلمة مرور"
    override val fieldTypeNumber = "رقم"
    override val fieldTypePhone = "هاتف"
    override val fieldTypeUrl = "URL"
    override val fieldTypeMultiline = "متعدد الأسطر"
    override val fieldTypeUnknown = "غير معروف"
    override val actionNone = "لا شيء"
    override val actionDone = "تم"
    override val actionGo = "انتقال"
    override val actionSearch = "بحث"
    override val actionSend = "إرسال"
    override val actionNext = "التالي"
    override val actionPrevious = "السابق"
}
