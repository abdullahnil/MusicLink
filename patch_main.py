import os

path = r"c:\Users\abdul\Documents\Antigravity Project\MusicLink\app\src\main\java\com\example\musiclink\MainActivity.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

replacements = [
    ('import androidx.compose.ui.text.style.TextAlign', 'import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.res.stringResource'),
    ('fetchLinksFromOdesli(incomingUrl)', 'fetchLinksFromOdesli(context, incomingUrl)'),
    ('suspend fun fetchLinksFromOdesli(incomingUrl: String): List<PlatformLink>', 'suspend fun fetchLinksFromOdesli(context: Context, incomingUrl: String): List<PlatformLink>'),
    ('throw Exception("API call failed with code ${response.code}. Servis şarkıyı bulamamış olabilir.")', 'throw Exception(context.getString(R.string.error_api_failed, response.code))'),
    ('throw Exception("Empty response body")', 'throw Exception(context.getString(R.string.empty_response_body))'),
    ('"YouTube Music (Arama)"', 'context.getString(R.string.youtube_music_search)'),
    ('"Bağlantıları alırken bir hata oluştu"', 'context.getString(R.string.error_fetching_links)'),
    ('"Bağlantılar aranıyor..."', 'stringResource(R.string.searching_links)'),
    ('"Hata: $errorMessage"', 'stringResource(R.string.error_prefix, errorMessage ?: "")'),
    ('"Platform Seçin"', 'stringResource(R.string.choose_platform)'),
    ('"Açmak istediğiniz uygulamayı seçin. \'Varsayılan Yap\' diyerek otomatik açılmasını sağlayabilirsiniz."', 'stringResource(R.string.choose_platform_desc)'),
    ('"Alternatif platform bağlantısı bulunamadı."', 'stringResource(R.string.no_links_found)'),
    ('"MusicLink\'e Hoş Geldiniz!"', 'stringResource(R.string.onboarding_title_1)'),
    ('"Arkadaşlarınızdan gelen müzik linklerini (Spotify, YouTube vb.) kendi kullandığınız müzik platformunda dinleme özgürlüğüne kavuştunuz."', 'stringResource(R.string.onboarding_desc_1)'),
    ('"Nasıl Çalışır?"', 'stringResource(R.string.onboarding_title_2)'),
    ('"Bir linke tıkladığınızda veya paylaştığınızda; MusicLink araya girerek o şarkının diğer tüm platformlardaki (YouTube Music, Apple, Deezer vb.) karşılıklarını saniyeler içinde bulur."', 'stringResource(R.string.onboarding_desc_2)'),
    ('"Varsayılanı Belirleyin"', 'stringResource(R.string.onboarding_title_3)'),
    ('"Müziği açmadan önce listeden en sevdiğiniz uygulamayı \'Varsayılan\' yaparsanız, bir sonraki tıklamada doğrudan o çok sevdiğiniz uygulamanıza yönlendirilirsiniz."', 'stringResource(R.string.onboarding_desc_3)'),
    ('Text("Geri")', 'Text(stringResource(R.string.back))'),
    ('Text("İleri")', 'Text(stringResource(R.string.next))'),
    ('Text("Başlayalım")', 'Text(stringResource(R.string.start))'),
    ('"MusicLink Ayarları"', 'stringResource(R.string.settings_title)'),
    ('"Şu anki varsayılan uygulamanız:"', 'stringResource(R.string.current_default)'),
    ('Text("Varsayılanı Temizle")', 'Text(stringResource(R.string.clear_default))'),
    ('"Varsayılan Uygulama Seçilmedi"', 'stringResource(R.string.no_default_title)'),
    ('"Liste ekranındayken bir platformu varsayılan yaparak buraya gelmesini sağlayabilirsiniz."', 'stringResource(R.string.no_default_desc)'),
    ('Text("Android Bağlantı Ayarlarını Aç")', 'Text(stringResource(R.string.open_android_settings))'),
    ('"Müzik linklerinin otomatik olarak MusicLink ile açılması için \'Desteklenen bağlantıları aç\' iznini verebilirsiniz."', 'stringResource(R.string.open_android_settings_desc)'),
    ('"Açmak için dokunun"', 'stringResource(R.string.tap_to_open)'),
    ('Text("Varsayılan")', 'Text(stringResource(R.string.default_button))'),
]

for old, new in replacements:
    content = content.replace(old, new)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
