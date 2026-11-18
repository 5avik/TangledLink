# Terminal (Linux): реализация и проверка

Дата: 16 ноября 2026. Ветка: test. Первичная проверка выполнена на 0.9.6-test; Terminal подготовлен к выпуску 0.9.7-test (versionCode 16).

## Как открыть

Ещё → Темы оформления → Terminal (Linux). Ниже пяти миров находятся четыре палитры.
Выбор — локальный черновик; Применить сохраняет мир и палитру, Отмена/Back оставляют сохранённые значения.
На главной Terminal доступны Консоль, Системная информация и События подключения.
Поддерживаемые команды показывает help. Консоль не является системной оболочкой.

## Интеграция

- Расширены существующие AppearanceSelection, ThemeManager и ThemeConfig/ThemeCatalog.
  Мир terminal добавлен после Original, Sakura, Aurora и Cyberpunk. Глобальный default — original.
- MMKV: pref_tngl_theme_id и отдельный pref_tngl_terminal_palette.
  Палитры: matrix_green (default), catppuccin_mocha, tokyo_night, gruvbox_dark.
  Неизвестные значения разрешаются в Original / Matrix Green.
  Переход в другой мир сохраняет последнюю terminal-палитру. Ordinal и runtime JSON не используются.
- ThemeSelectionController/SavedStateHandle владеет обоими ID черновика.
  При ошибке сохранения экран остаётся открытым, подтверждённое глобальное состояние не меняется.
  ThemeManager проверяет результаты MMKV encode и выполняет best-effort rollback частичных записей;
  MMKV не предоставляет здесь транзакцию нескольких отдельных ключей.
- Режим system/light/dark и dynamic color сохранены. При поддерживаемом dynamic color
  Material использует системную схему. Терминальные окна используют согласованные непрозрачные
  background/surface/text выбранной палитры; companion light schemes перенесены из пакета.
- ui/terminal/TerminalThemes.kt содержит типизированные Material/scene-палитры и TerminalFont.
  Используются Regular/Bold JetBrains Mono Nerd Font, лицензия находится в assets/licenses/JetBrainsMono-OFL.txt.
- TerminalScreens.kt: TerminalWindow, TerminalPrompt, AsciiPenguin, TerminalConnectionPanel,
  TerminalServerCard, реальные показатели трафика, Console/Fetch/Events.
  Главная прокручивается, поддерживает две колонки на широком экране. Существующие серверы,
  поиск, группы, подписки, аккаунт и настройки остаются в прежней навигации.
- PremiumScreens.kt добавляет внутренний маршрут terminal; новых Activity или нижних вкладок нет.
  MainContract/MainViewModel принимают типизированные действия, состояние публикуется через StateFlow.
- ThemedScene.kt использует лёгкие готовые VectorDrawable для Terminal, Coil — для декоративных превью.
  Launcher-иконка и общий бренд не изменялись. Терминальные декорации статичны:
  новых бесконечных анимаций, Matrix Rain или фоновых сервисов нет.
- Добавлены en/default и ru строки; описание раздела тем больше не ограничивается четырьмя мирами.

## Команды, безопасность и VPN

TerminalCommandParser.kt и TerminalPaletteTokens.kt адаптированы из core/ пакета.
Parser не получает Android Context, repository или VPN-сервис.
Разрешён только whitelist; Runtime.exec, ProcessBuilder, shell, root и WebView не используются.

TerminalCommandHandler принадлежит MainViewModel:
- connect/disconnect идемпотентны и используют общий с GUI ConnectionRequestGate.
  Перед ToggleService повторно проверяется актуальный isRunning.
  Gate удерживается при ожидании VPN-permission/native результата, освобождается по результату,
  отмене или ошибке. UI timeout не означает Connected и не снимает pending.
- Выбор сервера проверяет GUID по актуальным строкам и вызывает прежнее действие выбора.
  ping использует прежнюю проверку текущего сервера; при отсутствии данных выдаётся локализованная ошибка.
- theme set сохраняет только terminalPalette в копии текущего оформления, без переключения мира
  и без VPN-действия; false/exception storage оставляют подтверждённую палитру.
- История: до 50 корректных команд в памяти; Up/Down, Tab и видимые подсказки.
  Вывод: до 400 строк, каждая до 256 символов. Неизвестный ввод не отражается в ошибках,
  истории, логировании, analytics или диагностике. clear очищает только вывод.
- Fetch использует whitelist: Android, Build.MODEL, версия приложения, состояние сервиса,
  доменный GUID выбора, тип протокола, реальная задержка/uptime при наличии.
  Не используются конфиги, credential UUID, адреса, пароли, токены, ключи или raw logcat.
- Events при входе создаёт текущий снимок, затем хранит до 100 доступных переходов состояния
  открытого экрана в памяти. Нет вымышленных handshake, DNS или старых timestamps.
- Статистика берётся из существующего TrafficUiState; при отсутствии измерений график пуст.
  Состояние «VPN-сервис запущен» не заявляет подтверждённую доступность интернета.

## Проверено

Пакет: SHA256SUMS.txt — все 46 записей прошли проверку.
Ресурсы: четыре vector-фона, пять WebP-превью, семейство и два файла шрифта;
существующий tngl_brand_mark не перезаписывался.

Сборка:
```
./gradlew :app:assemblePlaystoreDebug :app:testPlaystoreDebugUnitTest :app:installPlaystoreDebug
```
BUILD SUCCESSFUL. Тестовый APK также собран и установлен отдельной задачей
installPlaystoreDebugAndroidTest, без connectedAndroidTest и удаления данных клиента.

JVM:
- TerminalCoreTest: более 80 содержательных проверок parser/planner/fallback.
- TerminalHandlerTest: идемпотентность и gate, cancel/failure, unknown/удалённый GUID,
  выбор существующего GUID, запрет отражения секретов, безопасная диагностика,
  ограничение буферов, completion, отсутствие данных, palette storage false/exception,
  сохранение мира, preview/Cancel/Apply, восстановление двух ID и повтор сохранения после ошибки.
- Полный существующий testPlaystoreDebugUnitTest также прошёл.

Устройство: Medium Phone, emulator-5554, Android API 37.
Проверки выполнялись через am instrument с AndroidJUnitRunner:
- TerminalUiTest — 4 палитры × light/dark × dynamic on/off, размер 320×568 и 640×360,
  масштаб шрифта 160%, доступность статуса и навигации; Enter/Up/Down/Tab,
  IME action, кнопка выполнения с клавиатурой, неизвестный ввод без отражения.
- ThemeFeatureTest — пять миров, Material dynamic priority, одно-/двухколоночные размеры,
  touch/keyboard/D-pad, radio-семантика, ошибка Apply и retry,
  Terminal/Gruvbox draft после ActivityScenario.recreate и отмена через системный Back.
- ThemePickerLayoutTest — все пять миров на малом/широком экране с увеличенным текстом.
- PremiumNavigationTest и SceneMotionTest — прошли.
Первый расширенный прогон обнаружил две ошибки прокрутки в тестах LazyColumn
(пятая строка ещё не была скомпонована). Тесты исправлены через performScrollToNode;
финальный целевой прогон пяти тестов прошёл: OK (5 tests). Остальные затронутые
navigation/motion/palette проверки успешно прошли в предшествующем прогоне.

Ручная проверка в существующем клиенте с сохранённым реальным сервером:
- GUI connect запускает сервис; повторный console connect сообщает «VPN уже запущен».
- theme set tokyo_night меняет оформление, PID VPN-процесса сохраняется.
- disconnect использует прежнюю остановку; повторный disconnect сообщает «VPN уже остановлен».
- Fetch показывает Android и реальные доступные поля; Events начинает с текущего снимка остановленного сервиса.
- После force-stop и холодного запуска восстановились Terminal/Tokyo Night и выбранный сервер.
- По окончании восстановлены Original и начальная Matrix Green; аккаунт и сервер не удалялись.

Состав debug APK проверен unzip: рабочие terminal-ресурсы и OFL-лицензия присутствуют,
terminal_tokens.json, README_RU, reference-макеты и TerminalCoreCheck в APK не добавлены.
git diff --check прошёл. Во время реализации темы build.gradle.kts, core/, service/, handler/, jniLibs/ и server/ не менялись.
Для выпуска 0.9.7-test в build.gradle.kts повышены только versionName и versionCode.
Исходное изменение .idea/deploymentTargetSelector.xml сохранено без редактирования.

## Проверка выпуска 0.9.7-test

PlaystoreRelease и PlaystoreDebug собраны успешно; JVM-тесты и compilePlaystoreDebugAndroidTestKotlin прошли.
Пять release APK проверены apksigner verify. SHA-256 сертификата каждого:
244ce807997dbde64417b12c1f5086ea0057cdc5894bd4b9acc2242f525c86c6.
aapt подтвердил pro.tngl, versionName 0.9.7-test и итоговый versionCode 4000016.
Контрольные суммы APK подготовлены для приложения к пререлизу.

## Not run

- Полная проверка произношения и порядка озвучивания TalkBack: инструменты устройства не дают
  прослушивать аудио. Compose semantics, focus, keyboard и D-pad проверены; это не заменяет TalkBack.
- Отмена системного VPN-permission и реальная native ошибка на устройстве специально не вызывались:
  использовалось уже выданное разрешение и существующая конфигурация.
  Соответствующие ветви gate проверены JVM-тестами.
- Доступность интернета, скорость и доставка трафика через выбранный VPN-сервер не измерялись.
  Проверен запуск/остановка существующего сервиса, а не полноценная сетевая приёмка.
- Отказ реального MMKV/заполненный накопитель на устройстве не имитировался; false/exception покрыты fake-store тестами.
- Физический поворот устройства и системное автоматическое переключение day/night отдельно не выполнялись:
  использованы ActivityScenario.recreate, ForcedSize portrait/landscape и обе Material-схемы на устройстве.
- Другие телефоны/API и замеры FPS/энергопотребления не запускались. Подписанный release APK отдельно на устройство не устанавливался.
